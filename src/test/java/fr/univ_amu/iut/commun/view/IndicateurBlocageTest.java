package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests du composant partagé [IndicateurBlocage] (#789). [ApplicationExtension] initialise le toolkit
/// JavaFX (construction des nœuds et du Tooltip) ; aucune scène affichée. On vérifie le **contrat
/// d'enrobage** : `enrober` renvoie une enveloppe qui entoure exactement le contrôle (celui-ci reste
/// désactivable indépendamment, l'enveloppe reçoit le survol pour le tooltip).
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class IndicateurBlocageTest {

    @Test
    @DisplayName("#2115 : l'explication d'un blocage n'est atteignable qu'au survol, pas au clavier")
    void l_explication_est_hors_de_portee_du_clavier() {
        // Constat mesuré à l'exécution, pas déduit du code : `IndicateurBlocage` explique pourquoi un
        // contrôle est grisé (#789), et c'est le SEUL endroit où le motif est dit. Or il vit dans un
        // Tooltip, que JavaFX ne déclenche qu'au survol.
        //
        // Ce test ne corrige rien : il FIXE l'état actuel pour qu'une correction future le fasse
        // rougir, et il documente les trois faits qui, ensemble, mettent l'explication hors de portée
        // de qui n'utilise pas la souris.
        Button bouton = new Button("Déposer");
        bouton.setDisable(true);
        StackPane enveloppe = IndicateurBlocage.enrober(bouton, "Préparez d'abord le dépôt.");
        new Scene(enveloppe);

        assertThat(enveloppe.isFocusTraversable())
                .as("l'enveloppe qui PORTE l'explication n'est pas atteignable au Tab")
                .isFalse();
        assertThat(bouton.isDisabled())
                .as("et le contrôle qu'elle entoure est désactivé, donc hors de l'ordre de tabulation")
                .isTrue();
        assertThat(enveloppe.getAccessibleText())
                .as("aucun texte accessible ne relaie le motif pour un lecteur d'écran")
                .isNull();
    }

    @Test
    @DisplayName("enrober (texte fixe) renvoie une enveloppe StackPane entourant le contrôle")
    void enrober_texte_fixe_entoure_le_controle() {
        Button bouton = new Button("Supprimer");
        StackPane enveloppe = IndicateurBlocage.enrober(bouton, "Suppression impossible : …");
        assertThat(enveloppe.getChildren()).containsExactly(bouton);
    }

    @Test
    @DisplayName("enrober (texte observable) renvoie une enveloppe StackPane entourant le contrôle")
    void enrober_texte_observable_entoure_le_controle() {
        Button bouton = new Button("Supprimer");
        StackPane enveloppe = IndicateurBlocage.enrober(bouton, new SimpleStringProperty("motif de blocage"));
        assertThat(enveloppe.getChildren()).containsExactly(bouton);
    }

    @Test
    @DisplayName("expliquer sur une enveloppe existante n'altère pas ses enfants")
    void expliquer_n_altere_pas_l_enveloppe() {
        Button bouton = new Button("Supprimer");
        StackPane enveloppe = new StackPane(bouton);
        IndicateurBlocage.expliquer(enveloppe, new SimpleStringProperty("motif"));
        assertThat(enveloppe.getChildren()).containsExactly(bouton);
    }
}
