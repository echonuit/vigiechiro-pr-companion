package fr.univ_amu.iut.commun.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Lecture et écriture du [ManifesteSauvegarde], via **Gson**, sur le patron de
/// `DescripteurVueJson` (#2726).
///
/// Écrit en lisible (`setPrettyPrinting`) : ce fichier est le seul endroit où un utilisateur peut
/// vérifier de ses yeux d'où venaient les dossiers d'une sauvegarde et à quoi ils correspondent dans
/// `sessions/`. Un JSON sur une seule ligne ne se lit pas.
///
/// **Absent et illisible ne sont pas le même cas**, et c'est le point de cette classe :
///
/// - **pas de fichier** : une sauvegarde antérieure à ce format. Cas normal, [Optional] vide, la
///   restauration retombe sur ce qu'elle savait faire hier ;
/// - **fichier présent mais inexploitable** : la sauvegarde est abîmée. Refus explicite, parce que
///   le traiter comme « absent » ferait silencieusement moins bien que promis, sur la seule
///   sauvegarde dont on a la preuve qu'elle a un problème.
public final class ManifesteSauvegardeJson {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ManifesteSauvegardeJson() {}

    /// Écrit le manifeste à la racine du dossier de sauvegarde.
    public static void ecrire(Path dossierSauvegarde, ManifesteSauvegarde manifeste) throws IOException {
        Objects.requireNonNull(manifeste, "manifeste");
        Files.writeString(
                dossierSauvegarde.resolve(ManifesteSauvegarde.NOM_FICHIER),
                GSON.toJson(manifeste),
                StandardCharsets.UTF_8);
    }

    /// Lit le manifeste d'un dossier de sauvegarde. [Optional] vide s'il n'y en a pas.
    ///
    /// @throws DataAccessException si le fichier existe mais n'est pas un manifeste exploitable
    public static Optional<ManifesteSauvegarde> lire(Path dossierSauvegarde) {
        Path fichier = dossierSauvegarde.resolve(ManifesteSauvegarde.NOM_FICHIER);
        if (!Files.isRegularFile(fichier)) {
            return Optional.empty();
        }
        return Optional.of(interpreter(fichier));
    }

    private static ManifesteSauvegarde interpreter(Path fichier) {
        ManifesteSauvegarde manifeste = analyser(fichier);
        if (manifeste == null || manifeste.version() <= 0) {
            throw new DataAccessException(refus(fichier) + " Il ne porte pas de version.", null);
        }
        return manifeste;
    }

    /// Gson traduit tout ce qui l'arrête en exception **non vérifiée**, y compris le refus d'un des
    /// enregistrements de ce paquet quand un champ obligatoire manque : d'où le rattrapage large,
    /// qui sert à **situer** l'échec, jamais à le taire.
    private static ManifesteSauvegarde analyser(Path fichier) {
        try {
            return GSON.fromJson(Files.readString(fichier, StandardCharsets.UTF_8), ManifesteSauvegarde.class);
        } catch (IOException | RuntimeException illisible) {
            throw new DataAccessException(refus(fichier), illisible);
        }
    }

    private static String refus(Path fichier) {
        return "Le manifeste de cette sauvegarde est illisible (" + fichier
                + "). La sauvegarde est peut-être abîmée : restaurez-en une autre, ou supprimez ce"
                + " fichier pour la restaurer comme avant, sans remettre les dossiers à leur place.";
    }
}
