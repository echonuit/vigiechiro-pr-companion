package fr.univ_amu.iut.commun.model;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// Écrivain d'archive ZIP **généraliste** du socle (#2792) : une archive, à un chemin choisi,
/// composée d'entrées **texte** (générées en mémoire, un CSV par exemple - aucun fichier
/// intermédiaire) et d'entrées **fichier** (recopiées en flux, mémoire bornée, patron
/// `CompacteurDepot`). Ce que le compacteur du dépôt ne sait pas faire : il est marié à sa
/// sémantique (nommage imposé, plafond, découpage) - lui reste le pipeline VigieChiro.
///
/// **Annulation et échec ne laissent rien** : le jeton est consulté avant chaque entrée et une
/// dernière fois avant de conclure ; sur [OperationAnnuleeException] comme sur toute erreur,
/// l'archive **partielle est supprimée** (patron `ExtracteurZip` : un fichier absent vaut mieux
/// qu'un fichier menteur). La progression est **déterminée** (« Archive : X / N · nom »), notifiée
/// après chaque entrée fichier, possiblement hors du fil JavaFX (l'appelant marshale).
public final class EcrivainZip {

    /// Une entrée générée en mémoire (UTF-8), typiquement le CSV qui accompagne les fichiers.
    public record EntreeTexte(String nomEntree, String contenu) {

        public EntreeTexte {
            exigerNom(nomEntree);
            Objects.requireNonNull(contenu, "contenu");
        }
    }

    /// Un fichier existant à recopier dans l'archive sous `nomEntree`.
    public record EntreeFichier(String nomEntree, Path source) {

        public EntreeFichier {
            exigerNom(nomEntree);
            Objects.requireNonNull(source, "source");
        }
    }

    private EcrivainZip() {}

    /// Écrit l'archive `destination` (remplacée si elle existe : l'appelant a déjà confirmé
    /// l'écrasement au moment de désigner le fichier). Renvoie la **taille de l'archive** en octets.
    ///
    /// @param surProgression notifié après chaque entrée fichier (avancement déterminé)
    /// @param jeton annulation coopérative, consultée avant chaque entrée
    /// @throws OperationAnnuleeException si `jeton` est annulé - l'archive partielle est supprimée
    /// @throws IOException sur échec d'écriture ou source illisible - l'archive partielle est supprimée
    public static long ecrire(
            Path destination,
            List<EntreeTexte> textes,
            List<EntreeFichier> fichiers,
            Consumer<Progression> surProgression,
            JetonAnnulation jeton)
            throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(surProgression, "surProgression");
        Objects.requireNonNull(jeton, "jeton");
        try (OutputStream sortie = new BufferedOutputStream(Files.newOutputStream(destination));
                ZipOutputStream zip = new ZipOutputStream(sortie)) {
            for (EntreeTexte texte : textes) {
                jeton.leverSiAnnule();
                zip.putNextEntry(new ZipEntry(texte.nomEntree()));
                zip.write(texte.contenu().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            int faits = 0;
            for (EntreeFichier fichier : fichiers) {
                jeton.leverSiAnnule();
                zip.putNextEntry(new ZipEntry(fichier.nomEntree()));
                Files.copy(fichier.source(), zip); // recopie en flux : mémoire bornée (#104)
                zip.closeEntry();
                faits++;
                surProgression.accept(new Progression(
                        "Archive : " + faits + " / " + fichiers.size() + " · " + nomCourt(fichier.nomEntree()),
                        (double) faits / fichiers.size()));
            }
            // Re-vérification finale : une annulation pendant la dernière entrée ne doit pas laisser
            // l'archive « aboutir » (le catch ci-dessous nettoie).
            jeton.leverSiAnnule();
        } catch (IOException | RuntimeException echec) {
            Files.deleteIfExists(destination);
            throw echec;
        }
        return Files.size(destination);
    }

    /// Dernier segment d'un nom d'entrée, pour afficher le **fichier courant** sans son chemin interne.
    private static String nomCourt(String nomEntree) {
        int barre = nomEntree.lastIndexOf('/');
        return barre < 0 ? nomEntree : nomEntree.substring(barre + 1);
    }

    private static void exigerNom(String nomEntree) {
        Objects.requireNonNull(nomEntree, "nomEntree");
        if (nomEntree.isBlank()) {
            throw new IllegalArgumentException("Le nom d'une entrée d'archive ne peut pas être vide.");
        }
    }
}
