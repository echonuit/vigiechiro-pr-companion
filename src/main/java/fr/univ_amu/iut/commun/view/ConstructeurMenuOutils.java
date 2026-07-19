package fr.univ_amu.iut.commun.view;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/// Peuple le `MenuButton` du menu ☰ à partir des [ActionMenu] contribuées (#930), à la façon dont
/// [CartesAccueil] bâtit les cartes d'accueil. Construction pure, sans état, extraite de
/// [MainController] pour le garder mince.
///
/// Les entrées sont triées par (groupe, ordre) ; un [SeparatorMenuItem] est inséré entre deux groupes
/// consécutifs distincts (mise en page historique). Les libellés sont **réévalués à l'ouverture** du
/// menu ([MenuButton#setOnShowing]) pour les entrées à libellé dynamique (ex. état de connexion).
final class ConstructeurMenuOutils {

    private ConstructeurMenuOutils() {}

    /// Vide puis peuple `menu` depuis `actions`. `fenetre` fournit (au clic) la fenêtre propriétaire
    /// des dialogues des actions.
    static void peupler(MenuButton menu, Collection<ActionMenu> actions, Supplier<Window> fenetre) {
        List<ActionMenu> triees = actions.stream()
                .sorted(Comparator.comparing(ActionMenu::groupe).thenComparingInt(ActionMenu::ordre))
                .toList();

        Map<MenuItem, ActionMenu> parItem = new LinkedHashMap<>();
        menu.getItems().clear();
        GroupeMenu groupePrecedent = null;
        for (ActionMenu action : triees) {
            if (groupePrecedent != null && action.groupe() != groupePrecedent) {
                menu.getItems().add(new SeparatorMenuItem());
            }
            MenuItem item = construire(action, fenetre);
            menu.getItems().add(item);
            parItem.put(item, action);
            groupePrecedent = action.groupe();
        }

        // Libellés ET icônes dynamiques : réévalués à chaque ouverture (sans effet pour les entrées fixes).
        // L'icône suivait jusqu'ici la seule construction : une entrée dont l'état change - la connexion -
        // aurait gardé l'icône de son premier état, à contre-sens de son libellé (#1933).
        menu.setOnShowing(evenement -> parItem.forEach((item, action) -> {
            item.setText(action.libelle());
            appliquerIcone(item, action);
        }));
    }

    private static MenuItem construire(ActionMenu action, Supplier<Window> fenetre) {
        MenuItem item;
        if (action.estBascule()) {
            CheckMenuItem bascule = new CheckMenuItem(action.libelle());
            bascule.selectedProperty().bindBidirectional(action.selection());
            item = bascule;
        } else {
            item = new MenuItem(action.libelle());
            item.setOnAction(evenement -> action.executer(fenetre.get()));
        }
        appliquerIcone(item, action);
        return item;
    }

    /// Pose (ou retire) l'icône de l'entrée. Réutilise le [FontIcon] déjà en place quand il y en a un :
    /// une entrée dont l'icône change à chaque ouverture n'a pas besoin d'un noeud neuf à chaque fois.
    private static void appliquerIcone(MenuItem item, ActionMenu action) {
        String literal = action.iconeLiteral();
        if (literal.isBlank()) {
            item.setGraphic(null);
        } else if (item.getGraphic() instanceof FontIcon icone) {
            icone.setIconLiteral(literal);
        } else {
            item.setGraphic(new FontIcon(literal));
        }
    }
}
