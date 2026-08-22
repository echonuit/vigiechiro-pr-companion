package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le garde des DEUX signaux de tournage.
///
/// Il existe parce que le premier n'a pas suffi. `Seance.filmee()` ne reconnaissait que
/// `recette.reperes`, posée par le seul banc bash, et le banc en Java pur pose `recette.film` : neuf
/// clips perceptifs ont été tournés sous Windows sans une seule respiration, chaque geste tenant en
/// une ou deux images.
///
/// ⚠️ Rien n'avait rougi, et rien ne pouvait rougir : les clips existaient, les tests passaient, et
/// l'index les comptait tous les neuf. Le seul endroit où ce défaut se voyait, c'est l'oeil de qui
/// regarde. C'est exactement ce qu'un garde doit reprendre à l'oeil.
class SeanceTest {

    private String reperes;
    private String film;

    /// Les deux propriétés sont RELEVÉES puis effacées, et rendues après coup.
    ///
    /// ⚠️ Sans cela, ce fichier serait faux précisément pendant un tournage : `recette.film` y est
    /// posée pour de bon, et le cas « aucune propriété » n'aurait alors jamais l'état qu'il annonce.
    /// Un garde qui ment quand le dispositif qu'il garde fonctionne ne garde rien.
    @BeforeEach
    void relever() {
        reperes = System.getProperty(JournalDesReperes.PROPRIETE);
        film = System.getProperty("recette.film");
        System.clearProperty(JournalDesReperes.PROPRIETE);
        System.clearProperty("recette.film");
    }

    @AfterEach
    void rendre() {
        rendre(JournalDesReperes.PROPRIETE, reperes);
        rendre("recette.film", film);
    }

    private static void rendre(String cle, String valeur) {
        if (valeur == null) {
            System.clearProperty(cle);
        } else {
            System.setProperty(cle, valeur);
        }
    }

    @Test
    @DisplayName("hors tournage, rien ne dort")
    void sans_aucune_propriete_la_seance_n_est_pas_filmee() {
        assertThat(Seance.filmee()).isFalse();
    }

    @Test
    @DisplayName("le banc bash : le journal des repères désigné")
    void avec_le_journal_des_reperes_la_seance_est_filmee() {
        System.setProperty(JournalDesReperes.PROPRIETE, "target/recette-filmee/reperes.tsv");

        assertThat(Seance.filmee()).isTrue();
    }

    /// ⚠️ Le cas qui manquait. Neutraliser la reconnaissance de `recette.film` fait rougir celui-ci
    /// et lui seul.
    @Test
    @DisplayName("le banc Java : la présence de recette.film suffit")
    void avec_la_propriete_du_banc_java_la_seance_est_filmee() {
        System.setProperty("recette.film", "");

        assertThat(Seance.filmee()).isTrue();
    }

    /// Un journal désigné par une chaîne blanche ne désigne rien : c'est la forme que prend la
    /// propriété INERTE du build ordinaire, où `${recette.reperes}` se résout à vide.
    @Test
    @DisplayName("un journal blanc ne vaut pas un tournage")
    void un_journal_blanc_ne_filme_pas() {
        System.setProperty(JournalDesReperes.PROPRIETE, "   ");

        assertThat(Seance.filmee()).isFalse();
    }
}
