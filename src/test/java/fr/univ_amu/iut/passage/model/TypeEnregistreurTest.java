package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// Le type d'enregistreur d'une nuit, et la valeur qu'en attend la plateforme (#6233).
class TypeEnregistreurTest {

    @Test
    @DisplayName("#6233 : une nuit importée d'une carte est l'œuvre d'un Passive Recorder")
    void une_nuit_importee_vient_d_un_passive_recorder() {
        assertThat(TypeEnregistreur.de(nuit("{\"feHz\":\"384000\"}"))).contains(TypeEnregistreur.PASSIVE_RECORDER);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("#6233 : une nuit sans paramètres d'acquisition, donc rapatriée, a un appareil inconnu")
    void une_nuit_sans_parametres_a_un_appareil_inconnu(String parametres) {
        assertThat(TypeEnregistreur.de(nuit(parametres))).isEmpty();
    }

    @Test
    @DisplayName("#6233 : la valeur publiée est celle du formulaire web de la plateforme")
    void la_valeur_publiee_est_celle_du_formulaire_web() {
        assertThat(TypeEnregistreur.PASSIVE_RECORDER.valeurPlateforme()).isEqualTo("PassiveRecorder");
    }

    private static Passage nuit(String parametresAcquisition) {
        return new Passage(
                42L,
                1,
                2026,
                "2026-07-03",
                "21:00:00",
                "05:00:00",
                parametresAcquisition,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                7L,
                "1997632",
                null);
    }
}
