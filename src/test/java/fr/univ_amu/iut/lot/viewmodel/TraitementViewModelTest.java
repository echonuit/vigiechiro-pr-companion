package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Zone « Traitement Vigie-Chiro » de M-Lot (#1263) : ce que l'écran dit de l'analyse serveur, et ce qu'il
/// en déduit : notamment **si la relance doit être interdite**. Suivi mocké, aucun réseau.
class TraitementViewModelTest {

    private static final Long ID_PASSAGE = 42L;
    private static final Horloge LE_13_JUILLET = Horloge.figeeAu(LocalDate.of(2026, 7, 13));

    private static final String COMPTE_RENDU = "Observations importées depuis Vigie-Chiro : 1284 observation(s).";

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);
    private final ImportObservations importation = mock(ImportObservations.class);

    @Test
    @DisplayName("hors connexion, le suivi est absent : la zone n'a rien à afficher")
    void suivi_absent() {
        TraitementViewModel vm = new TraitementViewModel(Optional.empty(), LE_13_JUILLET);

        assertThat(vm.disponible()).isFalse();
    }

    @Test
    @DisplayName("à l'ouverture : le dernier état connu est relu dans le cache, sans le moindre appel réseau")
    void ouverture_lit_le_cache() {
        when(suivi.dernierReleve(ID_PASSAGE))
                .thenReturn(Optional.of(new ReleveTraitement(ID_PASSAGE, "part-1", enCours(), "2026-07-13T09:05:00")));
        TraitementViewModel vm = viewModel();

        vm.chargerDernierReleve(ID_PASSAGE);

        assertThat(vm.messageProperty().get()).contains("Analyse en cours");
        assertThat(vm.fraicheurProperty().get())
                .as("l'écran doit dire de QUAND date ce qu'il affiche")
                .contains("Dernier état connu");
        verify(suivi, never()).relever(ID_PASSAGE);
    }

    @Test
    @DisplayName("jamais relevée : la zone le dit, plutôt que de rester muette")
    void jamais_relevee() {
        when(suivi.dernierReleve(ID_PASSAGE)).thenReturn(Optional.empty());
        TraitementViewModel vm = viewModel();

        vm.chargerDernierReleve(ID_PASSAGE);

        assertThat(vm.messageProperty().get()).contains("Analyse non lancée");
        assertThat(vm.fraicheurProperty().get()).isEmpty();
        assertThat(vm.relanceBloqueeProperty().get()).isFalse();
    }

    /// Planifiée, en cours ou relancée : la plateforme travaille, une relance n'a rien à faire (#5682).
    /// Terminée, ou jamais relevée : rien n'est demandé.
    @Test
    @DisplayName("#5682 : l'analyse est « demandée » tant que la plateforme y travaille, et seulement alors")
    void analyse_demandee_suit_le_releve() {
        when(suivi.dernierReleve(ID_PASSAGE)).thenReturn(Optional.empty());
        TraitementViewModel vm = viewModel();

        vm.appliquer(new Traitement(EtatTraitement.PLANIFIE, "2026-07-13T09:00:00+00:00", null, null, null, null));
        assertThat(vm.analyseDemandeeProperty().get()).as("planifiée").isTrue();
        vm.appliquer(enCours());
        assertThat(vm.analyseDemandeeProperty().get()).as("en cours").isTrue();
        vm.appliquer(new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:05:00+00:00", null, null));
        assertThat(vm.analyseDemandeeProperty().get()).as("terminée").isFalse();

        vm.appliquer(enCours());
        vm.chargerDernierReleve(ID_PASSAGE);
        assertThat(vm.analyseDemandeeProperty().get())
                .as("une nuit jamais relevée n'a rien de demandé, même après une autre")
                .isFalse();
    }

    @Test
    @DisplayName("analyse TERMINÉE : la relance est bloquée (elle effacerait les observations du serveur)")
    void terminee_bloque_la_relance() {
        TraitementViewModel vm = viewModel();

        vm.appliquer(new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:05:00+00:00", null, null));

        assertThat(vm.messageProperty().get()).contains("Analyse terminée").doesNotContain("prêtes à être importées");
        assertThat(vm.relanceBloqueeProperty().get())
                .as("le serveur, lui, accepterait de recalculer, et détruirait tout (#1244)")
                .isTrue();
    }

    @Test
    @DisplayName("analyse EN ÉCHEC : relance bloquée aussi dans l'IHM, et le motif du serveur est restitué")
    void echec_bloque_la_relance_et_montre_le_motif() {
        TraitementViewModel vm = viewModel();

        vm.appliquer(new Traitement(
                EtatTraitement.ERREUR,
                null,
                null,
                "2026-07-13T10:05:00+00:00",
                "RuntimeError: tadarida a planté\n  at ligne 12",
                1));

        assertThat(vm.messageProperty().get()).contains("a échoué", "RuntimeError: tadarida a planté");
        assertThat(vm.messageProperty().get())
                .as("une pile entière n'a rien à faire dans une carte : seule la première ligne")
                .doesNotContain("at ligne 12");
        // Relancer après un échec est légitime, mais ce n'est pas un geste d'IHM : il passe par la ligne de
        // commande (--forcer, #1265), justement parce qu'il mérite d'être réfléchi.
        assertThat(vm.relanceBloqueeProperty().get()).isTrue();
    }

    @Test
    @DisplayName("analyse en cours depuis plus de 24 h : on avertit qu'elle semble bloquée")
    void analyse_qui_traine() {
        TraitementViewModel vm = viewModel();

        // Démarrée l'avant-veille : le serveur ne dira jamais qu'il a renoncé, c'est à nous de le suggérer.
        vm.appliquer(new Traitement(EtatTraitement.EN_COURS, null, "2026-07-11T08:00:00+00:00", null, null, null));

        assertThat(vm.alerteProperty().get()).contains("plus de 24 h", "semble bloquée");
        assertThat(vm.relanceBloqueeProperty().get())
                .as("rien n'a encore été calculé : la relancer ne détruirait rien")
                .isFalse();
    }

    @Test
    @DisplayName("analyse en cours depuis ce matin : aucun avertissement (c'est normal)")
    void analyse_recente_pas_d_alerte() {
        TraitementViewModel vm = viewModel();

        vm.appliquer(enCours());

        assertThat(vm.alerteProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("serveur injoignable : on le dit, sans effacer ce qu'on savait déjà")
    void echec_de_releve() {
        TraitementViewModel vm = viewModel();
        vm.appliquer(enCours());

        vm.echec("délai dépassé");

        assertThat(vm.alerteProperty().get()).contains("Impossible de joindre", "délai dépassé");
        assertThat(vm.messageProperty().get())
                .as("l'état connu reste affiché : perdre l'information serait pire que de la dater")
                .contains("Analyse en cours");
        assertThat(vm.enCoursProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#5784 : un relevé qui rend l'analyse terminée importe les observations, et la carte le dit")
    void un_releve_termine_importe() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(terminee());
        when(importation.aDejaSesObservations(ID_PASSAGE)).thenReturn(false);
        when(importation.importer(ID_PASSAGE, false)).thenReturn(COMPTE_RENDU);
        TraitementViewModel vm = viewModelAvecImport();

        vm.appliquer(vm.releverEtImporter(ID_PASSAGE));

        verify(importation).importer(ID_PASSAGE, false);
        assertThat(vm.messageProperty().get()).contains("Analyse terminée");
        assertThat(vm.importObservationsProperty().get()).isEqualTo(COMPTE_RENDU);
    }

    @Test
    @DisplayName("#5784 : une nuit qui a déjà ses observations n'est pas réimportée, et la carte dit où les remplacer")
    void une_nuit_deja_importee_n_est_pas_reimportee() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(terminee());
        when(importation.aDejaSesObservations(ID_PASSAGE)).thenReturn(true);
        TraitementViewModel vm = viewModelAvecImport();

        vm.appliquer(vm.releverEtImporter(ID_PASSAGE));

        verify(importation, never()).importer(ID_PASSAGE, false);
        verify(importation, never()).importer(ID_PASSAGE, true);
        assertThat(vm.importObservationsProperty().get()).contains("déjà importées", "Sons & validation");
    }

    @Test
    @DisplayName("#5784 : un import qui échoue se dit avec son motif, sans masquer que l'analyse est terminée")
    void un_import_qui_echoue_se_dit() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(terminee());
        when(importation.importer(ID_PASSAGE, false)).thenThrow(new RegleMetierException("aucune donnée renvoyée"));
        TraitementViewModel vm = viewModelAvecImport();

        vm.appliquer(vm.releverEtImporter(ID_PASSAGE));

        assertThat(vm.messageProperty().get()).contains("Analyse terminée");
        assertThat(vm.importObservationsProperty().get())
                .contains("L'import des observations a échoué", "aucune donnée renvoyée", "Actualiser");
        assertThat(vm.alerteProperty().get())
                .as("l'alerte parle du calcul, pas de l'import")
                .isEmpty();
        assertThat(vm.enCoursProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#5784 : tant que l'analyse n'est pas terminée, rien n'est importé et la ligne d'import reste vide")
    void un_releve_en_cours_n_importe_rien() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(enCours());
        TraitementViewModel vm = viewModelAvecImport();

        vm.appliquer(vm.releverEtImporter(ID_PASSAGE));

        verifyNoInteractions(importation);
        assertThat(vm.importObservationsProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#5784 : à l'ouverture, un cache « terminée » n'importe rien et dit le geste qui reste")
    void l_ouverture_n_importe_jamais() {
        when(suivi.dernierReleve(ID_PASSAGE))
                .thenReturn(Optional.of(new ReleveTraitement(ID_PASSAGE, "part-1", terminee(), "2026-07-13T09:05:00")));
        when(importation.aDejaSesObservations(ID_PASSAGE)).thenReturn(false);
        TraitementViewModel vm = viewModelAvecImport();

        vm.chargerDernierReleve(ID_PASSAGE);

        verify(importation, never()).importer(ID_PASSAGE, false);
        verify(suivi, never()).relever(ID_PASSAGE);
        assertThat(vm.importObservationsProperty().get())
                .isEqualTo("Cliquez « Actualiser » pour importer les observations.");
    }

    @Test
    @DisplayName("#5784 : à l'ouverture, une nuit terminée et déjà importée le dit")
    void l_ouverture_dit_une_nuit_deja_importee() {
        when(suivi.dernierReleve(ID_PASSAGE))
                .thenReturn(Optional.of(new ReleveTraitement(ID_PASSAGE, "part-1", terminee(), "2026-07-13T09:05:00")));
        when(importation.aDejaSesObservations(ID_PASSAGE)).thenReturn(true);
        TraitementViewModel vm = viewModelAvecImport();

        vm.chargerDernierReleve(ID_PASSAGE);

        assertThat(vm.importObservationsProperty().get()).contains("déjà importées");
    }

    @Test
    @DisplayName("#5784 : un état qui cesse d'être « terminée » efface la ligne d'import")
    void la_ligne_d_import_ne_survit_pas_a_un_autre_etat() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(terminee(), enCours());
        when(importation.importer(ID_PASSAGE, false)).thenReturn(COMPTE_RENDU);
        TraitementViewModel vm = viewModelAvecImport();
        vm.appliquer(vm.releverEtImporter(ID_PASSAGE));

        vm.appliquer(vm.releverEtImporter(ID_PASSAGE));

        assertThat(vm.importObservationsProperty().get()).isEmpty();
    }

    private TraitementViewModel viewModel() {
        return new TraitementViewModel(Optional.of(suivi), LE_13_JUILLET);
    }

    private TraitementViewModel viewModelAvecImport() {
        return new TraitementViewModel(Optional.of(suivi), Optional.of(importation), LE_13_JUILLET);
    }

    private static Traitement terminee() {
        return new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:05:00+00:00", null, null);
    }

    /// Analyse démarrée le matin même du jour figé (aucun retard).
    private static Traitement enCours() {
        return new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T06:00:00+00:00", null, null, null);
    }
}
