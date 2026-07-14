package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture des résultats Tadarida ([DonneesVigieChiro]) : `GET /participations/#id/donnees` → fichiers +
/// observations. Fonctions pures et tolérantes, testées sur la forme réelle (participation Car130711).
class DonneesVigieChiroTest {

    @Test
    @DisplayName("donnees : _id (ancrage #1203) + titre + observations Tadarida (taxon, proba, freq, temps,"
            + " alternative)")
    void donnees_fichier_et_observations() {
        // Forme réelle (extrait de GET /participations/#id/donnees).
        String corps = "{\"_items\":[{\"_id\":\"d1\","
                + "\"titre\":\"Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000\",\"observations\":["
                + "{\"frequence_mediane\":44.0,\"temps_debut\":0.8,\"temps_fin\":4.7,\"tadarida_probabilite\":0.99,"
                + "\"tadarida_taxon\":{\"_id\":\"x\",\"libelle_court\":\"Pipkuh\"},"
                + "\"tadarida_taxon_autre\":[{\"taxon\":{\"libelle_court\":\"noise\"},\"probabilite\":0.04}]},"
                + "{\"tadarida_taxon\":{\"libelle_court\":\"noise\"},\"tadarida_probabilite\":0.9,"
                + "\"temps_debut\":0.1,\"temps_fin\":5.0}]}]}";

        List<DonneeVigieChiro> donnees = DonneesVigieChiro.donnees(corps);

        assertThat(donnees).hasSize(1);
        DonneeVigieChiro donnee = donnees.getFirst();
        assertThat(donnee.id())
                .as("le _id de la donnée est l'ancrage d'une correction (PATCH positionnel, #1203)")
                .isEqualTo("d1");
        assertThat(donnee.titre()).isEqualTo("Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000");
        assertThat(donnee.observations())
                .containsExactly(
                        new ObservationVigieChiro(
                                0, "Pipkuh", 0.99, 44.0, 0.8, 4.7, "noise", null, null, null, null, List.of()),
                        new ObservationVigieChiro(
                                1, "noise", 0.9, null, 0.1, 5.0, null, null, null, null, null, List.of()));
    }

