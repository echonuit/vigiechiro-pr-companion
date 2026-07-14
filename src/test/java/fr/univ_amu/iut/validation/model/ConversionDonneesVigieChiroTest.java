package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.model.ModeValidation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Conversion des résultats VigieChiro ([DonneeVigieChiro]) en [LigneObservation] locales
/// ([ConversionDonneesVigieChiro], #719 axe 4.2) : projection **fidèle au parseur CSV** (mêmes unités,
/// fréquence arrondie à l'entier), aplatissement par observation, mode de validation déduit du taxon
/// observateur, et **ancrage plateforme** propagé (#1139 : `_id` de la donnée + indice brut serveur).
/// Fonction pure.
class ConversionDonneesVigieChiroTest {

    @Test
    @DisplayName("une observation Tadarida brute -> LigneObservation (titre = séquence, freq arrondie,"
            + " NON_VALIDE, ancrage (d1, 0))")
    void observation_brute() {
        DonneeVigieChiro donnee = new DonneeVigieChiro(
                "d1",
                "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000",
                List.of(new ObservationVigieChiro(
                        0, "Pipkuh", 0.99, 44.6, 0.8, 4.7, "noise", null, null, null, null, List.of())));

        assertThat(ConversionDonneesVigieChiro.enLignes(List.of(donnee)))
                .containsExactly(new LigneObservation(
                        "Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000",
                        0.8,
                        4.7,
                        45, // 44.6 arrondi à l'entier (colonne median_freq_khz INTEGER)
                        "Pipkuh",
                        0.99,
                        "noise",
                        null,
                        null,
                        ModeValidation.NON_VALIDE,
                        "d1",
                        0,
                        null,
                        null,
                        null));
    }

    @Test
    @DisplayName("taxon observateur présent -> MANUEL + certitude serveur ; fréquence absente -> null ;"
            + " pas de probabilité numérique observateur côté serveur")
    void observation_validee_manuellement() {
        DonneeVigieChiro donnee = new DonneeVigieChiro(
                "d1",
                "F",
                List.of(new ObservationVigieChiro(
                        3,
                        "Eptser",
                        0.70,
                        null,
                        1.0,
                        2.0,
                        null,
                        "Pippip",
                        CertitudeObservateur.SUR,
                        null,
                        null,
                        List.of())));

        assertThat(ConversionDonneesVigieChiro.enLignes(List.of(donnee)))
                .singleElement()
                .satisfies(ligne -> {
                    assertThat(ligne.frequenceMedianeKHz()).isNull();
                    assertThat(ligne.taxonObservateur()).isEqualTo("Pippip");
                    assertThat(ligne.probObservateur())
                            .as("la certitude serveur n'est pas une probabilité numérique")
                            .isNull();
                    assertThat(ligne.certitudeObservateur()).isEqualTo(CertitudeObservateur.SUR);
                    assertThat(ligne.modeValidation()).isEqualTo(ModeValidation.MANUEL);
                    assertThat(ligne.idDonneeVigieChiro()).isEqualTo("d1");
                    assertThat(ligne.indiceVigieChiro())
                            .as("l'indice brut serveur est propagé tel quel (cible du PATCH, #1203)")
                            .isEqualTo(3);
                });
    }

    @Test
    @DisplayName("aplatissement : chaque observation devient une ligne portant le titre de sa donnée"
            + " et son propre ancrage (donnée, indice)")
    void aplatissement_multi_fichiers() {
        DonneeVigieChiro a = new DonneeVigieChiro(
                "d1",
                "A",
                List.of(
                        new ObservationVigieChiro(
                                0, "Pipkuh", 0.9, 40.0, 0.0, 1.0, null, null, null, null, null, List.of()),
                        new ObservationVigieChiro(
                                1, "noise", 0.5, 42.0, 1.0, 2.0, null, null, null, null, null, List.of())));
        DonneeVigieChiro b = new DonneeVigieChiro(
                "d2",
                "B",
                List.of(new ObservationVigieChiro(
                        0, "Nyclei", 0.8, 25.0, 0.0, 3.0, null, null, null, null, null, List.of())));

        assertThat(ConversionDonneesVigieChiro.enLignes(List.of(a, b)))
                .extracting(
                        LigneObservation::nomSequence,
                        LigneObservation::taxonTadarida,
                        LigneObservation::idDonneeVigieChiro,
                        LigneObservation::indiceVigieChiro)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A", "Pipkuh", "d1", 0),
                        org.assertj.core.groups.Tuple.tuple("A", "noise", "d1", 1),
                        org.assertj.core.groups.Tuple.tuple("B", "Nyclei", "d2", 0));
    }

    @Test
    @DisplayName("liste vide -> aucune ligne")
    void vide() {
        assertThat(ConversionDonneesVigieChiro.enLignes(List.of())).isEmpty();
    }
}
