package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La règle de résolution d'un chemin de séquence, éprouvée là où elle vit (#4666).
class SessionDEnregistrementTest {

    private static final String RACINE = "/workspace/Car130711-2026-Pass1-Z41";

    private static SessionDEnregistrement session() {
        return new SessionDEnregistrement(1L, RACINE, 0L, 0L, 42L);
    }

    /// **Ne rougit sur aucune mutation de la règle**, et gardé quand même : il pin le contrat de
    /// `Path.resolve`, et rougirait sur une résolution écrite par concaténation.
    @Test
    @DisplayName("un chemin absolu est rendu tel quel : la racine ne s'y ajoute pas")
    void un_chemin_absolu_ne_bouge_pas() {
        Path absolu = Path.of("/ailleurs/seq_000.wav");

        assertThat(session().resoudre(absolu)).isEqualTo(absolu);
    }

    @Test
    @DisplayName("un chemin relatif est résolu contre la racine de sa session")
    void un_chemin_relatif_se_resout() {
        assertThat(session().resoudre(Path.of("sequences/seq_000.wav")))
                .isEqualTo(Path.of(RACINE, "sequences", "seq_000.wav"));
    }

    @Test
    @DisplayName("résoudre deux fois donne le même résultat : le second passage voit un absolu")
    void resoudre_est_idempotent() {
        Path unePasse = session().resoudre(Path.of("seq_000.wav"));

        assertThat(session().resoudre(unePasse))
                .as("un appelant qui résout un chemin déjà résolu ne doit pas le préfixer deux fois")
                .isEqualTo(unePasse);
    }
}
