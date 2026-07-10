package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Progression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Suivi de la progression de la génération des archives de dépôt (#769) : estimation du temps restant
/// (calcul **pur**, temps écoulé en paramètre) et holder observable (fraction + libellé).
class ProgressionLotTest {

    @Test
    @DisplayName("ETA : extrapolation linéaire du temps restant à partir de l'écoulé")
    void eta_extrapolation_lineaire() {
        // À 25 % après 10 s → il reste ~30 s (10 × 0,75 / 0,25).
        assertThat(ProgressionLot.avecTempsRestant("Compression 5/20", 0.25, 10_000_000_000L))
                .isEqualTo("Compression 5/20 · ~30 s restant");
    }

    @Test
    @DisplayName("ETA : formatage en minutes au-delà de 60 s")
    void eta_en_minutes() {
        // À 10 % après 60 s → il reste ~540 s = ~9 min.
        assertThat(ProgressionLot.avecTempsRestant("Compression 2/20", 0.10, 60_000_000_000L))
                .contains("~9 min");
    }

    @Test
    @DisplayName("ETA : absente tant que l'avancement est nul, terminé ou trop récent pour estimer")
    void eta_absente_aux_bornes() {
        assertThat(ProgressionLot.avecTempsRestant("Compression 0/20", 0.0, 5_000_000_000L))
                .isEqualTo("Compression 0/20");
        assertThat(ProgressionLot.avecTempsRestant("Compression 20/20", 1.0, 5_000_000_000L))
                .isEqualTo("Compression 20/20");
        assertThat(ProgressionLot.avecTempsRestant("Compression 5/20", 0.25, 0L))
                .isEqualTo("Compression 5/20");
    }

    @Test
    @DisplayName("formaterDuree : ~X s sous la minute, ~X min [Y s] au-delà")
    void formate_la_duree() {
        assertThat(ProgressionLot.formaterDuree(45)).isEqualTo("~45 s");
        assertThat(ProgressionLot.formaterDuree(120)).isEqualTo("~2 min");
        assertThat(ProgressionLot.formaterDuree(125)).isEqualTo("~2 min 5 s");
    }

    @Test
    @DisplayName("Le holder publie fraction et libellé après demarrer/appliquer, remis à zéro par reinitialiser")
    void holder_publie_puis_reinitialise() {
        ProgressionLot progression = new ProgressionLot();
        progression.demarrer("Préparation…");
        assertThat(progression.fractionProperty().get()).isEqualTo(0.0);
        assertThat(progression.messageProperty().get()).isEqualTo("Préparation…");

        progression.appliquer(new Progression("Compression 10/20", 0.5));
        assertThat(progression.fractionProperty().get()).isEqualTo(0.5);
        assertThat(progression.messageProperty().get()).contains("Compression 10/20");

        progression.reinitialiser();
        assertThat(progression.fractionProperty().get()).isEqualTo(0.0);
        assertThat(progression.messageProperty().get()).isEmpty();
    }

    @Test
    @DisplayName(
            "#814 : compression parallèle — la fraction reste monotone (un point en retard ne fait pas reculer la barre)")
    void fraction_monotone_malgre_points_desordonnes() {
        ProgressionLot progression = new ProgressionLot();
        progression.demarrer("Préparation…");

        progression.appliquer(new Progression("Compression 12/20", 0.6));
        assertThat(progression.fractionProperty().get()).isEqualTo(0.6);

        // Point d'une autre archive, arrivé APRÈS mais correspondant à un avancement inférieur : la barre
        // ne doit pas reculer (elle reste à 0.6), même si le libellé suit le dernier fichier compressé.
        progression.appliquer(new Progression("Compression 9/20", 0.45));
        assertThat(progression.fractionProperty().get()).isEqualTo(0.6);
        assertThat(progression.messageProperty().get()).contains("Compression 9/20");
    }
}
