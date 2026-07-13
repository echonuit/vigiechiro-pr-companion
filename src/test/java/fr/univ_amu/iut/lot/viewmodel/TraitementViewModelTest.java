package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Zone « Traitement Vigie-Chiro » de M-Lot (#1263) : ce que l'écran dit de l'analyse serveur, et ce qu'il
/// en déduit — notamment **si la relance doit être interdite**. Suivi mocké, aucun réseau.
class TraitementViewModelTest {

    private static final Long ID_PASSAGE = 42L;
    private static final Horloge LE_13_JUILLET = Horloge.figeeAu(LocalDate.of(2026, 7, 13));

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);

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

    @Test
    @DisplayName("analyse TERMINÉE : la relance est bloquée (elle effacerait les observations du serveur)")
    void terminee_bloque_la_relance() {
        TraitementViewModel vm = viewModel();

        vm.appliquer(new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:05:00+00:00", null, null));

        assertThat(vm.messageProperty().get()).contains("Analyse terminée", "prêtes à être importées");
        assertThat(vm.relanceBloqueeProperty().get())
                .as("le serveur, lui, accepterait de recalculer — et détruirait tout (#1244)")
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

    private TraitementViewModel viewModel() {
        return new TraitementViewModel(Optional.of(suivi), LE_13_JUILLET);
    }

    /// Analyse démarrée le matin même du jour figé (aucun retard).
    private static Traitement enCours() {
        return new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T06:00:00+00:00", null, null, null);
    }
}
