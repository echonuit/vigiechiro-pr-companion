package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

/// Double-cliquer une ligne de tableau **sans dépendre du placement à l'écran** (#4554).
public final class DoubleClicDeterministe {

    private DoubleClicDeterministe() {}

    public static void surLigne(FxRobot robot, String idTable, int index) {
        // Amener la ligne dans le cadre AVANT de la chercher. Les `TableRow` sont virtualisés :
        // seules les lignes visibles existent comme noeuds, et une ligne hors cadre n'est donc pas
        // « introuvable », elle n'est pas encore construite. Le message qui remonterait - « aucune
        // ligne d'index N » - se lit pourtant comme une absence de donnée (#4016).
        robot.interact(() -> table(robot, idTable).scrollTo(index));
        WaitForAsyncUtils.waitForFxEvents();
        Node ligne = robot.lookup(idTable).lookup(".table-row-cell").queryAll().stream()
                .map(noeud -> (TableRow<?>) noeud)
                .filter(rangee -> !rangee.isEmpty() && rangee.getIndex() == index)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune ligne d'index " + index + " dans " + idTable));
        robot.interact(() -> ligne.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                2,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Double-cliquer la ligne dont une cellule porte `texte`, sans que l'appelant connaisse sa place.
    ///
    /// Sept des onze sites d'appel de #4554 nomment une donnée - un numéro de site, une date - et non
    /// une position. Leur faire calculer un index les lierait à un ordre de tri qu'ils n'ont pas à
    /// connaître. L'index se résout ici, à partir de ce que les colonnes rendent, puis le geste
    /// délègue à [#surLigne] et retrouve donc le défilement et l'événement direct.
    ///
    /// La **première** ligne correspondante est retenue, comme le fait `doubleClickOn(String)` de
    /// TestFX, et la comparaison est une inclusion : une cellule de date affiche « 21/06/2026 » là où
    /// l'appelant vise cette date, mais une cellule composée peut porter davantage.
    public static void surLigneContenant(FxRobot robot, String idTable, String texte) {
        TableView<?> table = table(robot, idTable);
        int lignes = table.getItems().size();
        AtomicInteger trouve = new AtomicInteger(-1);
        // On lit le TEXTE RENDU, jamais la valeur portée par l'item. La colonne de date de
        // M-Multisite affiche « 21/06/2026 » depuis une date ISO (#4019) : comparer la valeur ferait
        // manquer la ligne que la personne voit. Le rendu n'existe que pour les lignes construites,
        // d'où le défilement, qui sert ici à FAIRE EXISTER la cellule autant qu'à l'atteindre.
        for (int i = 0; i < lignes && trouve.get() < 0; i++) {
            int index = i;
            robot.interact(() -> {
                table.scrollTo(index);
                texteDeLaLigne(robot, idTable, index)
                        .filter(rendu -> rendu.contains(texte))
                        .ifPresent(rendu -> trouve.set(index));
            });
        }
        if (trouve.get() < 0) {
            // Un refus dit ce qui manque, article A13 : « aucune ligne d'index -1 » ferait chercher
            // un défaut de placement là où la donnée est simplement absente.
            throw new AssertionError(
                    "aucune ligne de " + idTable + " ne porte « " + texte + " » sur " + lignes + " ligne(s) lues");
        }
        surLigne(robot, idTable, trouve.get());
    }

    /// Le texte que la ligne d'index donné **affiche**, colonnes concaténées.
    private static Optional<String> texteDeLaLigne(FxRobot robot, String idTable, int index) {
        return robot.lookup(idTable).lookup(".table-row-cell").queryAll().stream()
                .map(noeud -> (TableRow<?>) noeud)
                .filter(rangee -> !rangee.isEmpty() && rangee.getIndex() == index)
                .findFirst()
                .map(rangee -> rangee.lookupAll(".table-cell").stream()
                        .filter(Labeled.class::isInstance)
                        .map(cellule -> ((Labeled) cellule).getText())
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\u001f")));
    }

    public static TableView<?> table(FxRobot robot, String idTable) {
        return robot.lookup(idTable).queryAs(TableView.class);
    }
}
