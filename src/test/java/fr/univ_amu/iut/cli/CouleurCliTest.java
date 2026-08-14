package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

/// Le mode couleur de la CLI (#3738).
///
/// ⚠️ La règle est éprouvée sur des **entrées fournies** et non sur la console du processus de test :
/// manipuler la console et l'environnement d'une JVM en cours n'est pas portable, et c'est précisément
/// la non-portabilité qui a créé ce défaut.
class CouleurCliTest {

    @Test
    @DisplayName("#3738 : de la couleur pour un humain devant un terminal")
    void couleur_quand_un_humain_regarde() {
        assertThat(CouleurCli.choisie(true, null)).isEqualTo(Ansi.ON);
    }

    @Test
    @DisplayName("#3738 : jamais de couleur dans un tuyau, un fichier ou un journal de CI")
    void pas_de_couleur_quand_la_sortie_est_redirigee() {
        // C'est le cas du défaut : sous Windows, l'aide sortait colorisée dans un journal de CI, et un
        // utilisateur dont la console ne rend pas l'ANSI y lit « ←[1mvigiechiro ».
        assertThat(CouleurCli.choisie(false, null)).isEqualTo(Ansi.OFF);
    }

    @Test
    @DisplayName("#3738 : NO_COLOR donne le dernier mot à l'utilisateur, même devant un terminal")
    void no_color_l_emporte() {
        assertThat(CouleurCli.choisie(true, "1")).isEqualTo(Ansi.OFF);
        assertThat(CouleurCli.choisie(true, "0"))
                .as("la convention veut que la variable compte dès qu'elle est présente et non vide,"
                        + " quelle que soit sa valeur : la respecter à moitié serait pire que l'ignorer")
                .isEqualTo(Ansi.OFF);
    }

    @Test
    @DisplayName("#3738 : une variable posée mais vide ne dit rien, et ne décide donc rien")
    void no_color_vide_ne_decide_rien() {
        assertThat(CouleurCli.choisie(true, "")).isEqualTo(Ansi.ON);
    }
}
