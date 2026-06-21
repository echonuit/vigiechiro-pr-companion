package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Garde de saisie de M-Qualification : quitter l'écran avec un **verdict choisi mais pas enregistré**
/// (brouillon) doit déclencher la confirmation du socle. On vérifie le prédicat
/// [QualificationController#aSaisieNonEnregistree()] (lecture du ViewModel, sans FXML).
@ExtendWith(MockitoExtension.class)
class QualificationControllerGardeTest {

    @Mock
    private ServiceQualification service;

    @Test
    @DisplayName("aSaisieNonEnregistree : faux par défaut, vrai dès qu'un verdict brouillon est choisi")
    void garde_reflete_le_verdict_brouillon() {
        QualificationViewModel verdictVm = new QualificationViewModel(service);
        SelectionEcouteViewModel selectionVm = new SelectionEcouteViewModel(service);
        QualificationController controller =
                new QualificationController(verdictVm, selectionVm, (idPassage, contexte) -> {});

        assertThat(controller.aSaisieNonEnregistree()).isFalse();

        verdictVm.choisirVerdict(Verdict.OK);

        assertThat(controller.aSaisieNonEnregistree()).isTrue();
    }

    @Test
    @DisplayName("aSaisieNonEnregistree : se ré-arme si le commentaire est modifié après enregistrement")
    void garde_se_rearme_si_commentaire_modifie_apres_enregistrement() {
        QualificationViewModel verdictVm = new QualificationViewModel(service);
        SelectionEcouteViewModel selectionVm = new SelectionEcouteViewModel(service);
        QualificationController controller =
                new QualificationController(verdictVm, selectionVm, (idPassage, contexte) -> {});

        verdictVm.choisirVerdict(Verdict.OK);
        verdictVm.enregistrer();
        assertThat(controller.aSaisieNonEnregistree()).isFalse(); // verdict + commentaire enregistrés

        // Le commentaire reste éditable après enregistrement : le modifier recrée un brouillon non
        // persisté → la garde doit de nouveau protéger la sortie.
        verdictVm.commentaireProperty().set("Finalement à revoir");

        assertThat(controller.aSaisieNonEnregistree()).isTrue();
    }

    @Test
    @DisplayName("aSaisieNonEnregistree : se ré-arme si le verdict est changé après enregistrement")
    void garde_se_rearme_si_verdict_change_apres_enregistrement() {
        QualificationViewModel verdictVm = new QualificationViewModel(service);
        SelectionEcouteViewModel selectionVm = new SelectionEcouteViewModel(service);
        QualificationController controller =
                new QualificationController(verdictVm, selectionVm, (idPassage, contexte) -> {});

        verdictVm.choisirVerdict(Verdict.OK);
        verdictVm.enregistrer();
        assertThat(controller.aSaisieNonEnregistree()).isFalse();

        // Changer de verdict après enregistrement : le verdict affiché ne correspond plus au persisté.
        verdictVm.choisirVerdict(Verdict.DOUTEUX);

        assertThat(controller.aSaisieNonEnregistree()).isTrue();
    }
}
