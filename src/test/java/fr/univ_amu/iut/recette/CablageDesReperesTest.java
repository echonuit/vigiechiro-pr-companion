package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde le câblage qui rend l'extension active en séance filmée, et inerte partout ailleurs
/// (#3774).
///
/// ## Pourquoi un garde pour trois lignes de configuration
///
/// [ReperesDeSeanceTest] prouve que le moteur appelle l'extension **quand on la lui donne**. Il ne
/// dit rien de la façon dont la vraie séance la lui donne, et c'est là que ça casse en silence :
/// renommer la classe, ou perdre une propriété du profil, ne fait rougir aucun test. La séance
/// tournerait, le film sortirait, et le journal serait **vide** - indiscernable d'une séance où
/// aucun test ne cite de cas.
///
/// ⚠️ Ce garde lit des fichiers de configuration, pas du comportement. Il ne remplace donc pas une
/// séance filmée réelle ; il attrape la faute d'inattention entre deux séances, qui sont rares.
class CablageDesReperesTest {

    private static final Path SERVICES =
            Path.of("src", "test", "resources", "META-INF", "services", "org.junit.jupiter.api.extension.Extension");

    private static final Path POM = Path.of("pom.xml");

    @Test
    @DisplayName("Le fichier de services nomme l'extension, sous son nom d'aujourd'hui")
    void le_fichier_de_services_nomme_l_extension() {
        // Le nom vient de la classe elle-même : la renommer sans toucher au fichier rougit ici,
        // plutôt que de rendre une séance muette.
        assertThat(lire(SERVICES))
                .as("sans cette ligne, la détection automatique ne trouve rien à détecter")
                .contains(ReperesDeSeance.class.getName());
    }

    @Test
    @DisplayName("Le profil filmé pose les deux propriétés, qui vont ensemble")
    void le_profil_filme_pose_les_deux_proprietes() {
        String profil = profilRecetteFilmee();

        assertThat(sansEspaces(profil))
                .as("la détection charge l'extension ; sans elle, la propriété d'écriture ne sert à rien")
                .contains("<recette.autodetection>true</recette.autodetection>");
        assertThat(sansEspaces(profil))
                .as("la propriété dit où écrire ; sans elle, l'extension chargée ne fait rien")
                .contains("<recette.reperes>${project.build.directory}/recette-filmee/reperes.tsv</recette.reperes>");
    }

    @Test
    @DisplayName("Hors du profil, les deux propriétés sont inertes")
    void hors_du_profil_les_deux_proprietes_sont_inertes() {
        // C'est ce qui garantit qu'un `mvn test` ordinaire ne charge rien de neuf et n'écrit rien.
        String racine = sansEspaces(lire(POM).split("<profiles>")[0]);

        assertThat(racine).contains("<recette.autodetection>false</recette.autodetection>");
        assertThat(racine).contains("<recette.reperes/>");
    }

    @Test
    @DisplayName("Surefire transmet les deux propriétés à la JVM forkée")
    void surefire_transmet_les_deux_proprietes() {
        // Les tests tournent dans une JVM forkée : une propriété posée sur celle de Maven n'y
        // arriverait pas, et l'extension lirait une propriété vide sans rien dire.
        String pom = sansEspaces(lire(POM));

        assertThat(pom)
                .contains("<junit.jupiter.extensions.autodetection.enabled>${recette.autodetection}"
                        + "</junit.jupiter.extensions.autodetection.enabled>");
        assertThat(pom).contains("<recette.reperes>${recette.reperes}</recette.reperes>");
    }

    // ----------------------------------------------------------------------------------------

    private static String profilRecetteFilmee() {
        String pom = lire(POM);
        int debut = pom.indexOf("<id>recette-filmee</id>");
        assertThat(debut).as("le profil recette-filmee a disparu du pom.xml").isNotNegative();
        return pom.substring(debut, pom.indexOf("</profile>", debut));
    }

    private static String sansEspaces(String texte) {
        return texte.replaceAll("\\s+", "");
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException("Fichier de câblage introuvable : " + fichier, e);
        }
    }
}
