package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests unitaires de [ContextePassage] : l'identité compacte pour la zone gauche de la barre de statut
/// (#1016), avec ou sans numéro de passage connu.
class ContextePassageTest {

    @Test
    @DisplayName("identiteStatut : « Carré X · Point · N° Z » quand le numéro est connu")
    void identite_avec_numero() {
        ContextePassage contexte = new ContextePassage(7L, 3, new ContexteSite("640380", "A1", "Étang"));
        assertThat(contexte.identiteStatut()).isEqualTo("Carré 640380 · A1 · N° 3");
    }

    @Test
    @DisplayName("identiteStatut : le segment « · N° X » est omis quand le numéro est inconnu (0)")
    void identite_sans_numero() {
        ContextePassage contexte = new ContextePassage(7L, 0, new ContexteSite("640380", "A1", null));
        assertThat(contexte.identiteStatut()).isEqualTo("Carré 640380 · A1");
    }
}
