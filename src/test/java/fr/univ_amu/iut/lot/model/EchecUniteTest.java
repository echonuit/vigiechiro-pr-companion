package fr.univ_amu.iut.lot.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La seule question que le compte rendu pose avant de conseiller une reconnexion (#3689, #5598).
///
/// Aucun banc ne la posait directement : les deux surfaces la lisaient, et un `403` du stockage y
/// répondait « oui » parce qu'il était rangé avec les refus de droits.
class EchecUniteTest {

    @Test
    @DisplayName("un refus de droits de l'API se réarme par une reconnexion")
    void refus_de_droits() {
        assertThat(new EchecUnite("a.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION).seRearmeParUneReconnexion())
                .isTrue();
    }

    @Test
    @DisplayName("#5598 : un refus du stockage ne se réarme pas par une reconnexion")
    void refus_du_stockage() {
        assertThat(new EchecUnite("a.zip", "HTTP 403", true, CauseRefus.STOCKAGE).seRearmeParUneReconnexion())
                .as("le jeton de l'API n'intervient pas dans une URL pré-signée")
                .isFalse();
    }

    @Test
    @DisplayName("un contenu refusé ne se réarme pas par une reconnexion")
    void contenu_refuse() {
        assertThat(new EchecUnite("a.zip", "HTTP 422", true, CauseRefus.CONTENU).seRearmeParUneReconnexion())
                .isFalse();
    }

    @Test
    @DisplayName("un échec rejouable n'a rien à réarmer : la reprise le reprend déjà")
    void echec_rejouable() {
        assertThat(EchecUnite.rejouable("a.zip", "coupure").seRearmeParUneReconnexion())
                .isFalse();
    }
}
