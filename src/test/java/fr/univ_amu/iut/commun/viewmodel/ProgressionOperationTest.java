package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Progression;
import java.time.Duration;
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
    @DisplayName("#3483 : un écoulé POSÉ sort l'horloge du calcul, dans ses deux états")
    void ecoule_pose_ignore_l_horloge() {
        // L'horloge a deux états, et le posé doit l'emporter dans les deux.
        //
        // 1. Aucune référence temporelle (`demarrer` jamais appelé) : le chemin horloge rendrait un
        //    écoulé NUL, donc AUCUNE estimation. Si l'estimation apparaît, c'est le posé qui a servi.
        ProgressionOperation sansReference = new ProgressionOperation();
        sansReference.appliquer(new Progression("Décompression : 740 / 3692", 0.20), Duration.ofMillis(2500));
        assertThat(sansReference.messageProperty().get()).isEqualTo("Décompression : 740 / 3692 · ~10 s restant");

        // 2. Référence posée à l'instant : le chemin horloge rendrait un écoulé quasi nul, donc une
        //    estimation quasi nulle. Le message doit être le MÊME qu'au cas 1.
        ProgressionOperation demarree = new ProgressionOperation();
        demarree.demarrer("Préparation de la décompression…");
        demarree.appliquer(new Progression("Décompression : 740 / 3692", 0.20), Duration.ofMillis(2500));
        assertThat(demarree.messageProperty().get())
                .isEqualTo(sansReference.messageProperty().get());
    }

    @Test
    @DisplayName("#3483 : les trois états capturés par CaptureImport annoncent une durée fixée d'avance")
    void les_etats_captures_annoncent_une_duree_fixee() {
        // Ces trois couples (écoulé, fraction) sont ceux que `CaptureImport` pose, DANS SA SÉQUENCE. Les
        // figer ici rend l'écart VISIBLE en test si quelqu'un les retouche, plutôt qu'en revue visuelle
        // six semaines plus tard. Les valeurs tombent juste : 10,0 - 14,0 - 17,3 s, loin d'un demi
        // arrondissable, donc à l'abri d'un basculement d'arrondi.
        //
        // La séquence compte, et ce test l'a montré en échouant : posé tel quel à la suite des deux
        // premiers, le troisième état annonçait « ~10 s » et non « ~17 s ». La fraction est MONOTONE
        // (#814), donc 0,126 après 0,20 ne redescend pas et l'estimation se calcule sur 0,20. Dans la
        // capture, `marquerEnCours()` appelle `demarrer` entre les deux et remet la fraction à zéro -
        // c'est pourquoi l'image est juste. Un outil qui poserait une fraction plus basse SANS repartir
        // afficherait l'estimation de l'état précédent, sans rien pour le signaler.
        ProgressionOperation progression = new ProgressionOperation();
        progression.demarrer("Préparation de la décompression…"); // cf. marquerExtractionEnCours()

        progression.appliquer(new Progression("Décompression", 0.20), Duration.ofMillis(2500));
        assertThat(progression.messageProperty().get()).isEqualTo("Décompression · ~10 s restant");

        progression.appliquer(new Progression("Décompression · 128 Mo", 0.20), Duration.ofMillis(3500));
        assertThat(progression.messageProperty().get()).isEqualTo("Décompression · 128 Mo · ~14 s restant");

        progression.demarrer("Préparation…"); // cf. marquerEnCours()
        progression.appliquer(new Progression("Copie 48/191", 0.126), Duration.ofMillis(2500));
        assertThat(progression.messageProperty().get()).isEqualTo("Copie 48/191 · ~17 s restant");
    }

    @Test
    @DisplayName("#3505 : sans référence temporelle, le chemin horloge n'estime RIEN")
    void chemin_horloge_sans_reference_n_estime_rien() {
        // Le chemin qui lit l'horloge n'était couvert par aucune assertion - justement parce qu'il n'est
        // pas déterministe -, et deux mutants y survivaient. Il porte pourtant une propriété qui, elle,
        // est déterministe : sans `demarrer()`, l'écoulé vaut ZÉRO, donc aucune estimation. C'est ce sur
        // quoi `SuiviProgression.apercu` s'appuie pour montrer un état « avant estimation possible ».
        ProgressionOperation progression = new ProgressionOperation();

        progression.appliquer(new Progression("Compression 10/20", 0.5));

        assertThat(progression.messageProperty().get()).isEqualTo("Compression 10/20");
    }

    @Test
    @DisplayName("#3505 : l'estimation se compte depuis demarrer(), pas depuis l'origine de l'horloge")
    void chemin_horloge_estime_depuis_la_reference_posee() {
        // Premier jet faux, et c'est le test qui avait tort : j'attendais « aucune estimation », parce
        // que la Javadoc dit « trop récent pour estimer ». Le code n'exclut en réalité qu'un écoulé NUL -
        // quelques microsecondes donnent « ~0 s restant », pas l'absence de mention.
        //
        // La propriété qui tient vraiment est celle que la classe annonce : l'écoulé se compte depuis la
        // référence posée, jamais depuis l'origine de `System.nanoTime()`. Un mutant qui ADDITIONNE au
        // lieu de soustraire annonce « ~6460 min restant » là où deux instructions se suivent.
        ProgressionOperation progression = new ProgressionOperation();
        progression.demarrer("Préparation…");

        progression.appliquer(new Progression("Compression 10/20", 0.5));

        assertThat(progression.messageProperty().get())
                .startsWith("Compression 10/20 · ~")
                .doesNotContain("min");
    }

    @Test
    @DisplayName("#3483 : un écoulé négatif ne produit pas d'estimation à rebours")
    void ecoule_negatif_sans_estimation() {
        ProgressionOperation progression = new ProgressionOperation();
        progression.appliquer(new Progression("Décompression", 0.20), Duration.ofSeconds(-5));
        assertThat(progression.messageProperty().get()).isEqualTo("Décompression");
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
