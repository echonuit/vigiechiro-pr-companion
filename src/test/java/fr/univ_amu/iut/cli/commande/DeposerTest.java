package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Compte rendu du dépôt de `deposer` (#617). `rendreDepot` est une fonction pure : passage, date de
/// dépôt, nombre de séquences et volume lisible ; le volume peut être `null` (non calculé).
class DeposerTest {

    @Test
    @DisplayName("Dépôt avec volume : passage, date, nombre de séquences et volume lisible")
    void rendre_depot_avec_volume() {
        String texte = Deposer.rendreDepot(12L, 128, 536_870_912L, "2026-06-20T10:00:00");

        assertThat(texte)
                .contains("Passage #12 déposé le 2026-06-20T10:00:00")
                .contains("128 séquence(s)")
                .contains("512 Mo");
    }

    @Test
    @DisplayName("Volume non calculé (null) : mention « volume inconnu »")
    void rendre_depot_volume_inconnu() {
        String texte = Deposer.rendreDepot(3L, 0, null, "2026-06-20T10:00:00");

        assertThat(texte).contains("Passage #3").contains("0 séquence(s)").contains("volume inconnu");
    }
}
