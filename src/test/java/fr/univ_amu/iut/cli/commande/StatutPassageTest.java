package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.Vent;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Rendu (texte + JSON) de `statut-passage` (#618). Testé sur des projections [DetailPassage] construites
/// à la main : `rendreTexte`/`projeter` sont des fonctions **pures** (sans base ni effet de bord), donc
/// vérifiables sans injecteur ni SQLite. Deux cas couvrent les branches : passage complet (déposé, météo
/// renseignée, résultats Tadarida présents) et passage minimal (aucun verdict, non déposé, météo vide,
/// aucun résultat).
class StatutPassageTest {

    private static DetailPassage passageComplet() {
        return new DetailPassage(
                2,
                2026,
                "2026-06-15",
                "21:30:00",
                "05:45:00",
                "SM4-0042",
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                "2026-06-20",
                4_509_715_660L, // ~4,2 Go
                536_870_912L, // 512 Mo
                128,
                640.0,
                new MeteoReleve(18.5, 12.0, Vent.MOYEN, CouvertureNuageuse.DE_25_A_50),
                new DecompteAudio(128, 128));
    }

    private static DetailPassage passageMinimal() {
        return new DetailPassage(
                1,
                2026,
                "2026-06-15",
                "21:30:00",
                "05:45:00",
                "SM4-0042",
                StatutWorkflow.IMPORTE,
                null, // aucun verdict posé
                null, // non déposé
                0L,
                0L,
                0,
                0.0,
                new MeteoReleve(null, null, null, null), // météo vide
                new DecompteAudio(0, 0));
    }

    @Test
    @DisplayName("Texte d'un passage complet : protocole, statut, verdict, volumes, durée, météo, Tadarida")
    void texte_passage_complet() {
        ResultatsIdentification resultats =
                new ResultatsIdentification(9L, "transformes/passage2_Vu.csv", "\"Vu\"", "2026-06-21T08:00:00", 42L);

        String texte = StatutPassage.rendreTexte(42L, passageComplet(), Optional.of(resultats));

        assertThat(texte)
                .contains("Passage #42")
                .contains("année 2026, passage n°2")
                .contains("Déposé")
                .contains("OK")
                .contains("déposé le 2026-06-20")
                .contains("bruts 4,2 Go, séquences 512 Mo")
                .contains("128 (durée enregistrée 10 min 40 s)")
                .contains("début 18,5 °C")
                .contains("vent moyen")
                .contains("nuages 25 à 50 %")
                .contains("oui (\"Vu\", importé le 2026-06-21T08:00:00)");
    }

    @Test
    @DisplayName("Texte d'un passage minimal : verdict en attente, non déposé, météo absente, Tadarida non")
    void texte_passage_minimal() {
        String texte = StatutPassage.rendreTexte(1L, passageMinimal(), Optional.empty());

        assertThat(texte)
                .contains("Passage #1")
                .contains("en attente")
                .contains("non déposé")
                .contains("bruts 0 Ko, séquences 0 Ko")
                .contains("non renseignée")
                .doesNotContain("oui (");
    }

    @Test
    @DisplayName("JSON d'un passage complet : nombres typés, libellés d'enum, chemin Tadarida")
    void json_passage_complet() {
        ResultatsIdentification resultats =
                new ResultatsIdentification(9L, "transformes/passage2_Vu.csv", "\"Vu\"", "2026-06-21T08:00:00", 42L);

        Map<String, Object> objet = StatutPassage.projeter(42L, passageComplet(), Optional.of(resultats));

        assertThat(objet)
                .containsEntry("passage", 42L)
                .containsEntry("annee", 2026)
                .containsEntry("numeroPassage", 2)
                .containsEntry("statut", "Déposé")
                .containsEntry("verdict", "OK")
                .containsEntry("nombreSequences", 128)
                .containsEntry("resultatsTadarida", true)
                .containsEntry("cheminResultatsTadarida", "transformes/passage2_Vu.csv");
    }

    @Test
    @DisplayName("JSON d'un passage minimal : verdict/dépôt/chemin à null, Tadarida à false")
    void json_passage_minimal() {
        Map<String, Object> objet = StatutPassage.projeter(1L, passageMinimal(), Optional.empty());

        assertThat(objet).containsEntry("resultatsTadarida", false);
        assertThat(objet.get("verdict")).isNull();
        assertThat(objet.get("deposeLe")).isNull();
        assertThat(objet.get("cheminResultatsTadarida")).isNull();
    }
}
