package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le **cascadage** des listes de valeurs (#3095) : ce qu'une puce offre dépend des lignes que les
/// **autres** critères laissent passer, et se recalcule à l'ouverture de son menu.
///
/// Le domaine était auparavant photographié à la création de la puce et ne bougeait plus : elle
/// proposait des valeurs devenues impossibles, et ne faisait pas réapparaître celles qui redevenaient
/// disponibles.
@ExtendWith(ApplicationExtension.class)
class CritereListeCascadeTest {

    /// Les valeurs offertes, que le test fait varier entre deux ouvertures du menu comme le ferait un
    /// autre critère qui se resserre.
    private final AtomicReference<List<String>> offertes =
            new AtomicReference<>(List.of("Aix", "Venelles", "Gardanne"));

    private CritereFiltre<String> critere() {
        return CritereListe.multiple(
                "lieu", "Lieu", "Choisir un lieu", offertes::get, (Function<String, String>) ligne -> ligne);
    }

    /// Simule l'ouverture du menu, moment où le domaine se recalcule.
    private static void ouvrirLeMenu(Node editeur) {
        MenuButton bouton = (MenuButton) editeur;
        assertThat(bouton.getOnShowing())
                .as("le domaine doit se recalculer à l'ouverture du menu : aucun gestionnaire posé")
                .isNotNull();
        bouton.getOnShowing().handle(new Event(Event.ANY));
    }

    private static List<String> entrees(Node editeur) {
        return ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(item -> ((CheckMenuItem) item).getText())
                        .toList();
    }

    private static List<String> cochees(Node editeur) {
        return ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(CheckMenuItem.class::cast)
                        .filter(CheckMenuItem::isSelected)
                        .map(CheckMenuItem::getText)
                        .toList();
    }

    private static void cocher(Node editeur, String valeur) {
        ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(CheckMenuItem.class::cast)
                        .filter(item -> valeur.equals(item.getText()))
                        .findFirst()
                        .orElseThrow()
                        .setSelected(true);
    }

    @Test
    @DisplayName("#3095 : le domaine se recalcule à l'ouverture, une valeur devenue impossible disparaît")
    void le_domaine_se_recalcule_a_l_ouverture() {
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(ignore -> {});
        assertThat(entrees(editeur)).containsExactly("Aix", "Venelles", "Gardanne");

        // Un autre critère se resserre : Gardanne n'a plus de ligne.
        offertes.set(List.of("Aix", "Venelles"));
        ouvrirLeMenu(editeur);

        assertThat(entrees(editeur))
                .as("proposer une valeur qui ne ramène rien fait perdre du temps à qui la coche")
                .containsExactly("Aix", "Venelles");
    }

    @Test
    @DisplayName("#3095 : une valeur redevenue disponible réapparaît")
    void une_valeur_redevenue_disponible_reapparait() {
        // Le pendant, et c'est celui qu'un simple filtrage à la création ne saurait jamais faire : on
        // relâche un autre critère, et le choix se rouvre.
        offertes.set(List.of("Aix"));
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(ignore -> {});
        assertThat(entrees(editeur)).containsExactly("Aix");

        offertes.set(List.of("Aix", "Venelles"));
        ouvrirLeMenu(editeur);

        assertThat(entrees(editeur)).containsExactly("Aix", "Venelles");
    }

    @Test
    @DisplayName("#3095 : une valeur cochée devenue impossible reste cochée, visible et marquée")
    void une_valeur_cochee_devenue_impossible_est_conservee() {
        // Le point sur lequel tout le chantier se joue. La retirer élargirait le filtre en silence :
        // l'écran montrerait plus que ce qu'il annonce, soit le défaut que le palier 1 vient de
        // corriger (#3056, #3093). On la garde donc, et on dit qu'elle ne ramène rien.
        AtomicReference<Predicate<String>> courant = new AtomicReference<>();
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(courant::set);
        cocher(editeur, "Venelles");
        assertThat(courant.get().test("Venelles")).isTrue();
        assertThat(courant.get().test("Aix")).isFalse();

        offertes.set(List.of("Aix", "Gardanne")); // Venelles n'est plus offert
        ouvrirLeMenu(editeur);

        assertThat(cochees(editeur))
                .as("le filtre posé ne doit pas se relâcher tout seul")
                .containsExactly("Venelles");
        assertThat(entrees(editeur))
                .as("elle reste visible, sans quoi on ne saurait pas pourquoi la table est vide")
                .contains("Venelles");
        assertThat(courant.get().test("Venelles"))
                .as("et le prédicat continue de la retenir")
                .isTrue();
        assertThat(marquees(editeur))
                .as("marquée comme ne ramenant rien, sinon elle se lit comme un choix ordinaire")
                .containsExactly("Venelles");
    }

    /// Les entrées marquées « hors du jeu courant » par leur classe de style.
    private static List<String> marquees(Node editeur) {
        return ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(CheckMenuItem.class::cast)
                        .filter(item -> item.getStyleClass().contains(CritereListe.CLASSE_VALEUR_HORS_JEU))
                        .map(CheckMenuItem::getText)
                        .toList();
    }

    @Test
    @DisplayName("#3095 : un choix disparu retombe sur le défaut, et le basculement est ANNONCÉ")
    void un_choix_disparu_retombe_sur_le_defaut_et_le_dit() {
        // Arbitrage : plutôt que de laisser la puce sans choix, on réapplique le défaut. La conséquence
        // est que l'écran filtre alors sur autre chose que ce qui avait été demandé - c'est justement le
        // mode de panne du palier 1. Il est donc annoncé, faute de quoi la table changerait sous les
        // yeux sans raison lisible.
        AtomicReference<List<String>> groupes = new AtomicReference<>(List.of("Chiroptères", "Oiseaux"));
        List<String> annonces = new java.util.ArrayList<>();
        AtomicReference<Predicate<String>> courant = new AtomicReference<>();
        CritereFiltre<String> critere = CritereListe.valeursPreselectionnees(
                "groupe",
                "Taxon parent",
                CritereListe.Domaine.deChaines(groupes::get),
                (String groupe) -> (String ligne) -> ligne.equals(groupe),
                offertes -> offertes.isEmpty() ? null : offertes.get(0),
                annonces::add);
        javafx.scene.control.ComboBox<?> choix = (javafx.scene.control.ComboBox<?>) critere.editeur(courant::set);

        choix.getSelectionModel().select(1); // « Oiseaux »
        assertThat(critere.valeurCourante(choix)).containsExactly("Oiseaux");

        groupes.set(List.of("Chiroptères")); // les oiseaux sortent du jeu courant
        choix.getOnShowing().handle(new Event(Event.ANY));

        assertThat(critere.valeurCourante(choix))
                .as("le défaut reprend la main plutôt que de laisser la puce sans choix")
                .containsExactly("Chiroptères");
        assertThat(annonces)
                .as("l'écran filtre sur autre chose que ce qui était demandé : le taire serait le défaut"
                        + " que le palier 1 vient de corriger")
                .containsExactly("Oiseaux");
        assertThat(courant.get().test("Chiroptères")).isTrue();
    }

    @Test
    @DisplayName("#3095 : un choix toujours offert n'est ni remplacé ni annoncé")
    void un_choix_toujours_offert_ne_bascule_pas() {
        // Le pendant : l'annonce doit rester rare, sinon elle cesse d'être lue.
        AtomicReference<List<String>> groupes = new AtomicReference<>(List.of("Chiroptères", "Oiseaux"));
        List<String> annonces = new java.util.ArrayList<>();
        CritereFiltre<String> critere = CritereListe.valeursPreselectionnees(
                "groupe",
                "Taxon parent",
                CritereListe.Domaine.deChaines(groupes::get),
                (String groupe) -> (String ligne) -> ligne.equals(groupe),
                offertes -> offertes.isEmpty() ? null : offertes.get(0),
                annonces::add);
        javafx.scene.control.ComboBox<?> choix = (javafx.scene.control.ComboBox<?>) critere.editeur(ignore -> {});

        choix.getSelectionModel().select(1); // « Oiseaux »
        groupes.set(List.of("Chiroptères", "Oiseaux", "Orthoptères"));
        choix.getOnShowing().handle(new Event(Event.ANY));

        assertThat(critere.valeurCourante(choix)).containsExactly("Oiseaux");
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#3095 : une valeur cochée toujours offerte n'est pas marquée")
    void une_valeur_cochee_toujours_offerte_n_est_pas_marquee() {
        AtomicReference<Predicate<String>> courant = new AtomicReference<>();
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(courant::set);
        cocher(editeur, "Aix");

        offertes.set(List.of("Aix", "Venelles"));
        ouvrirLeMenu(editeur);

        assertThat(cochees(editeur)).containsExactly("Aix");
        assertThat(marquees(editeur))
                .as("le marquage doit rester rare, sinon il cesse d'être lu")
                .isEmpty();
    }
}
