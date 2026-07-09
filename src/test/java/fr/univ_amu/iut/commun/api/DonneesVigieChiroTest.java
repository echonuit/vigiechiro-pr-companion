package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture des résultats Tadarida ([DonneesVigieChiro]) : `GET /participations/#id/donnees` → fichiers +
/// observations. Fonctions pures et tolérantes, testées sur la forme réelle (participation Car130711).
class DonneesVigieChiroTest {

    @Test
    @DisplayName("donnees : titre + observations Tadarida (taxon, proba, freq, temps, alternative)")
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
        assertThat(donnee.titre()).isEqualTo("Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000");
        assertThat(donnee.observations())
                .containsExactly(
                        new ObservationVigieChiro("Pipkuh", 0.99, 44.0, 0.8, 4.7, "noise", null, null),
                        new ObservationVigieChiro("noise", 0.9, null, 0.1, 5.0, null, null, null));
    }

    @Test
    @DisplayName("donnees : donnée sans titre et observation sans taxon Tadarida ignorées ; illisible → vide")
    void donnees_tolerant() {
        String corps = "{\"_items\":["
                + "{\"_id\":\"d1\",\"observations\":[{\"tadarida_taxon\":{\"libelle_court\":\"Pipkuh\"}}]}," // sans
                // titre
                + "{\"_id\":\"d2\",\"titre\":\"F\",\"observations\":[{\"frequence_mediane\":40.0}]}]}"; // obs sans
        // taxon

        List<DonneeVigieChiro> donnees = DonneesVigieChiro.donnees(corps);

        assertThat(donnees).singleElement().satisfies(donnee -> {
            assertThat(donnee.titre()).isEqualTo("F");
            assertThat(donnee.observations()).isEmpty();
        });
        assertThat(DonneesVigieChiro.donnees("nope")).isEmpty();
    }
}
