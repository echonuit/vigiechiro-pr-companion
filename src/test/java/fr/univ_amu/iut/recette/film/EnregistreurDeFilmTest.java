package fr.univ_amu.iut.recette.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineTestKit;

/// Ce que le banc filme, et ce qu'il laisse passer (#4162).
///
/// ## Pourquoi cette question se pose
///
/// Une seule ligne au fichier de services de JUnit rend l'extension active partout où la détection
/// automatique est demandée : il n'y a pas trente classes à annoter pour couvrir tous les cas. Le
/// revers est qu'ainsi branchée, elle filme **tous** les tests, y compris ceux qui ne citent aucun
/// cas. Une sonde l'a mesuré sur une seule classe de service : **vingt clips de carton**, deux
/// secondes chacun, que personne n'ouvrira - et l'index, qui se lit par cas, ne sait même pas les
/// nommer.
///
/// ## Pourquoi le cas NÉGATIF est le seul joué par le moteur
///
/// ⚠️ Le banc a besoin de `ffmpeg`, que le build ordinaire n'installe pas. Un cas qui filmerait pour
/// de vrai serait donc rouge sur toute PR, ou vert par une hypothèse sautée - c'est-à-dire un vert
/// qui ne prouve rien.
///
/// Le refus, lui, s'éprouve **sans** `ffmpeg`, et c'est une propriété du remède et non une
/// commodité : on refuse **avant** d'ouvrir l'encodeur.
///
/// ⚠️ Le garde vise un dossier qui **n'existe pas encore**, et non un dossier vide. Sans cela il
/// serait vert à vide sur toute machine sans `ffmpeg` : l'encodeur y échoue, donc aucun fichier
/// n'apparaît, donc « aucun fichier » ne prouve rien. Or l'enregistrement crée son dossier
/// **avant** de lancer l'encodeur : c'est cette création-là qui trahit une décision prise trop
/// tard, avec ou sans `ffmpeg` sur la machine.
///
/// Ce qui prouve l'autre moitié - qu'un test qui cite un cas produit bien son clip - est le tournage
/// lui-même, qui rend neuf clips sur neuf sur trois plateformes.
class EnregistreurDeFilmTest {

    @Test
    @DisplayName("un test qui cite un cas se filme")
    void un_test_qui_cite_un_cas_se_filme() {
        assertThat(EnregistreurDeFilm.aFilmer(List.of("S1-26"), false)).isTrue();
    }

    @Test
    @DisplayName("un test qui ne cite aucun cas ne se filme pas")
    void un_test_sans_cas_ne_se_filme_pas() {
        assertThat(EnregistreurDeFilm.aFilmer(List.of(), false)).isFalse();
    }

    /// L'échappatoire existe pour le débogage d'un test qui ne porte pas encore de cas. Elle se
    /// demande, elle ne s'obtient pas par défaut : un tournage complet est ce que le banc sert à produire.
    @Test
    @DisplayName("la propriété « tout » filme aussi ce qui ne cite rien")
    void la_propriete_tout_filme_aussi_ce_qui_ne_cite_rien() {
        assertThat(EnregistreurDeFilm.aFilmer(List.of(), true)).isTrue();
    }

    @Test
    @DisplayName("« tout » ne retire rien à ce qui cite un cas")
    void tout_ne_retire_rien_a_ce_qui_cite_un_cas() {
        assertThat(EnregistreurDeFilm.aFilmer(List.of("S1-26"), true)).isTrue();
    }

    /// Le garde du CÂBLAGE, joué par le vrai moteur JUnit.
    ///
    /// ⚠️ Il porte sur l'ABSENCE DE FICHIER, et non sur une ligne de journal. Un clip qui s'arrête à
    /// son carton et un clip qui n'existe pas se ressemblent trait pour trait dans une sortie
    /// console : seul le dossier tranche.
    @Test
    @DisplayName("le moteur ne laisse aucune trace derrière un test qui ne cite rien")
    void aucune_trace_pour_un_test_qui_ne_cite_rien(@TempDir Path bac) throws IOException {
        Path clips = bac.resolve("clips");

        jouer(SeanceSansCas.class, clips);

        assertThat(clips)
                .as("le dossier de tournage ne doit même pas être créé : la décision se prend avant")
                .doesNotExist();
        assertThat(contenu(clips))
                .as("et rien ne doit s'y trouver, pas même un carton")
                .isEmpty();
    }

    /// Sans la propriété, l'extension ne fait rien du tout, quelle que soit l'annotation. C'est ce
    /// qui permet de la laisser branchée sur une classe hors séance filmée.
    @Test
    @DisplayName("sans la propriété de tournage, rien n'est écrit non plus")
    void sans_la_propriete_de_tournage_rien_n_est_ecrit(@TempDir Path bac) {
        Path clips = bac.resolve("clips");
        System.clearProperty("recette.film");
        System.setProperty("recette.film.dossier", clips.toString());
        try {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(SeanceSansCas.class))
                    .execute();
        } finally {
            System.clearProperty("recette.film.dossier");
        }

        assertThat(clips).doesNotExist();
    }

    // ----------------------------------------------------------------------------------------

    private static void jouer(Class<?> seance, Path dossier) {
        System.setProperty("recette.film", "");
        System.setProperty("recette.film.dossier", dossier.toString());
        try {
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(seance)).execute();
        } finally {
            System.clearProperty("recette.film");
            System.clearProperty("recette.film.dossier");
        }
    }

    private static List<Path> contenu(Path dossier) throws IOException {
        if (!Files.exists(dossier)) {
            return List.of();
        }
        try (Stream<Path> present = Files.walk(dossier)) {
            return present.filter(Files::isRegularFile).toList();
        }
    }

    /// Une séance d'exemple dont le seul test ne cite aucun cas.
    ///
    /// ⚠️ Aucune classe d'exemple ne cite de cas ici, et c'est délibéré : filmer pour de vrai
    /// demanderait `ffmpeg`. Voir l'en-tête de ce fichier.
    @ExtendWith(EnregistreurDeFilm.class)
    static class SeanceSansCas {
        @Test
        void ne_cite_aucun_cas() {
            // Le corps n'a pas d'importance : ce qui est éprouvé est ce que l'extension fait autour.
        }
    }
}
