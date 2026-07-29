package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.multisite.model.LignePassage;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le menu ☰ de « Carte & passages » **se referme** sur les gestes indisponibles.
///
/// **Pourquoi ce test existe.** Les quatre entrées de lot (#2357) disparaissent quand la fonctionnalité
/// qui les fournit est désactivée - c'est le patron de l'[ADR 0003], repris par l'ADR 2357 : « absente,
/// l'entrée disparaît plutôt que de rester grisée sans recours ». La décision est écrite dans deux ADR et
/// dans `patterns.md`, et **aucun test ne la tenait**.
///
/// La mesure de mutation l'a dit : les quatre `setVisible(...)` survivaient. `DecouverteModulesTest`
/// vérifie que l'**injecteur** se construit sans la fonctionnalité ; personne ne vérifiait que le **menu**
/// s'y adapte. Un écran qui offrirait un geste que rien ne peut exécuter est pire qu'un écran qui ne
/// l'offre pas.
///
/// Le test appelle [MenuActionsMultisite#installer] directement : la vue entière n'apporterait rien ici,
/// et son injecteur fixe les quatre actions comme présentes.
@ExtendWith(ApplicationExtension.class)
class MenuActionsMultisiteTest {

    /// TestFX exige un point de départ ; le toolkit JavaFX suffit, aucune scène n'est nécessaire pour
    /// construire des [MenuItem].
    @Start
    void demarrer(Stage stage) {
        // Volontairement vide : seul le démarrage du toolkit compte.
    }

    private static MenuActionsMultisite.Entrees entrees() {
        return new MenuActionsMultisite.Entrees(
                new MenuItem(),
                new MenuItem(),
                new MenuItem(),
                new MenuItem(),
                new MenuItem(),
                new MenuItem(),
                new MenuItem(),
                new MenuItem(),
                new MenuItem());
    }

    /// Installe le menu avec les quatre actions de lot présentes ou absentes, et rend ses entrées de lot.
    private static List<MenuItem> entreesDeLot(boolean actionsDisponibles, MenuActionsMultisite.Entrees e) {
        Optional<ActionGroupee> action = actionsDisponibles ? Optional.of(mock(ActionGroupee.class)) : Optional.empty();
        MenuActionsMultisite.installer(
                e,
                new SimpleBooleanProperty(true),
                new SimpleObjectProperty<LignePassage>(null),
                new SimpleIntegerProperty(2),
                new ActionsDeLot(action, action, action, action),
                true,
                true);
        return List.of(
                e.preparerSelection(),
                e.televerserSelection(),
                e.importerResultatsSelection(),
                e.declencherCalculSelection());
    }

    @Test
    @DisplayName("ADR 2357 : fonctionnalités actives, les quatre entrées de lot sont OFFERTES")
    void actions_disponibles_entrees_offertes() {
        assertThat(entreesDeLot(true, entrees()))
                .as("le témoin du test suivant : sans lui, « invisible » pourrait n'être qu'un défaut de câblage")
                .allSatisfy(item -> assertThat(item.isVisible()).isTrue());
    }

    @Test
    @DisplayName("ADR 2357 : fonctionnalité coupée, les quatre entrées de lot DISPARAISSENT du menu")
    void actions_absentes_entrees_retirees() {
        assertThat(entreesDeLot(false, entrees()))
                .as("une entrée grisée sans recours vaut moins qu'une entrée absente : elle promet un geste"
                        + " que rien ne peut exécuter")
                .allSatisfy(item -> assertThat(item.isVisible()).isFalse());
    }

    @Test
    @DisplayName("#1338 : « Relever l'état des analyses » se retire aussi quand le relevé est indisponible")
    void relever_les_analyses_se_retire() {
        MenuActionsMultisite.Entrees offert = entrees();
        MenuActionsMultisite.Entrees retire = entrees();
        Optional<ActionGroupee> action = Optional.of(mock(ActionGroupee.class));

        for (boolean peutRelever : List.of(true, false)) {
            MenuActionsMultisite.installer(
                    peutRelever ? offert : retire,
                    new SimpleBooleanProperty(true),
                    new SimpleObjectProperty<LignePassage>(null),
                    new SimpleIntegerProperty(0),
                    new ActionsDeLot(action, action, action, action),
                    true,
                    peutRelever);
        }

        assertThat(offert.reculerAnalyses().isVisible()).isTrue();
        assertThat(retire.reculerAnalyses().isVisible()).isFalse();
    }
}
