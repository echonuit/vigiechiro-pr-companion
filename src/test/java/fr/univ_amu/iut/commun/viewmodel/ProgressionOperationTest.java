package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Progression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Suivi de la progression d'une opération longue (socle partagé import #33/#146 + génération des
/// archives #769) : estimation du temps restant (calcul **pur**, temps écoulé en paramètre) et holder
/// observable (fraction + libellé).
class ProgressionOperationTest {

    @Test
    @DisplayName("ETA : extrapolation linéaire du temps restant à partir de l'écoulé")
    void eta_extrapolation_lineaire() {
        // À 25 % après 10 s → il reste ~30 s (10 × 0,75 / 0,25).
        assertThat(ProgressionOperation.avecTempsRestant("Compression 5/20", 0.25, 10_000_000_000L))
                .isEqualTo("Compression 5/20 · ~30 s restant");
    }

    @Test
    @DisplayName("ETA : formatage en minutes au-delà de 60 s")
    void eta_en_minutes() {
        // À 10 % après 60 s → il reste ~540 s = ~9 min.
        assertThat(ProgressionOperation.avecTempsRestant("Compression 2/20", 0.10, 60_000_000_000L))
                .contains("~9 min");
    }

    @Test
    @DisplayName("ETA : absente tant que l'avancement est nul, terminé ou trop récent pour estimer")
    void eta_absente_aux_bornes() {
        assertThat(ProgressionOperation.avecTempsRestant("Compression 0/20", 0.0, 5_000_000_000L))
                .isEqualTo("Compression 0/20");
        assertThat(ProgressionOperation.avecTempsRestant("Compression 20/20", 1.0, 5_000_000_000L))
                .isEqualTo("Compression 20/20");
        assertThat(ProgressionOperation.avecTempsRestant("Compression 5/20", 0.25, 0L))
                .isEqualTo("Compression 5/20");
    }

    @Test
    @DisplayName("formaterDuree : ~X s sous la minute, ~X min [Y s] au-delà")
    void formate_la_duree() {
        assertThat(ProgressionOperation.formaterDuree(45)).isEqualTo("~45 s");
        assertThat(ProgressionOperation.formaterDuree(60)).isEqualTo("~1 min");
        assertThat(ProgressionOperation.formaterDuree(90)).isEqualTo("~1 min 30 s");
        assertThat(ProgressionOperation.formaterDuree(120)).isEqualTo("~2 min");
        assertThat(ProgressionOperation.formaterDuree(125)).isEqualTo("~2 min 5 s");
    }

    @Test
    @DisplayName("Le holder publie fraction et libellé après demarrer/appliquer, remis à zéro par reinitialiser")
    void holder_publie_puis_reinitialise() {
        ProgressionOperation progression = new ProgressionOperation();
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
            "#814 : travail parallèle, la fraction reste monotone (un point en retard ne fait pas reculer la barre)")
    void fraction_monotone_malgre_points_desordonnes() {
        ProgressionOperation progression = new ProgressionOperation();
        progression.demarrer("Préparation…");

        progression.appliquer(new Progression("Compression 12/20", 0.6));
        assertThat(progression.fractionProperty().get()).isEqualTo(0.6);

        // Point d'une autre unité, arrivé APRÈS mais correspondant à un avancement inférieur : la barre
        // ne doit pas reculer (elle reste à 0.6), même si le libellé suit le dernier point reçu.
        progression.appliquer(new Progression("Compression 9/20", 0.45));
        assertThat(progression.fractionProperty().get()).isEqualTo(0.6);
        assertThat(progression.messageProperty().get()).contains("Compression 9/20");
    }

    @Test
    @DisplayName("demarrer() repose la fraction à zéro : deux opérations successives ne se cumulent pas")
    void demarrer_reinitialise_entre_deux_operations() {
        ProgressionOperation progression = new ProgressionOperation();
        progression.demarrer("Import…");
        progression.appliquer(new Progression("Transformation 20/20", 0.95));

        // Nouvelle opération (p. ex. décompression après un import) : la monotonie ne retient pas
        // l'avancement de la précédente.
        progression.demarrer("Décompression…");
        assertThat(progression.fractionProperty().get()).isEqualTo(0.0);
        progression.appliquer(new Progression("Décompression 1/10", 0.1));
        assertThat(progression.fractionProperty().get()).isEqualTo(0.1);
    }
}
