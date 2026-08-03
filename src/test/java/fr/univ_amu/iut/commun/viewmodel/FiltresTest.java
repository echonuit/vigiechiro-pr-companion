package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests des **filtres composables** d'une table (#470). Purs : `FilteredList` est une collection
/// observable, elle ne demande pas de toolkit JavaFX.
///
/// La classe n'avait aucun test dédié avant #3095, alors qu'elle porte la conjonction appliquée à
/// toutes les tables de l'application.
class FiltresTest {

    /// Un jeu où chaque critère écarte des lignes **différentes**, pour que retirer l'un ou l'autre ne
    /// donne pas le même résultat : c'est tout ce que `saufLui` doit savoir faire.
    private static ObservableList<String> source() {
        return FXCollections.observableArrayList("a1", "a2", "b1");
    }

    private static Filtres<String> filtresSur(FilteredList<String> affichees) {
        Filtres<String> filtres = new Filtres<>(affichees, () -> {});
        filtres.definir("lettre", ligne -> ligne.startsWith("a"));
        filtres.definir("chiffre", ligne -> ligne.endsWith("1"));
        return filtres;
    }

    @Test
    @DisplayName("La conjonction s'applique bien à la liste affichée")
    void la_conjonction_s_applique() {
        FilteredList<String> affichees = new FilteredList<>(source());

        filtresSur(affichees);

        assertThat(affichees).containsExactly("a1");
    }

    @Test
    @DisplayName("#3095 : saufLui rend les lignes que les AUTRES critères laissent passer")
    void sauf_lui_ignore_le_critere_nomme() {
        // Le domaine d'une puce se calcule sans son propre prédicat. Sinon la puce s'auto-effondre :
        // une fois « a » retenu, le jeu filtré ne contient plus que des « a », le menu n'offrirait donc
        // plus que « a », et on ne pourrait jamais retenir une seconde valeur.
        FilteredList<String> affichees = new FilteredList<>(source());
        Filtres<String> filtres = filtresSur(affichees);

        assertThat(filtres.saufLui("lettre"))
                .as("sans le critère de lettre, il ne reste que le critère de chiffre")
                .containsExactly("a1", "b1");
        assertThat(filtres.saufLui("chiffre"))
                .as("sans le critère de chiffre, il ne reste que le critère de lettre")
                .containsExactly("a1", "a2");
    }

    @Test
    @DisplayName("#3095 : saufLui d'un critère inconnu applique tous les filtres actifs")
    void sauf_lui_d_un_critere_inconnu_n_enleve_rien() {
        // Le cas d'une puce qui n'a pas encore posé son prédicat : rien à retrancher, on voit donc
        // exactement ce que la table affiche.
        FilteredList<String> affichees = new FilteredList<>(source());
        Filtres<String> filtres = filtresSur(affichees);

        assertThat(filtres.saufLui("inconnu")).containsExactly("a1");
    }

    @Test
    @DisplayName("#3095 : sans aucun filtre actif, saufLui rend toute la source")
    void sauf_lui_sans_filtre_rend_tout() {
        FilteredList<String> affichees = new FilteredList<>(source());
        Filtres<String> filtres = new Filtres<>(affichees, () -> {});

        assertThat(filtres.saufLui("lettre")).containsExactly("a1", "a2", "b1");
    }

    @Test
    @DisplayName("#3095 : saufLui lit la source, pas la liste déjà filtrée")
    void sauf_lui_part_de_la_source() {
        // Le piège que cette méthode existe pour éviter : partir de `affichees` rendrait un
        // sous-ensemble de ce qui est déjà filtré, donc jamais une valeur redevenue disponible.
        FilteredList<String> affichees = new FilteredList<>(source());
        Filtres<String> filtres = new Filtres<>(affichees, () -> {});
        filtres.definir("tout_ecarter", ligne -> false);

        assertThat(affichees).as("prérequis : la table n'affiche plus rien").isEmpty();
        assertThat(filtres.saufLui("tout_ecarter"))
                .as("retirer le seul filtre actif redonne bien toute la source")
                .containsExactly("a1", "a2", "b1");
    }

    @Test
    @DisplayName("Retirer un filtre par un prédicat nul le désactive")
    void retirer_un_filtre() {
        FilteredList<String> affichees = new FilteredList<>(source());
        Filtres<String> filtres = filtresSur(affichees);

        filtres.definir("lettre", null);

        assertThat(affichees).containsExactly("a1", "b1");
    }

    @Test
    @DisplayName("Réinitialiser retire tous les filtres")
    void reinitialiser_retire_tout() {
        FilteredList<String> affichees = new FilteredList<>(source());
        Filtres<String> filtres = filtresSur(affichees);

        filtres.reinitialiser();

        assertThat(affichees).containsExactly("a1", "a2", "b1");
        assertThat(filtres.saufLui("lettre")).containsExactly("a1", "a2", "b1");
    }

    @Test
    @DisplayName("Le rappel après application est déclenché à chaque changement")
    void le_rappel_est_declenche() {
        FilteredList<String> affichees = new FilteredList<>(source());
        List<String> appels = new java.util.ArrayList<>();
        Filtres<String> filtres = new Filtres<>(affichees, () -> appels.add("applique"));

        filtres.definir("lettre", ligne -> ligne.startsWith("a"));
        filtres.definir("lettre", null);
        filtres.appliquer();

        assertThat(appels).hasSize(3);
    }
}