    @Test
    @DisplayName("observations : l'indice brut du tableau serveur est conservé malgré le filtrage, et la"
            + " certitude observateur (jeton SUR|PROBABLE|POSSIBLE) est lue (#1139)")
    void indice_brut_et_certitude() {
        // L'observation d'indice 1 n'a pas de taxon Tadarida : ignorée. Celle d'indice 2 doit garder
        // l'indice 2 (la cible du PATCH positionnel), pas glisser à 1 dans la liste filtrée.
        String corps = "{\"_items\":[{\"_id\":\"d1\",\"titre\":\"F\",\"observations\":["
                + "{\"tadarida_taxon\":{\"libelle_court\":\"Pipkuh\"},\"tadarida_probabilite\":0.9},"
                + "{\"frequence_mediane\":40.0},"
                + "{\"tadarida_taxon\":{\"libelle_court\":\"Eptser\"},\"tadarida_probabilite\":0.7,"
                + "\"observateur_taxon\":{\"libelle_court\":\"Pippip\"},\"observateur_probabilite\":\"SUR\"}]}]}";

        List<ObservationVigieChiro> observations =
                DonneesVigieChiro.donnees(corps).getFirst().observations();

        assertThat(observations)
                .extracting(
                        ObservationVigieChiro::indiceServeur,
                        ObservationVigieChiro::taxonTadarida,
                        ObservationVigieChiro::taxonObservateur,
                        ObservationVigieChiro::certitudeObservateur)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "Pipkuh", null, null),
                        org.assertj.core.groups.Tuple.tuple(
                                2, "Eptser", "Pippip", fr.univ_amu.iut.commun.model.CertitudeObservateur.SUR));
    }

    @Test
    @DisplayName("donnees : donnée sans titre et observation sans taxon Tadarida ignorées ; illisible → vide")
    void donnees_tolerant() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"d1\",\"observations\":[{\"tadarida_taxon\":{\"libelle_court\":\"Pipkuh\"}}]}," // sans
                // titre
                + "{\"titre\":\"F\",\"observations\":[{\"frequence_mediane\":40.0}]}]}"; // obs sans
        // taxon, donnée sans _id

        List<DonneeVigieChiro> donnees = DonneesVigieChiro.donnees(corps);

        assertThat(donnees).singleElement().satisfies(donnee -> {
            assertThat(donnee.id())
                    .as("donnée sans _id : lisible quand même (mais non adressable pour une correction)")
                    .isNull();
            assertThat(donnee.titre()).isEqualTo("F");
            assertThat(donnee.observations()).isEmpty();
        });
        assertThat(DonneesVigieChiro.donnees("nope")).isEmpty();
    }

    @Test
    @DisplayName("#1417 : l'avis du validateur et le fil de discussion sont lus — ils arrivaient déjà dans"
            + " cette réponse, le parseur les jetait")
    void avis_du_validateur_et_fil() {
        // Forme réelle : `validateur_taxon` est un taxon imbriqué (comme `observateur_taxon`),
        // `validateur_probabilite` un jeton du même domaine fermé, `messages` un tableau de
        // sous-documents {message, auteur, date} (schéma de `donnees`, spike de #724).
        String corps = "{\"_items\":[{\"_id\":\"d1\",\"titre\":\"F\",\"observations\":["
                + "{\"tadarida_taxon\":{\"libelle_court\":\"Pipkuh\"},"
                + "\"observateur_taxon\":{\"libelle_court\":\"Pippip\"},\"observateur_probabilite\":\"POSSIBLE\","
                + "\"validateur_taxon\":{\"libelle_court\":\"Pipnat\"},\"validateur_probabilite\":\"SUR\","
                + "\"messages\":["
                + "{\"message\":\"Tu es sûr ? La médiane est basse.\",\"auteur\":\"u-validateur\","
                + "\"date\":\"Sat, 11 Jul 2026 21:04:00 GMT\"},"
                + "{\"message\":\"Non, je doute.\",\"auteur\":{\"_id\":\"u-moi\"}}]}]}]}";

        ObservationVigieChiro observation =
                DonneesVigieChiro.donnees(corps).getFirst().observations().getFirst();

        assertThat(observation.taxonObservateur())
                .as("l'observateur avait corrigé Tadarida...")
                .isEqualTo("Pippip");
        assertThat(observation.taxonValidateur())
                .as("...et un expert du MNHN l'a révisé : c'est ce mot-là qui manquait à l'écran")
                .isEqualTo("Pipnat");
        assertThat(observation.certitudeValidateur())
                .as("la certitude du validateur partage le domaine fermé de celle de l'observateur")
                .isEqualTo(fr.univ_amu.iut.commun.model.CertitudeObservateur.SUR);

        assertThat(observation.messages()).hasSize(2);
        assertThat(observation.messages().getFirst())
                .as("auteur donné brut (objectid) + date RFC 1123 du serveur")
                .isEqualTo(new MessageVigieChiro(
                        "u-validateur",
                        "Tu es sûr ? La médiane est basse.",
                        java.time.Instant.parse("2026-07-11T21:04:00Z")));
        assertThat(observation.messages().get(1))
                .as("auteur donné résolu ({_id: …}) : les deux formes existent selon les projections ;"
                        + " date absente → fil daté à moitié, mais lisible")
                .isEqualTo(new MessageVigieChiro("u-moi", "Non, je doute.", null));
    }

    @Test
    @DisplayName("#1417 : fil absent → liste vide (le cas courant) ; entrée sans texte ou date illisible →"
            + " on garde ce qu'on peut, on ne jette pas le message")
    void fil_tolerant() {
        String corps = "{\"_items\":[{\"_id\":\"d1\",\"titre\":\"F\",\"observations\":["
                + "{\"tadarida_taxon\":{\"libelle_court\":\"Pipkuh\"}}," // aucun champ `messages`
                + "{\"tadarida_taxon\":{\"libelle_court\":\"Eptser\"},\"messages\":["
                + "{\"auteur\":\"u-1\"}," // pas de texte : ce n'est pas un message
                + "{\"message\":\"Vu.\",\"date\":\"pas une date\"}]}]}]}";

        List<ObservationVigieChiro> observations =
                DonneesVigieChiro.donnees(corps).getFirst().observations();

        assertThat(observations.getFirst().messages())
                .as("aucun fil ouvert : liste vide, jamais null")
                .isEmpty();
        assertThat(observations.get(1).messages())
                .as("l'entrée sans texte est écartée ; celle dont la date est illisible reste un message")
                .containsExactly(new MessageVigieChiro(null, "Vu.", null));
    }
}
