package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Construction du menu ☰ par [ConstructeurMenuOutils] (#930). [ApplicationExtension] initialise le
/// toolkit JavaFX (MenuItem/CheckMenuItem) ; aucune scène affichée. On vérifie le tri (groupe puis
/// ordre), l'insertion d'un séparateur entre groupes, la bascule liée et la réévaluation des libellés
/// à l'ouverture du menu.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class ConstructeurMenuOutilsTest {

    @Test
    @DisplayName("les entrées sont triées par (groupe, ordre) avec un séparateur entre groupes")
    void trie_par_groupe_puis_ordre_avec_separateurs() {
        List<ActionMenu> actions = List.of(
                action(GroupeMenu.COMPTE, 10, () -> "Compte", () -> {}),
                action(GroupeMenu.BASE, 20, () -> "B20", () -> {}),
                action(GroupeMenu.BASE, 10, () -> "B10", () -> {}),
                action(GroupeMenu.MAINTENANCE, 10, () -> "Maint", () -> {}));

        MenuButton menu = new MenuButton();
        ConstructeurMenuOutils.peupler(menu, actions, () -> null);

        assertThat(menu.getItems()).hasSize(6);
        assertThat(menu.getItems().get(2)).isInstanceOf(SeparatorMenuItem.class);
        assertThat(menu.getItems().get(4)).isInstanceOf(SeparatorMenuItem.class);
        assertThat(menu.getItems().stream()
                        .filter(item -> !(item instanceof SeparatorMenuItem))
                        .map(MenuItem::getText)
                        .toList())
                .containsExactly("B10", "B20", "Maint", "Compte");
    }

    @Test
    @DisplayName("une bascule produit une case à cocher liée bidirectionnellement à sa property")
    void bascule_est_une_case_liee() {
        BooleanProperty selection = new SimpleBooleanProperty(false);
        MenuButton menu = new MenuButton();
        ConstructeurMenuOutils.peupler(
                menu, List.of(bascule(GroupeMenu.PREFERENCES, 10, "Toggle", selection)), () -> null);

        assertThat(menu.getItems()).singleElement().isInstanceOf(CheckMenuItem.class);
        CheckMenuItem item = (CheckMenuItem) menu.getItems().get(0);
        assertThat(item.isSelected()).isFalse();

        selection.set(true);
        assertThat(item.isSelected()).isTrue();

        item.setSelected(false);
        assertThat(selection.get()).isFalse();
    }

    @Test
    @DisplayName("les libellés dynamiques sont réévalués à l'ouverture du menu")
    void libelles_reevalues_a_l_ouverture() {
        AtomicReference<String> etat = new AtomicReference<>("initial");
        MenuButton menu = new MenuButton();
        ConstructeurMenuOutils.peupler(menu, List.of(action(GroupeMenu.COMPTE, 10, etat::get, () -> {})), () -> null);

        MenuItem item = menu.getItems().get(0);
        assertThat(item.getText()).isEqualTo("initial");

        etat.set("connecté");
        menu.getOnShowing().handle(new Event(Event.ANY));
        assertThat(item.getText()).isEqualTo("connecté");
    }

    private static ActionMenu action(GroupeMenu groupe, int ordre, Supplier<String> libelle, Runnable executer) {
        return new ActionMenu() {
            @Override
            public GroupeMenu groupe() {
                return groupe;
            }

            @Override
            public int ordre() {
                return ordre;
            }

            @Override
            public String libelle() {
                return libelle.get();
            }

            @Override
            public void executer(Window proprietaire) {
                executer.run();
            }
        };
    }

    private static ActionMenu bascule(GroupeMenu groupe, int ordre, String libelle, BooleanProperty selection) {
        return new ActionMenu() {
            @Override
            public GroupeMenu groupe() {
                return groupe;
            }

            @Override
            public int ordre() {
                return ordre;
            }

            @Override
            public String libelle() {
                return libelle;
            }

            @Override
            public boolean estBascule() {
                return true;
            }

            @Override
            public BooleanProperty selection() {
                return selection;
            }

            @Override
            public void executer(Window proprietaire) {
                // bascule : pas d'action au clic
            }
        };
    }
}
