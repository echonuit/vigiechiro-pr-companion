package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.beans.property.SimpleStringProperty;
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
@ExtendWith(ApplicationExtension.class)
class IndicateurBlocageTest {

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
