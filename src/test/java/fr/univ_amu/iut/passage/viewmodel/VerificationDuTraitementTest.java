package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le geste « Vérifier le traitement » de la vue d'un passage (#5862), issue par issue.
///
/// Les états sont posés sans date : la phrase attendue s'écrit alors en entier, sans dépendre du fuseau
/// du poste. L'heure du traitement a son propre banc, `FormatsTraitementFuseauTest`.
class VerificationDuTraitementTest {

    private static final Long ID_PASSAGE = 42L;
    private static final String COMPTE_RENDU = "Observations importées depuis Vigie-Chiro : 1284 observation(s).";

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);
    private final ImportObservations importation = mock(ImportObservations.class);
    private final VerificationDuTraitement verification =
            new VerificationDuTraitement(Optional.of(suivi), Optional.of(importation));

    @Test
    @DisplayName("analyse en cours : le bandeau dit où elle en est, et rien n'est importé")
    void en_cours_rien_n_est_importe() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(etat(EtatTraitement.EN_COURS));

        RetourOperation retour = verification.verifier(ID_PASSAGE);

        assertThat(retour.texte())
                .isEqualTo("Analyse en cours. Comptez plusieurs dizaines de minutes ; vous pouvez fermer"
                        + " l'application.");
        assertThat(retour.severite()).isEqualTo(Severite.INFO);
        verifyNoInteractions(importation);
    }

    @Test
    @DisplayName("analyse terminée, nuit sans observation : elles sont importées, et le bandeau le dit")
    void terminee_les_observations_sont_importees() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(etat(EtatTraitement.FINI));
        when(importation.importer(ID_PASSAGE, false)).thenReturn(COMPTE_RENDU);

        RetourOperation retour = verification.verifier(ID_PASSAGE);

        assertThat(retour.texte()).isEqualTo("Analyse terminée. " + COMPTE_RENDU);
        assertThat(retour.severite()).isEqualTo(Severite.SUCCES);
    }

    @Test
    @DisplayName("analyse terminée, nuit déjà importée : rien ne repart, et le bandeau dit où les remplacer")
    void terminee_deja_importee() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(etat(EtatTraitement.FINI));
        when(importation.aDejaSesObservations(ID_PASSAGE)).thenReturn(true);

        RetourOperation retour = verification.verifier(ID_PASSAGE);

        assertThat(retour.texte())
                .isEqualTo("Analyse terminée. Les observations de cette nuit sont déjà importées. Pour les"
                        + " remplacer, passez par « Sons & validation ».");
        assertThat(retour.severite()).isEqualTo(Severite.INFO);
        verify(importation, never()).importer(ID_PASSAGE, false);
    }

    @Test
    @DisplayName("import en échec : l'analyse reste dite terminée, et l'échec nomme le bouton de cette vue")
    void l_import_echoue_sans_masquer_l_etat() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(etat(EtatTraitement.FINI));
        when(importation.importer(ID_PASSAGE, false)).thenThrow(new RegleMetierException("HTTP 403"));

        RetourOperation retour = verification.verifier(ID_PASSAGE);

        assertThat(retour.texte())
                .isEqualTo("Analyse terminée. L'import des observations a échoué : HTTP 403. Cliquez de nouveau"
                        + " « Vérifier le traitement », ou importez depuis « Sons & validation ».");
        assertThat(retour.severite()).isEqualTo(Severite.ERREUR);
    }

    @Test
    @DisplayName("analyse en échec côté plateforme : le bandeau le dit comme une erreur")
    void analyse_en_echec() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(etat(EtatTraitement.ERREUR));

        RetourOperation retour = verification.verifier(ID_PASSAGE);

        assertThat(retour.texte()).isEqualTo("L'analyse a échoué côté Vigie-Chiro.");
        assertThat(retour.severite()).isEqualTo(Severite.ERREUR);
        verifyNoInteractions(importation);
    }

    @Test
    @DisplayName("plateforme illisible : le refus du relevé remonte tel quel, pour le bandeau")
    void releve_impossible() {
        when(suivi.relever(ID_PASSAGE)).thenThrow(new RegleMetierException("Vigie-Chiro est injoignable"));

        assertThatThrownBy(() -> verification.verifier(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("injoignable");
        verifyNoInteractions(importation);
    }

    @Test
    @DisplayName("hors connexion : le geste n'est pas disponible, et le dit s'il est forcé")
    void hors_connexion() {
        VerificationDuTraitement sansSuivi = new VerificationDuTraitement(Optional.empty(), Optional.of(importation));

        assertThat(sansSuivi.disponible()).isFalse();
        assertThat(verification.disponible()).isTrue();
        assertThatThrownBy(() -> sansSuivi.verifier(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Non connecté");
        verifyNoInteractions(importation);
    }

    private static Traitement etat(EtatTraitement etat) {
        return new Traitement(etat, null, null, null, null, null);
    }
}
