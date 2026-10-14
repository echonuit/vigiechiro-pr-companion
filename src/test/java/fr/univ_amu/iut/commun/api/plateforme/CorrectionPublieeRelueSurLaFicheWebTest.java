package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// La correction d'une observation que Companion publie se relit sur le portail de Vigie-Chiro (#6064,
/// sous-chantier #5645).
///
/// La publication passe par [PublicationCorrections#publier(Long)], que l'écran de validation appelle,
/// avec un vrai client pointé sur la plateforme de test. Seul le côté local est bouchonné : l'observation
/// revue, déjà ancrée sur une donnée de l'état de départ, et la table des taxons.
///
/// Elle écrit sur une participation à elle, `nuit-a-corriger` : `nuit-traitee` est lue par trois autres
/// bancs, et une correction publiée y changerait ce qu'ils lisent.
///
/// Ce banc lit ce que le cas S4-91 de la recette demande de constater, sans le citer par `@CasDeRecette`,
/// qui ferait attendre un clip au tournage : le cas garde son marqueur « hors-portée », dont le motif le
/// nomme. `ScenarioConnectePublicationTest` publie par l'écran, et ne joue qu'au tournage.
@Tag("plateforme-de-test")
@ExtendWith(MockitoExtension.class)
class CorrectionPublieeRelueSurLaFicheWebTest {

    private static final long ID_PASSAGE = 42L;

    /// Le taxon retenu par l'observatrice, là où Tadarida avait rendu « Pippip ».
    private static final String TAXON_RETENU = "Barbar";

    private static final String TAXON_DE_L_OBSERVATEUR =
            "[ng-model=\"observation.observateur_taxon\"] .ui-select-match";

    private static final String CERTITUDE_DE_L_OBSERVATEUR = "select[ng-model=\"observation.observateur_probabilite\"]";

    private static NavigateurDeLaPlateforme navigateur;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    ObservationDao observations;

    @BeforeAll
    static void ouvrirLeNavigateur() {
        navigateur = NavigateurDeLaPlateforme.connecteComme("observatrice");
    }

    @AfterAll
    static void fermerLeNavigateur() {
        if (navigateur != null) {
            navigateur.close();
        }
    }

    @Test
    @DisplayName("#6064 : le portail porte le taxon et la certitude que Companion a publiés pour l'observation")
    void le_portail_porte_la_correction_publiee() {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
        String participation = acces.id("participations:nuit-a-corriger");
        when(observations.revuesDuPassage(ID_PASSAGE))
                .thenReturn(List.of(revue(acces.id("donnees:sequence-a-corriger"), Certitude.PROBABLE)));
        when(liens.tous(LienVigieChiro.ENTITE_TAXON))
                .thenReturn(Map.of("Pippip", acces.id("taxons:pippip"), TAXON_RETENU, acces.id("taxons:barbar")));
        ClientVigieChiro client =
                new ClientVigieChiro(acces.urlDeBase(), () -> Optional.of(acces.jeton("observatrice")));

        BilanPublication bilan =
                new PublicationCorrections(client, liens, observations, Optional.empty()).publier(ID_PASSAGE);

        assertThat(bilan.echecs())
                .as("la plateforme doit accepter la correction")
                .isEmpty();
        navigateur.ouvrir("participations/" + participation + "/donnees");
        Attente.que(
                () -> navigateur.texteDe(TAXON_DE_L_OBSERVATEUR).contains(TAXON_RETENU),
                () -> "le taxon de l'observateur n'a pas paru sur le portail. La ligne disait : "
                        + navigateur.texteDe(TAXON_DE_L_OBSERVATEUR) + " ; la page : " + navigateur.texteDe("body"),
                NavigateurDeLaPlateforme.DELAI_D_UNE_PAGE_MS);
        assertThat(navigateur.valeurDe(CERTITUDE_DE_L_OBSERVATEUR))
                .as("la certitude déclarée dans Companion, et non une autre")
                .isEqualTo(Certitude.PROBABLE.name());
    }

    /// L'observation revue que l'observatrice a corrigée, ancrée sur la première observation de la donnée.
    private static Observation revue(String idDonnee, Certitude certitude) {
        return new Observation(
                1L,
                10L,
                0.5,
                1.25,
                45,
                "Pippip",
                0.92,
                null,
                TAXON_RETENU,
                null,
                null,
                false,
                ModeValidation.MANUEL,
                100L,
                false,
                idDonnee,
                0,
                certitude,
                null,
                null);
    }
}
