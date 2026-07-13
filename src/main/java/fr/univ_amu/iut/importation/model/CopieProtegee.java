package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/// Copie protégée d'un fichier depuis la carte SD vers l'espace de travail (R9).
///
/// **Contrainte autoritaire R9** : l'application copie systématiquement les fichiers de la SD vers
/// son workspace et **n'écrit jamais sur les originaux** de la carte. Cette classe matérialise la
/// règle : elle **ouvre la source uniquement en lecture** ([Files#copy] lit l'octet source, n'y
/// écrit pas) et écrit exclusivement dans la destination.
///
/// Garantie vérifiée : après copie, l'empreinte SHA-256 de la destination est recalculée et
/// comparée à celle de la source. Une divergence (copie tronquée, disque plein) lève une
/// [IllegalStateException]. Le test de la règle R9 vérifie en plus que le hash **de la source**
/// est identique avant et après l'opération (la source n'a pas bougé).
public class CopieProtegee {

    /// Copie `source` vers le fichier `destination` (les dossiers parents sont créés). La source
    /// n'est jamais modifiée. Écrase une destination existante (réimport idempotent).
    ///
    /// @return le chemin de la destination écrite
    /// @throws IllegalStateException si la copie n'est pas fidèle (empreintes différentes)
    public Path copier(Path source, Path destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        try {
            String empreinteSource = Empreintes.sha256Hex(source);
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            String empreinteCopie = Empreintes.sha256Hex(destination);
            if (!empreinteSource.equals(empreinteCopie)) {
                throw new IllegalStateException(
                        "Copie non fidèle de " + source + " : empreinte SHA-256 divergente après copie.");
            }
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException(messageEchec(source, e.getMessage()), e);
        }
    }

    /// Construit le message d'échec présenté à l'utilisateur. Le cas **disque plein** (ENOSPC) est le
    /// plus courant en pratique (un workspace de plusieurs Go) : on le reconnaît pour donner une consigne
    /// actionnable (« libérez de la place ») plutôt que l'opaque « Échec de la copie protégée ». Pour toute
    /// autre `IOException`, la cause technique est **jointe** au message (au lieu d'être masquée), afin que
    /// l'utilisateur — et le support — voient la vraie raison.
    ///
    /// Statique et publique pour être testable directement, sans provoquer une vraie panne d'espace disque.
    public static String messageEchec(Path source, String messageCause) {
        String nom = source.getFileName() == null
                ? source.toString()
                : source.getFileName().toString();
        if (messageCause != null && messageCause.contains("No space left on device")) {
            return "Espace disque insuffisant pour copier « " + nom
                    + " » : libérez de la place dans le dossier de travail (ou choisissez une source plus petite,"
                    + " par exemple le jeu d'exemple) puis relancez l'import.";
        }
        return "Échec de la copie protégée de " + source + (messageCause == null ? "" : " (" + messageCause + ")");
    }

    /// Copie `source` dans le dossier `dossierDestination`, en conservant le nom de fichier
    /// d'origine (le dossier est créé au besoin).
    ///
    /// @return le chemin du fichier copié
    public Path copierVers(Path source, Path dossierDestination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dossierDestination, "dossierDestination");
        return copier(source, dossierDestination.resolve(source.getFileName().toString()));
    }
}
