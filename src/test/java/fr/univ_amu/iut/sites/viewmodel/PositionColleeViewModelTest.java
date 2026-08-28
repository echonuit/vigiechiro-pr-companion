package fr.univ_amu.iut.sites.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CarroyageNational;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.PropositionCarre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le versant « partir d'un lieu plutôt que d'un numéro » de la modale de site (#4577).
@DisplayName("Situer une position dans la modale de site (#4577)")
class PositionColleeViewModelTest {

    private final PositionColleeViewModel vm =
            new PositionColleeViewModel(new PropositionCarre(CarroyageNational.embarque()));

    @Test
    @DisplayName("situer une position intérieure propose son carré et le dit")
    void situer_propose_le_carre() {
        vm.texte().set("44.44674980384396, 6.298116860416506");

        vm.situer("");

        assertThat(vm.numeroPropose().get()).isEqualTo("040110");
        assertThat(vm.retour().get().texte()).contains("040110");
        assertThat(vm.retour().get().severite()).isEqualTo(Severite.SUCCES);
    }

    @Test
    @DisplayName("remplacer un numéro tapé le DIT, et nomme celui qu'on a perdu")
    void remplacement_nomme_ce_qu_il_ecrase() {
        vm.texte().set("44.44674980384396, 6.298116860416506");

        vm.situer("640380");

        assertThat(vm.numeroPropose().get()).isEqualTo("040110");
        assertThat(vm.retour().get().texte())
                .as("un numéro tapé à la main est une intention : l'écraser sans un mot l'efface")
                .contains("640380")
                .contains("remplacé");
    }

    @Test
    @DisplayName("quand la position confirme le numéro déjà saisi, elle le dit au lieu de se taire")
    void confirmation_se_dit() {
        vm.texte().set("44.44674980384396, 6.298116860416506");

        vm.situer("040110");

        assertThat(vm.retour().get().texte()).contains("confirme");
        assertThat(vm.retour().get().severite()).isEqualTo(Severite.SUCCES);
    }

    @Test
    @DisplayName("sur une frontière : rien à déposer, un avertissement qui nomme les candidats")
    void frontiere_ne_depose_rien() {
        vm.texte().set("44.444990, 6.306335");

        vm.situer("");

        assertThat(vm.numeroPropose().get()).isEmpty();
        assertThat(vm.retour().get().texte()).contains("040110").contains("040111");
        assertThat(vm.retour().get().severite()).isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("hors métropole : rien à déposer, et le motif le dit")
    void hors_metropole_ne_depose_rien() {
        vm.texte().set("45.0, -20.0");

        vm.situer("");

        assertThat(vm.numeroPropose().get()).isEmpty();
        assertThat(vm.retour().get().texte()).contains("métropolitaine");
    }

    @Test
    @DisplayName("un texte illisible : rien à déposer, et le motif dit quoi coller")
    void texte_illisible_ne_depose_rien() {
        vm.texte().set("mon jardin");

        vm.situer("");

        assertThat(vm.numeroPropose().get()).isEmpty();
        assertThat(vm.retour().get().texte()).contains("deux nombres");
    }

    @Test
    @DisplayName("changer le texte oublie le verdict : il ne juge plus ce qui est à l'écran")
    void changer_le_texte_oublie_le_verdict() {
        vm.texte().set("44.44674980384396, 6.298116860416506");
        vm.situer("");

        vm.texte().set("44.44674980384396, 6.29");

        assertThat(vm.numeroPropose().get()).isEmpty();
        assertThat(vm.retour().get()).isEqualTo(RetourOperation.AUCUN);
    }
}
