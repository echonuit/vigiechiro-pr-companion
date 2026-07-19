package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import fr.univ_amu.iut.importation.model.AnalyseCoherence;
import fr.univ_amu.iut.importation.model.AnalyseMelange;
import fr.univ_amu.iut.importation.model.JournalParse;
import fr.univ_amu.iut.importation.model.PassageExistant;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que l'inspection relève avant l'import, rendu en **compte rendu** : mélange d'enregistreurs (#33),
/// désaccord journal/fichiers (#33), nuit déjà importée (#147).
///
/// Reprend et fusionne `AvertissementMelangeTest` et `AvertissementIncoherenceTest`, qui éprouvaient la
/// mise en phrase de deux libellés séparés. Les détections restent couvertes par `AnalyseMelangeTest` et
/// `AnalyseCoherenceTest` ; ici on vérifie ce qui est **restitué**, et surtout que les listes ne sont
/// plus aplaties dans une phrase (#2050).
class AvertissementsInspectionTest {

    private static final LocalDate NUIT = LocalDate.of(2026, 4, 22);

    private static AnalyseMelange melange(String... fichiers) {
        return AnalyseMelange.depuis(List.of(fichiers).stream().map(Path::of).toList());
    }

    private static AnalyseCoherence coherence(String serie, String fichier) {
        return AnalyseCoherence.depuis(journal(serie), null, List.of(Path.of(fichier)));
    }

    private static JournalParse journal(String serie) {
        return new JournalParse(serie, null, NUIT, null, null, null, null, null, true, null, List.of(), List.of());
    }

    private static Constat constatUnique(CompteRendu rendu) {
        assertThat(rendu.constats()).hasSize(1);
        return rendu.constats().getFirst();
    }

    @Test
    @DisplayName("Dossier sans question : compte rendu vide, rien ne s'affiche")
    void dossier_sain() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                melange("PaRecPR1925492_20260422_203000.wav", "PaRecPR1925492_20260422_233000.wav"),
                coherence("1925492", "PaRecPR1925492_20260422_203000.wav"),
                List.of());

        assertThat(rendu.estVide()).isTrue();
    }

    @Test
    @DisplayName("Plusieurs enregistreurs : chaque série est un détail, pas une virgule dans la phrase")
    void melange_enregistreurs() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                melange("PaRecPR1925492_20260422_203000.wav", "PaRecPR1648011_20260422_203000.wav"), null, List.of());

        Constat constat = constatUnique(rendu);
        assertThat(constat.severite())
                .as("le dossier est douteux, mais rien n'a échoué et l'import reste possible")
                .isEqualTo(Severite.AVERTISSEMENT);
        assertThat(constat.details()).extracting(Detail::sujet).containsExactly("série 1648011", "série 1925492");
    }

    @Test
    @DisplayName("Plusieurs nuits d'un seul enregistreur : cas géré, rien à signaler")
    void multi_nuits_d_un_seul_enregistreur() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                melange(
                        "PaRecPR1925492_20260422_203000.wav",
                        "PaRecPR1925492_20260423_203000.wav",
                        "PaRecPR1925492_20260424_203000.wav"),
                null,
                List.of());

        assertThat(rendu.estVide()).isTrue();
    }

    @Test
    @DisplayName("Plusieurs enregistreurs ET plusieurs nuits : le nombre de nuits s'ajoute aux séries")
    void melange_enregistreurs_et_nuits() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                melange(
                        "PaRecPR1925492_20260422_203000.wav",
                        "PaRecPR1648011_20260423_203000.wav",
                        "PaRecPR1648011_20260424_203000.wav"),
                null,
                List.of());

        assertThat(constatUnique(rendu).details())
                .extracting(Detail::sujet)
                .containsExactly("série 1648011", "série 1925492", "sur 3 nuits");
    }

    @Test
    @DisplayName("Désaccord de série seul : un détail, et rien sur la date")
    void incoherence_serie() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                null, coherence("1925492", "PaRecPR1648011_20260422_203000.wav"), List.of());

        assertThat(constatUnique(rendu).details()).singleElement().satisfies(detail -> {
            assertThat(detail.sujet()).isEqualTo("série déclarée absente des fichiers");
            assertThat(detail.precision()).isEqualTo("1925492 (fichiers : 1648011)");
        });
    }

    @Test
    @DisplayName("Désaccord de date seul : un détail, et rien sur la série")
    void incoherence_date() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                null, coherence("1925492", "PaRecPR1925492_20260430_203000.wav"), List.of());

        assertThat(constatUnique(rendu).details()).singleElement().satisfies(detail -> {
            assertThat(detail.sujet()).isEqualTo("date du journal hors de la nuit des fichiers");
            assertThat(detail.precision()).isEqualTo("22/04/2026 (fichiers : 30/04/2026)");
        });
    }

    @Test
    @DisplayName("Série ET date en désaccord : deux détails distincts, pas une phrase liée par « et »")
    void incoherence_serie_et_date() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                null, coherence("1925492", "PaRecPR1648011_20260430_203000.wav"), List.of());

        // La phrase d'origine reliait les deux par un « et », obligeant à reconstituer mentalement ce qui
        // portait sur quoi. Deux détails séparés se lisent sans effort.
        assertThat(constatUnique(rendu).details())
                .extracting(Detail::sujet)
                .containsExactly("série déclarée absente des fichiers", "date du journal hors de la nuit des fichiers");
    }

    @Test
    @DisplayName("#147 : chaque passage déjà présent est un détail, avec son année et son point")
    void nuit_deja_importee() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                null,
                null,
                List.of(new PassageExistant(2, 2026, "640380", "A1"), new PassageExistant(7, 2025, "130711", "Z4")));

        assertThat(constatUnique(rendu).details())
                .extracting(Detail::sujet)
                .containsExactly("n° 2 (2026) au carré 640380, point A1", "n° 7 (2025) au carré 130711, point Z4");
    }

    @Test
    @DisplayName("Trois relevés à la fois : trois constats, pas trois libellés concurrents")
    void les_trois_ensemble() {
        CompteRendu rendu = AvertissementsInspection.rediger(
                melange("PaRecPR1925492_20260422_203000.wav", "PaRecPR1648011_20260422_203000.wav"),
                coherence("1111111", "PaRecPR1648011_20260430_203000.wav"),
                List.of(new PassageExistant(2, 2026, "640380", "A1")));

        assertThat(rendu.constats()).hasSize(3);
        assertThat(rendu.severite())
                .as("aucun n'est un échec : le compte rendu entier est un avertissement")
                .isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("La question de confirmation liste les passages, une par ligne")
    void question_de_confirmation() {
        String question = AvertissementsInspection.question(
                List.of(new PassageExistant(2, 2026, "640380", "A1"), new PassageExistant(7, 2026, "130711", "Z4")));

        // La modale a la place de tout dire. La phrase d'origine joignait les passages par des
        // points-virgules, sur une seule ligne, et se terminait par la question.
        assertThat(question).isEqualTo("""
                        Cette nuit a déjà été importée :
                          - n° 2 (2026) au carré 640380, point A1
                          - n° 7 (2026) au carré 130711, point Z4

                        Importer quand même comme nouveau passage ?""");
        assertThat(AvertissementsInspection.question(List.of()))
                .as("rien à confirmer : l'import part sans poser de question")
                .isEmpty();
    }
}
