package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ResultatCorrection;
import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.TriPublication;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// **Publication des corrections** ([PublicationCorrections], #723) : classification des observations
/// revues (poussable / à compléter / sans ancrage / hors référentiel), rafale `no_bilan` sauf dernier
/// envoi, échecs détaillés sans interrompre, refus net quand rien n'est revu. API mockée : on teste
/// l'orchestration, pas le transport (couvert par `ClientVigieChiroTest`).
@ExtendWith(MockitoExtension.class)
class PublicationCorrectionsTest {

    @Mock
    ClientVigieChiro client;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    ObservationDao observations;

    private PublicationCorrections publication() {
        return new PublicationCorrections(client, liens, observations);
    }

    /// Observation revue (taxon observateur posé), aux champs de publication paramétrables.
    private static Observation revue(
            long id, String taxonObservateur, CertitudeObservateur certitude, String idDonnee, Integer indice) {
        return new Observation(
                id,
                10L,
                0.0,
                5.0,
                45,
                "Pipkuh",
                0.8,
                null,
                taxonObservateur,
                null,
                null,
                false,
                ModeValidation.MANUEL,
                100L,
                false,
                idDonnee,
                indice,
                certitude,
                null,
                null);
    }

    @Test
    @DisplayName("classe les revues : poussable envoyée ; sans certitude, sans ancrage et hors référentiel"
            + " écartées et comptées")
    void classe_et_pousse() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", CertitudeObservateur.SUR, "d1", 0),
                        revue(2L, "Pippip", null, "d1", 1), // certitude non déclarée
                        revue(3L, "Pippip", CertitudeObservateur.SUR, null, null), // import CSV : sans ancrage
                        revue(4L, "Barbar", CertitudeObservateur.SUR, "d2", 0))); // taxon sans objectid
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation("d1", 0, "obj-pippip", CertitudeObservateur.SUR, true))
                .thenReturn(ResultatCorrection.reussie());

        BilanPublication bilan = publication().publier(7L);

        assertThat(bilan.poussees()).isEqualTo(1);
        assertThat(bilan.sansCertitude()).isEqualTo(1);
        assertThat(bilan.sansAncrage()).isEqualTo(1);
        assertThat(bilan.horsReferentiel()).isEqualTo(1);
        assertThat(bilan.sansEchec()).isTrue();
        assertThat(bilan.ecartees()).isEqualTo(3);
        verify(client).corrigerObservation("d1", 0, "obj-pippip", CertitudeObservateur.SUR, true);
    }

    @Test
    @DisplayName(
            "rafale : no_bilan sur tous les envois SAUF le dernier (le serveur ne régénère son bilan" + " qu'une fois)")
    void no_bilan_sauf_dernier() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", CertitudeObservateur.SUR, "d1", 0),
                        revue(2L, "Pippip", CertitudeObservateur.PROBABLE, "d1", 3),
                        revue(3L, "Pippip", CertitudeObservateur.POSSIBLE, "d2", 1)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation(anyString(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(ResultatCorrection.reussie());

        BilanPublication bilan = publication().publier(7L);

        assertThat(bilan.poussees()).isEqualTo(3);
        verify(client).corrigerObservation("d1", 0, "obj-pippip", CertitudeObservateur.SUR, false);
        verify(client).corrigerObservation("d1", 3, "obj-pippip", CertitudeObservateur.PROBABLE, false);
        verify(client).corrigerObservation("d2", 1, "obj-pippip", CertitudeObservateur.POSSIBLE, true);
    }

    @Test
    @DisplayName("un refus n'interrompt pas la rafale ; un 404 est expliqué « ancrage périmé » (re-compute)")
    void echec_partiel_detaille() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", CertitudeObservateur.SUR, "d1", 0),
                        revue(2L, "Pippip", CertitudeObservateur.SUR, "d2", 5)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation("d1", 0, "obj-pippip", CertitudeObservateur.SUR, false))
                .thenReturn(ResultatCorrection.echouee("HTTP 404 : donnée introuvable"));
        when(client.corrigerObservation("d2", 5, "obj-pippip", CertitudeObservateur.SUR, true))
                .thenReturn(ResultatCorrection.reussie());

        BilanPublication bilan = publication().publier(7L);

        assertThat(bilan.poussees()).isEqualTo(1);
        assertThat(bilan.sansEchec()).isFalse();
        assertThat(bilan.echecs()).singleElement().asString().contains("Observation 1", "d1", "ancrage périmé");
    }

    @Test
    @DisplayName("trier : le même classement que publier, sans aucun envoi (aperçu de la confirmation)")
    void trier_apercu_sans_reseau() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", CertitudeObservateur.SUR, "d1", 0), revue(2L, "Pippip", null, "d1", 1)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));

        TriPublication tri = publication().trier(7L);

        assertThat(tri.publiables()).hasSize(1);
        assertThat(tri.sansCertitude()).isEqualTo(1);
        assertThat(tri.ecartees()).isEqualTo(1);
        verify(client, never()).corrigerObservation(anyString(), anyInt(), anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName("aucune observation revue : refus net (RegleMetierException), sans toucher le réseau")
    void aucune_revue_refuse() {
        when(observations.revuesDuPassage(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> publication().publier(7L))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucune observation revue");
        verify(client, never()).corrigerObservation(anyString(), anyInt(), anyString(), any(), anyBoolean());
    }
}
