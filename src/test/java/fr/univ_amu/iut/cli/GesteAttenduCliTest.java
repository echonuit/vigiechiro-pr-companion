package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.GesteAttendu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// #2635 : un refus dit **ce qui manque**, chaque surface dit **quoi faire**.
///
/// Le test vit dans le paquet `cli` parce que c'est le seul endroit d'où l'on voit les deux formateurs -
/// celui de la ligne de commande y est confiné. Exposer le second uniquement pour qu'un test les compare
/// aurait élargi la production au service du test.
///
/// Ce qui est vérifié n'est pas la formulation, qui changera, mais le fait que le modèle n'en porte
/// aucune : le même refus donne **deux gestes** et **un seul** énoncé.
class GesteAttenduCliTest {

    @Test
    @DisplayName("Le même refus donne un chemin de menu dans l'application, une commande au terminal")
    void deux_surfaces_deux_gestes() {
        RegleMetierException refus = new RegleMetierException(
                "Les observations n'ont pas pu être lues : l'application n'est pas connectée à Vigie-Chiro.",
                new Besoin.Connexion());

        String pourLApplication = GesteAttendu.message(refus);
        String pourLeTerminal = GesteAttenduCli.message(refus);

        assertThat(pourLApplication).contains("menu ☰").doesNotContain("vigiechiro connexion");
        assertThat(pourLeTerminal).contains("vigiechiro connexion").doesNotContain("menu ☰ > Se connecter");
        // L'énoncé, lui, est le même des deux côtés : c'est le fait, il n'appartient à aucune surface.
        assertThat(pourLApplication).contains("n'est pas connectée à Vigie-Chiro");
        assertThat(pourLeTerminal).contains("n'est pas connectée à Vigie-Chiro");
    }

    @Test
    @DisplayName("Une fonctionnalité éteinte se règle dans l'application, et le terminal le dit franchement")
    void fonctionnalite_se_regle_dans_l_application() {
        RegleMetierException refus = new RegleMetierException(
                "Régénérer les séquences est impossible : la fonctionnalité « Importation » est désactivée.",
                new Besoin.Fonctionnalite("Importation"));

        assertThat(GesteAttenduCli.message(refus))
                .as("mieux vaut renvoyer à l'endroit qui existe que d'inventer une commande")
                .contains("dans l'application")
                .contains("ne se règlent pas en ligne de commande");
    }

    @Test
    @DisplayName("Un refus sans besoin est rendu tel quel : on n'invente pas un geste")
    void sans_besoin_rien_n_est_ajoute() {
        RegleMetierException refus = new RegleMetierException("Ce passage est déjà déposé.");

        assertThat(GesteAttendu.message(refus)).isEqualTo("Ce passage est déjà déposé.");
        assertThat(GesteAttenduCli.message(refus)).isEqualTo("Ce passage est déjà déposé.");
    }

    @Test
    @DisplayName("Une exception quelconque traverse sans dommage")
    void exception_ordinaire_traverse() {
        assertThat(GesteAttendu.message(new IllegalStateException("état incohérent")))
                .isEqualTo("état incohérent");
        assertThat(GesteAttenduCli.message(new IllegalStateException("état incohérent")))
                .isEqualTo("état incohérent");
    }
}
