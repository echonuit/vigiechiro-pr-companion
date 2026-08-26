package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

/// Le mode couleur de la CLI (#3738).
///
/// La règle est éprouvée sur des **entrées fournies** et non sur la console du processus de test :
/// manipuler la console et l'environnement d'une JVM en cours n'est pas portable, et c'est précisément
/// la non-portabilité qui a créé ce défaut.
class CouleurCliTest {

    @Test
    @DisplayName("#3738 : de la couleur pour un humain devant un terminal")
    void couleur_quand_un_humain_regarde() {
        assertThat(CouleurCli.choisie(true, null, null)).isEqualTo(Ansi.ON);
    }

    @Test
    @DisplayName("#3738 : jamais de couleur dans un tuyau, un fichier ou un journal de CI")
    void pas_de_couleur_quand_la_sortie_est_redirigee() {
        // C'est le cas du défaut : sous Windows, l'aide sortait colorisée dans un journal de CI, et un
        // utilisateur dont la console ne rend pas l'ANSI y lit « ←[1mvigiechiro ».
        assertThat(CouleurCli.choisie(false, null, null)).isEqualTo(Ansi.OFF);
    }

    @Test
    @DisplayName("#3738 : NO_COLOR donne le dernier mot à l'utilisateur, même devant un terminal")
    void no_color_l_emporte() {
        assertThat(CouleurCli.choisie(true, "1", null)).isEqualTo(Ansi.OFF);
        assertThat(CouleurCli.choisie(true, "0", null))
                .as("la convention veut que la variable compte dès qu'elle est présente et non vide,"
                        + " quelle que soit sa valeur : la respecter à moitié serait pire que l'ignorer")
                .isEqualTo(Ansi.OFF);
    }

    @Test
    @DisplayName("#3738 : une variable posée mais vide ne dit rien, et ne décide donc rien")
    void no_color_vide_ne_decide_rien() {
        assertThat(CouleurCli.choisie(true, "", null)).isEqualTo(Ansi.ON);
    }

    // Le dernier mot n'etait donne que dans UN sens : `NO_COLOR` eteignait, rien n'allumait. Trois
    // situations ordinaires en souffraient - un pager qui rend l'ANSI, un journal de CI qui
    // l'interprete, un enrobage `script`/`unbuffer` - et dans les trois l'utilisateur VEUT la couleur,
    // la console SAIT l'afficher, et le produit refusait (#3796).

    @Test
    @DisplayName("#3796 : FORCE_COLOR allume la couleur même sans console")
    void force_color_allume_sans_console() {
        assertThat(CouleurCli.choisie(false, null, "1")).isEqualTo(Ansi.ON);
    }

    @Test
    @DisplayName("#3796 : NO_COLOR l'emporte sur FORCE_COLOR")
    void le_refus_prime_sur_la_demande() {
        // Un refus explicite prime sur une demande explicite. Se tromper dans ce sens affiche du texte
        // nu ; se tromper dans l'autre crache des séquences d'échappement chez quelqu'un qui a demandé
        // qu'on ne le fasse pas.
        assertThat(CouleurCli.choisie(true, "1", "1")).isEqualTo(Ansi.OFF);
        assertThat(CouleurCli.choisie(false, "1", "1")).isEqualTo(Ansi.OFF);
    }

    @Test
    @DisplayName("#3796 : une FORCE_COLOR posée mais vide ne décide rien")
    void force_color_vide_ne_decide_rien() {
        // Même lecture que `NO_COLOR` : présente ET non vide. Deux règles différentes pour deux
        // variables voisines seraient un piège de plus à retenir.
        assertThat(CouleurCli.choisie(false, null, "")).isEqualTo(Ansi.OFF);
        assertThat(CouleurCli.choisie(true, null, "")).isEqualTo(Ansi.ON);
    }
}
