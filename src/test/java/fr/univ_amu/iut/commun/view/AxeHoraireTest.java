package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import javafx.scene.chart.NumberAxis;
import javafx.util.StringConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Vérifie le socle d'axe horaire ([AxeHoraire]), partagé par la courbe climatique du diagnostic et la
/// courbe d'activité : deux écrans qui placent leurs points en **minutes depuis une origine** et
/// attendent une graduation **en heures**.
///
/// Ce test existe parce que ni l'un ni l'autre de ces écrans n'assert ses étiquettes d'axe : une
/// régression du format (des minutes brutes réapparaissant à la place des heures) ne ferait rougir
/// aucun de leurs tests.
///
/// [ApplicationExtension] : construire un `NumberAxis` démarre le toolkit JavaFX (un axe est un
/// `Node`), sans quoi la classe ne se charge même pas.
@ExtendWith(ApplicationExtension.class)
class AxeHoraireTest {

    @Test
    void l_abscisse_zero_porte_l_heure_d_origine() {
        StringConverter<Number> etiquettes = AxeHoraire.etiquettesHeure(LocalTime.of(18, 0));

        assertThat(etiquettes.toString(0)).isEqualTo("18");
        assertThat(etiquettes.toString(60)).isEqualTo("19");
    }

    @Test
    void l_etiquette_repasse_par_minuit_sans_rien_de_special() {
        // 18 h + 6 h = minuit, puis 18 h + 8 h = 2 h du matin : une nuit franchit minuit, l'axe doit
        // suivre sans discontinuité.
        StringConverter<Number> etiquettes = AxeHoraire.etiquettesHeure(LocalTime.of(18, 0));

        assertThat(etiquettes.toString(360)).isEqualTo("00");
        assertThat(etiquettes.toString(480)).isEqualTo("02");
    }

    @Test
    void graduer_borne_l_axe_et_coupe_l_auto_cadrage() {
        NumberAxis axe = new NumberAxis();

        AxeHoraire.graduerEnHeures(axe, LocalTime.of(18, 0), 840, 60);

        assertThat(axe.isAutoRanging())
                .as("les bornes viennent de l'écran, qui seul sait ce qu'il doit montrer")
                .isFalse();
        assertThat(axe.getLowerBound()).isZero();
        assertThat(axe.getUpperBound()).isEqualTo(840);
        assertThat(axe.getTickUnit()).isEqualTo(60);
        assertThat(axe.getTickLabelFormatter().toString(840))
                .as("la borne haute de la fenêtre nocturne retombe sur 8 h")
                .isEqualTo("08");
    }

    @Test
    void une_saisie_d_etiquette_ne_fait_pas_tomber_l_ecran() {
        // Une étiquette d'axe ne se saisit pas : fromString n'est jamais appelé, mais un appel inattendu
        // de JavaFX doit rendre une valeur plutôt que lever.
        assertThat(AxeHoraire.etiquettesHeure(LocalTime.of(18, 0)).fromString("peu importe"))
                .isEqualTo(0);
    }
}
