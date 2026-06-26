package fr.univ_amu.iut.qualification.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [QualificationViewModel] (noyau verdict : pré-check + verdict différé +
/// bandeau statut/verdict). Le [ServiceQualification] est mocké (Mockito) : aucune base de données.
/// La liste de la sélection d'écoute est couverte par [SelectionEcouteViewModelTest].
@ExtendWith(MockitoExtension.class)
class QualificationViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ServiceQualification service;

    private QualificationViewModel viewModel;

    @BeforeEach
    void preparer() {
        viewModel = new QualificationViewModel(service);
    }

    /// Stub du contexte lu par `ouvrirSur` après le pré-check : passage transformé, pas encore de
    /// verdict persisté (le bandeau affiche donc `A_VERIFIER`).
    private void stubContexte() {
        when(service.chargerContexte(ID_PASSAGE))
                .thenReturn(new ContexteVerification(
                        "640380",
                        "A1",
                        "Étang de la Tuilière",
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        20,
                        100.0,
                        StatutWorkflow.TRANSFORME,
                        null));
    }

    @Test
    @DisplayName("ouvrirSur mappe le pré-check en 3 feux et amorce le bandeau verdict")
    void ouvrir_mappe_le_precheck() {
        when(service.precheck(ID_PASSAGE)).thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.ORANGE, Feu.ROUGE));
        stubContexte();

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.feuCouvertureProperty().get()).isEqualTo(Feu.VERT);
        assertThat(viewModel.feuNombreProperty().get()).isEqualTo(Feu.ORANGE);
        assertThat(viewModel.feuRenommageProperty().get()).isEqualTo(Feu.ROUGE);
        assertThat(viewModel.preCheckAnomalieProperty().get()).isTrue();
        assertThat(viewModel.statutProperty().get()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(viewModel.verdictActuelProperty().get()).isEqualTo(Verdict.A_VERIFIER);
        assertThat(viewModel.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Sans verdict décisif, l'enregistrement est refusé sans toucher au service")
    void verdict_manquant_refuse() {
        assertThat(viewModel.peutEnregistrer().get()).isFalse();
        viewModel.choisirVerdict(Verdict.A_VERIFIER);
        assertThat(viewModel.peutEnregistrer().get()).isFalse();

        viewModel.enregistrer();

        assertThat(viewModel.messageProperty().get()).contains("verdict");
        verify(service, never()).enregistrerVerdict(any(), any(), any());
    }

    @Test
    @DisplayName("Enregistrer un verdict OK persiste et passe l'état à ENREGISTRE")
    void enregistrer_ok() {
        when(service.precheck(ID_PASSAGE)).thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT));
        stubContexte();
        when(service.estAJeter(ID_PASSAGE)).thenReturn(false);
        viewModel.ouvrirSur(ID_PASSAGE);
        viewModel.commentaireProperty().set("Beaux contacts de pipistrelle.");
        viewModel.choisirVerdict(Verdict.OK);

        assertThat(viewModel.peutEnregistrer().get()).isTrue();
        viewModel.enregistrer();

        verify(service).enregistrerVerdict(ID_PASSAGE, Verdict.OK, "Beaux contacts de pipistrelle.");
        assertThat(viewModel.etatVerdictProperty().get()).isEqualTo(EtatVerdict.ENREGISTRE);
        assertThat(viewModel.verdictActuelProperty().get()).isEqualTo(Verdict.OK);
        assertThat(viewModel.statutProperty().get()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(viewModel.avertissementAJeterProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("Un verdict « à jeter » enregistré déclenche l'avertissement R14")
    void verdict_a_jeter_avertit() {
        when(service.precheck(ID_PASSAGE)).thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT));
        stubContexte();
        when(service.estAJeter(ID_PASSAGE)).thenReturn(true);
        viewModel.ouvrirSur(ID_PASSAGE);
        viewModel.choisirVerdict(Verdict.A_JETER);

        viewModel.enregistrer();

        assertThat(viewModel.etatVerdictProperty().get()).isEqualTo(EtatVerdict.ENREGISTRE);
        // #258 : message affiché (lié à lblAvertissement) → pas de code de règle visible.
        assertThat(viewModel.avertissementAJeterProperty().get())
                .contains("à jeter")
                .doesNotContain("R14");
    }

    @Test
    @DisplayName("Un commentaire vide est transmis comme null (commentaire existant conservé)")
    void commentaire_vide_devient_null() {
        when(service.precheck(ID_PASSAGE)).thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT));
        stubContexte();
        when(service.estAJeter(ID_PASSAGE)).thenReturn(false);
        viewModel.ouvrirSur(ID_PASSAGE);
        viewModel.choisirVerdict(Verdict.DOUTEUX);

        viewModel.enregistrer();

        verify(service).enregistrerVerdict(eq(ID_PASSAGE), eq(Verdict.DOUTEUX), isNull());
    }

    @Test
    @DisplayName("Un passage introuvable à l'ouverture est restitué dans le message")
    void passage_introuvable() {
        when(service.precheck(ID_PASSAGE)).thenThrow(new RegleMetierException("Passage introuvable : 42"));

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.messageProperty().get()).contains("introuvable");
        assertThat(viewModel.feuCouvertureProperty().get()).isNull();
    }

    @Test
    @DisplayName("Une réouverture qui échoue nettoie les feux/statut/verdict du passage précédent")
    void ouvrir_en_echec_nettoie_l_etat_precedent() {
        when(service.precheck(ID_PASSAGE))
                .thenReturn(new PreCheckNuit.Diagnostic(Feu.VERT, Feu.VERT, Feu.VERT))
                .thenThrow(new RegleMetierException("Passage introuvable : 42"));
        stubContexte();
        viewModel.ouvrirSur(ID_PASSAGE);
        assertThat(viewModel.feuCouvertureProperty().get()).isEqualTo(Feu.VERT);

        viewModel.ouvrirSur(ID_PASSAGE);

        assertThat(viewModel.feuCouvertureProperty().get()).isNull();
        assertThat(viewModel.statutProperty().get()).isNull();
        assertThat(viewModel.verdictActuelProperty().get()).isEqualTo(Verdict.A_VERIFIER);
        assertThat(viewModel.messageProperty().get()).contains("introuvable");
    }
}
