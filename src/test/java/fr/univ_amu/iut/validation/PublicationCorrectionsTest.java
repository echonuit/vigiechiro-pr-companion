package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.TriPublication;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Mock
    ImportVigieChiro importateur;

    /// Publication **sans** import disponible : l'ancrage manquant reste écarté et compté (comportement
    /// des injecteurs sans `connexion`).
    private PublicationCorrections publication() {
        return new PublicationCorrections(client, liens, observations, Optional.empty());
    }

    /// Publication **avec** import : elle peut acquérir l'ancrage manquant avant de pousser (#1838).
    private PublicationCorrections publicationAvecImport() {
        return new PublicationCorrections(client, liens, observations, Optional.of(importateur));
    }

    /// Observation revue (taxon observateur posé), aux champs de publication paramétrables.
    private static Observation revue(
            long id, String taxonObservateur, Certitude certitude, String idDonnee, Integer indice) {
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
    @DisplayName("#1838 : l'ancrage manquant d'une nuit rattachée est acquis AVANT de pousser")
    void ancrage_manquant_acquis_avant_de_pousser() {
        // Nuit importée par CSV (#1565) : rattachée, mais ses observations n'ont pas d'ancrage. Le
        // ré-import (remplacer = true) le rapatrie en préservant les validations ; on simule ici son effet
        // en rendant des observations désormais ancrées.
        when(importateur.estRattache(7L)).thenReturn(true);
        when(importateur.ancrageManquant(7L)).thenReturn(true);
        when(observations.revuesDuPassage(7L)).thenReturn(List.of(revue(1L, "Pippip", Certitude.SUR, "d1", 0)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation("d1", 0, "obj-pippip", Certitude.SUR, true))
                .thenReturn(ResultatEcriture.reussie());

        BilanPublication bilan = publicationAvecImport().publier(7L, progres -> {}, JetonAnnulation.neutre());

        // remplacer = true : c'est ce qui préserve les validations de l'observateur (publier ne doit
        // jamais coûter ses corrections à l'utilisateur).
        verify(importateur).importer(eq(7L), eq(true), any(SuiviPagination.class));
        assertThat(bilan.poussees()).isEqualTo(1);
        assertThat(bilan.sansAncrage()).isZero();
    }

    @Test
    @DisplayName("#1838 : une nuit déjà ancrée ne paie aucun rapatriement")
    void ancrage_deja_present_aucun_import() {
        when(importateur.estRattache(7L)).thenReturn(true);
        when(importateur.ancrageManquant(7L)).thenReturn(false);
        when(observations.revuesDuPassage(7L)).thenReturn(List.of(revue(1L, "Pippip", Certitude.SUR, "d1", 0)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation("d1", 0, "obj-pippip", Certitude.SUR, true))
                .thenReturn(ResultatEcriture.reussie());

        publicationAvecImport().publier(7L, progres -> {}, JetonAnnulation.neutre());

        verify(importateur, never()).importer(anyLong(), anyBoolean(), any(SuiviPagination.class));
    }

    @Test
    @DisplayName("#1838 : nuit NON rattachée : rien à ancrer, les observations restent écartées et comptées")
    void non_rattachee_reste_ecartee() {
        // Chemin non nominal : sans participation, il n'y a rien à quoi s'ancrer. On ne tente pas un
        // rapatriement voué à l'échec ; l'utilisateur doit d'abord rattacher la nuit.
        when(importateur.estRattache(7L)).thenReturn(false);
        when(observations.revuesDuPassage(7L)).thenReturn(List.of(revue(1L, "Pippip", Certitude.SUR, null, null)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));

        BilanPublication bilan = publicationAvecImport().publier(7L, progres -> {}, JetonAnnulation.neutre());

        verify(importateur, never()).importer(anyLong(), anyBoolean(), any(SuiviPagination.class));
        assertThat(bilan.poussees()).isZero();
        assertThat(bilan.sansAncrage()).isEqualTo(1);
    }

    @Test
    @DisplayName("#1838 : « Annuler » s'honore à chaque page rapatriée, pas seulement à la fin")
    void annulation_honoree_page_par_page() {
        when(importateur.estRattache(7L)).thenReturn(true);
        when(importateur.ancrageManquant(7L)).thenReturn(true);
        // L'import réel notifie le suivi à CHAQUE page ; on le simule pour vérifier que le jeton y est
        // honoré - sans quoi « Annuler » ne prendrait effet qu'après les dizaines de pages.
        doAnswer(invocation -> {
                    invocation.getArgument(2, SuiviPagination.class).surPage(1, 48);
                    return null;
                })
                .when(importateur)
                .importer(eq(7L), eq(true), any(SuiviPagination.class));
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        assertThatThrownBy(() -> publicationAvecImport().publier(7L, progres -> {}, jeton))
                .as("annulé dès la première page : rien ne doit partir vers la plateforme")
                .isInstanceOf(OperationAnnuleeException.class);

        verify(client, never()).corrigerObservation(any(), anyInt(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("classe les revues : poussable envoyée ; sans certitude, sans ancrage et hors référentiel"
            + " écartées et comptées")
    void classe_et_pousse() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", Certitude.SUR, "d1", 0),
                        revue(2L, "Pippip", null, "d1", 1), // certitude non déclarée
                        revue(3L, "Pippip", Certitude.SUR, null, null), // import CSV : sans ancrage
                        revue(4L, "Barbar", Certitude.SUR, "d2", 0))); // taxon sans objectid
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation("d1", 0, "obj-pippip", Certitude.SUR, true))
                .thenReturn(ResultatEcriture.reussie());

        BilanPublication bilan = publication().publier(7L);

        assertThat(bilan.poussees()).isEqualTo(1);
        assertThat(bilan.sansCertitude()).isEqualTo(1);
        assertThat(bilan.sansAncrage()).isEqualTo(1);
        assertThat(bilan.horsReferentiel()).isEqualTo(1);
        assertThat(bilan.sansEchec()).isTrue();
        assertThat(bilan.ecartees()).isEqualTo(3);
        verify(client).corrigerObservation("d1", 0, "obj-pippip", Certitude.SUR, true);
    }

    @Test
    @DisplayName("Passage reconstruit (#1596) : toutes les revues sont sans ancrage → RIEN de publiable,"
            + " aucun envoi vers la plateforme")
    void passage_reconstruit_rien_de_publiable() {
        // Un passage reconstruit par CSV (#1565) porte des observations SANS ancrage plateforme tant qu'il
        // n'a pas été réactivé (#1571). La garde tient par construction : rien ne part, tout est compté
        // « sans ancrage » (l'IHM affiche alors « Rien à publier… réimportez depuis VigieChiro »).
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", Certitude.SUR, null, null),
                        revue(2L, "Barbar", Certitude.PROBABLE, null, null)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON))
                .thenReturn(Map.of("Pippip", "obj-pippip", "Barbar", "obj-barbar"));

        TriPublication tri = publication().trier(7L);
        BilanPublication bilan = publication().publier(7L);

        assertThat(tri.publiables())
                .as("aucune observation ancrée : rien à publier")
                .isEmpty();
        assertThat(tri.sansAncrage()).isEqualTo(2);
        assertThat(bilan.poussees()).as("rien n'est envoyé").isZero();
        assertThat(bilan.sansAncrage()).isEqualTo(2);
        verify(client, never()).corrigerObservation(anyString(), anyInt(), anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName(
            "rafale : no_bilan sur tous les envois SAUF le dernier (le serveur ne régénère son bilan" + " qu'une fois)")
    void no_bilan_sauf_dernier() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", Certitude.SUR, "d1", 0),
                        revue(2L, "Pippip", Certitude.PROBABLE, "d1", 3),
                        revue(3L, "Pippip", Certitude.POSSIBLE, "d2", 1)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation(anyString(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(ResultatEcriture.reussie());

        BilanPublication bilan = publication().publier(7L);

        assertThat(bilan.poussees()).isEqualTo(3);
        verify(client).corrigerObservation("d1", 0, "obj-pippip", Certitude.SUR, false);
        verify(client).corrigerObservation("d1", 3, "obj-pippip", Certitude.PROBABLE, false);
        verify(client).corrigerObservation("d2", 1, "obj-pippip", Certitude.POSSIBLE, true);
    }

    @Test
    @DisplayName("un refus n'interrompt pas la rafale ; un 404 est expliqué « ancrage périmé » (re-compute)")
    void echec_partiel_detaille() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(
                        revue(1L, "Pippip", Certitude.SUR, "d1", 0), revue(2L, "Pippip", Certitude.SUR, "d2", 5)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON)).thenReturn(Map.of("Pippip", "obj-pippip"));
        when(client.corrigerObservation("d1", 0, "obj-pippip", Certitude.SUR, false))
                .thenReturn(ResultatEcriture.echouee("HTTP 404 : donnée introuvable"));
        when(client.corrigerObservation("d2", 5, "obj-pippip", Certitude.SUR, true))
                .thenReturn(ResultatEcriture.reussie());

        BilanPublication bilan = publication().publier(7L);

        assertThat(bilan.poussees()).isEqualTo(1);
        assertThat(bilan.sansEchec()).isFalse();
        assertThat(bilan.echecs()).singleElement().asString().contains("Observation 1", "d1", "ancrage périmé");
    }

    @Test
    @DisplayName("trier : le même classement que publier, sans aucun envoi (aperçu de la confirmation)")
    void trier_apercu_sans_reseau() {
        when(observations.revuesDuPassage(7L))
                .thenReturn(List.of(revue(1L, "Pippip", Certitude.SUR, "d1", 0), revue(2L, "Pippip", null, "d1", 1)));
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
