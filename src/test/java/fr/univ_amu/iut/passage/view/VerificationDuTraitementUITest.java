package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import fr.univ_amu.iut.passage.viewmodel.VerificationDuTraitement;
import java.util.Optional;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le câblage du bouton « Vérifier le traitement » (#5862) : quand il se grise et ce qu'il en dit, et ce
/// que le bandeau de la vue reçoit d'un clic. Sans FXML : c'est la couture entre le bouton, le geste et
/// le ViewModel qui est éprouvée, sur un exécuteur synchrone.
@ExtendWith(ApplicationExtension.class)
class VerificationDuTraitementUITest {

    private static final Long ID_PASSAGE = 42L;
    private static final String LIEN = "https://vigiechiro.herokuapp.com/#/participations/p-1";

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);
    private final ImportObservations importation = mock(ImportObservations.class);
    private final SimpleStringProperty lienParticipation = new SimpleStringProperty("");

    private Button bouton;
    private StackPane enveloppe;
    private PassageViewModel viewModel;

    @BeforeEach
    void monter() {
        bouton = new Button("Vérifier le traitement");
        enveloppe = new StackPane(bouton);
        viewModel = new PassageViewModel(
                mock(ServicePassage.class), mock(ServiceReactivationPassage.class), mock(PortailVigieChiro.class));
    }

    private void cabler(VerificationDuTraitement verification) {
        VerificationDuTraitementUI.cabler(
                new VerificationDuTraitementUI.Vue(bouton, enveloppe),
                verification,
                lienParticipation,
                new IndicateurOccupation(new StackPane(), new ExecuteurTacheSynchrone()),
                () -> ID_PASSAGE,
                viewModel);
    }

    private VerificationDuTraitement connecte() {
        return new VerificationDuTraitement(Optional.of(suivi), Optional.of(importation));
    }

    @Test
    @DisplayName("sans participation liée : le bouton est grisé et dit qu'il n'y a rien à vérifier")
    void sans_participation_le_bouton_dit_pourquoi() {
        cabler(connecte());

        assertThat(bouton.isDisabled()).isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe)).isEqualTo(VerificationDuTraitementUI.SANS_PARTICIPATION);
    }

    @Test
    @DisplayName("hors connexion : le bouton est grisé même avec une participation, et dit de se connecter")
    void hors_connexion_le_bouton_dit_pourquoi() {
        cabler(new VerificationDuTraitement(Optional.empty(), Optional.of(importation)));
        lienParticipation.set(LIEN);

        assertThat(bouton.isDisabled()).isTrue();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe)).isEqualTo(VerificationDuTraitementUI.HORS_CONNEXION);
    }

    @Test
    @DisplayName("connecté, participation liée : le bouton s'ouvre, et son infobulle dit ce qu'il fait")
    void connecte_et_lie_le_bouton_s_ouvre() {
        cabler(connecte());
        lienParticipation.set(LIEN);

        assertThat(bouton.isDisabled()).isFalse();
        assertThat(InfobulleDeBlocage.texteDe(enveloppe)).isEqualTo(VerificationDuTraitementUI.CE_QUE_FAIT_LE_GESTE);
        verify(suivi, never()).relever(ID_PASSAGE);
    }

    @Test
    @DisplayName("un clic relève, et le bandeau de la vue reçoit ce que le relevé a dit")
    void un_clic_pose_le_resultat_dans_le_bandeau() {
        when(suivi.relever(ID_PASSAGE))
                .thenReturn(new Traitement(EtatTraitement.EN_COURS, null, null, null, null, null));
        cabler(connecte());
        lienParticipation.set(LIEN);

        bouton.fire();

        assertThat(viewModel.retourProperty().get().texte()).startsWith("Analyse en cours.");
        assertThat(viewModel.retourProperty().get().severite()).isEqualTo(Severite.INFO);
    }

    @Test
    @DisplayName("un relevé impossible se lit dans le bandeau, comme une erreur, avec sa cause")
    void un_releve_impossible_va_au_bandeau() {
        when(suivi.relever(ID_PASSAGE)).thenThrow(new RegleMetierException("Vigie-Chiro est injoignable (délai)"));
        cabler(connecte());
        lienParticipation.set(LIEN);

        bouton.fire();

        assertThat(viewModel.retourProperty().get().texte()).contains("Vigie-Chiro est injoignable (délai)");
        assertThat(viewModel.retourProperty().get().severite()).isEqualTo(Severite.ERREUR);
    }
}
