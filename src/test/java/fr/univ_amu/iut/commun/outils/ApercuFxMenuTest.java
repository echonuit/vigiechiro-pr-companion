package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

/// Contrat de [ApercuFx#enregistrerMenuOuvert] (#2065), le helper qui photographie un menu déployé.
///
/// Il vient de `CaptureValidationTadarida`, où il allait être recopié pour trois autres écrans.
///
/// Ce test épingle une propriété que le code d'origine croyait fausse : **capturer un menu ne le vide
/// pas**. Son commentaire affirmait que « le menu d'origine s'en trouve vidé, sans conséquence : le
/// processus se termine après », ce qui aurait interdit tout appelant capturant autre chose ensuite -
/// `CaptureMultisite` est exactement dans ce cas. Mesure faite, les deux listes d'items sont
/// indépendantes et la source est intacte.
///
/// La propriété est donc **acquise, pas construite** : le test existe pour qu'elle le reste. Elle
/// tomberait si quelqu'un remplaçait la copie défensive par `apercu.getItems().setAll(menu.getItems())`
/// suivi d'un `clear()`, ou déplaçait les items au lieu de les recopier.
@ExtendWith(ApplicationExtension.class)
class ApercuFxMenuTest {

    @Test
    @DisplayName("Le menu source garde ses entrées après la capture")
    void le_menu_source_est_restitue(FxRobot robot, @TempDir Path tmp) {
        MenuItem premier = new MenuItem("Exporter…");
        MenuItem second = new MenuItem("Écouter la sélection filtrée");
        MenuButton menu = new MenuButton("", null, premier, second);
        List<MenuItem> avant = List.copyOf(menu.getItems());

        // Le helper monte un Stage : il s'execute sur le fil JavaFX, comme en production.
        robot.interact(() -> ApercuFx.enregistrerMenuOuvert(menu, tmp.resolve("menu.png")));

        assertThat(menu.getItems())
                .as("capturer un menu ne doit rien lui prendre : un appelant qui photographie ensuite "
                        + "l'écran entier y trouverait un ☰ vide")
                .containsExactlyElementsOf(avant);
    }

    @Test
    @DisplayName("Un menu vide ne fait pas échouer la capture")
    void un_menu_vide_ne_leve_pas(FxRobot robot, @TempDir Path tmp) {
        MenuButton menu = new MenuButton();

        // Un job de capture ne doit jamais tomber sur un écran dont le menu n'a rien à montrer : le
        // helper rend compte par son booléen, il ne lève pas.
        robot.interact(() -> ApercuFx.enregistrerMenuOuvert(menu, tmp.resolve("vide.png")));

        assertThat(menu.getItems()).isEmpty();
    }

    @Test
    @DisplayName("#3169 : les feuilles se cherchent sur les ANCÊTRES, là où le FXML les pose")
    void les_feuilles_se_cherchent_sur_les_ancetres() {
        // Le popup vit dans la scène de son HÔTE, et cet hôte était nu : aucune règle de `design.css`
        // ne s'y appliquait. Tant qu'un menu ne portait que du texte ordinaire, le rendu par défaut
        // passait pour juste. Le jour où une entrée a porté un SENS par sa classe - la valeur « hors
        // jeu », grisée et en italique (#3095) - la capture a montré une entrée identique aux autres,
        // sous une légende qui la disait grisée. Mesuré sur le PNG : l'encre des deux lignes valait
        // (134,126,134) et (131,124,128), soit la même. Passe 8 de la clôture des suites de #3092.
        //
        // Ce cas épingle le piège qui a fait échouer mon PREMIER correctif : l'attribut `stylesheets`
        // d'un FXML garnit le nœud RACINE, pas la scène. Ne lire que la scène rendait une liste vide
        // sur tous les écrans du dépôt, et le correctif ne changeait rien.
        MenuButton menu = new MenuButton("", null, new MenuItem("Aix-en-Provence"));
        VBox racine = new VBox(new VBox(menu));
        racine.getStylesheets().add("design.css");
        Scene scene = new Scene(racine, 300, 120);
        scene.getStylesheets().add("palette.css");

        assertThat(ApercuFx.feuillesDe(menu))
                .as("celle de la scène ET celle de l'ancêtre, même à deux niveaux de profondeur")
                .containsExactlyInAnyOrder("palette.css", "design.css");
    }

    @Test
    @DisplayName("#3169 : un menu hors scène ne fait pas échouer la lecture des feuilles")
    void un_menu_hors_scene_ne_leve_pas() {
        assertThat(ApercuFx.feuillesDe(new MenuButton())).isEmpty();
    }
}
