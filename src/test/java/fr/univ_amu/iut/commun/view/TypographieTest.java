package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import javafx.scene.text.Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// La police embarquée est-elle réellement **dans le jar**, et enregistrée ? (#3361)
///
/// ## Un garde qui aurait menti
///
/// La première version de ce test vérifiait seulement que `Font.getFamilies()` contient « Noto Sans »
/// après `installer()`. **Il restait vert en retirant le chargement** : la machine de développement a
/// Noto Sans installée en système, si bien que la famille existe de toute façon. Le test aurait
/// certifié un embarquement qui ne se faisait pas - exactement le défaut qu'il devait prévenir, puisque
/// `Font.loadFont` rend `null` sans lever quand la ressource manque, et qu'`installer()` avale l'échec
/// par contrat.
///
/// La vérification porte donc d'abord sur ce qui est **déterministe partout** : les fichiers sont-ils
/// présents et lisibles dans les ressources ? C'est le risque réel - un changement de packaging, un
/// filtrage de ressources, un `.gitattributes` qui abîme un binaire.
@ExtendWith(ApplicationExtension.class)
class TypographieTest {

    @Test
    @DisplayName("#3361 : les deux fichiers de police sont dans les ressources, et ne sont pas vides")
    void les_fichiers_sont_embarques() throws Exception {
        for (String chemin : new String[] {"/fonts/NotoSans-Regular.ttf", "/fonts/NotoSans-Bold.ttf"}) {
            try (InputStream flux = Typographie.class.getResourceAsStream(chemin)) {
                assertThat(flux)
                        .as(
                                "« %s » doit être embarqué : sans lui, le produit retombe en SILENCE sur la "
                                        + "police du système, et le rendu redevient dépendant de la machine",
                                chemin)
                        .isNotNull();
                assertThat(flux.readAllBytes().length)
                        .as("« %s » ne doit pas être un fichier vide ou tronqué", chemin)
                        .isGreaterThan(100_000);
            }
        }
    }

    @Test
    @DisplayName("#3361 : le nom cité en tête de base.css est bien celui que JavaFX connaît")
    void la_famille_est_celle_du_css() throws Exception {
        Typographie.installer();

        assertThat(Font.getFamilies())
                .as("un nom de famille mal orthographié ferait retomber le CSS sur la police suivante, "
                        + "sans que rien ne le dise")
                .contains(Typographie.FAMILLE);

        String css = new String(
                Typographie.class
                        .getResourceAsStream("/fr/univ_amu/iut/commun/view/base.css")
                        .readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(css)
                .as("la police embarquée doit être citée EN PREMIER : placée après, elle ne servirait "
                        + "que si les autres manquent, ce qui est précisément le cas qu'on veut supprimer")
                .contains("-fx-font-family: \"" + Typographie.FAMILLE + "\"");
    }

    @Test
    @DisplayName("#3361 : installer deux fois ne recharge rien - l'appel vient de deux endroits")
    void idempotente() {
        // `App.start` et `ApercuFx.enregistrerPng` appellent tous deux : l'ordre ne doit pas compter, et
        // une session de capture qui enchaîne 41 images ne doit pas relire la police à chaque fois.
        Typographie.installer();
        int apresPremiere = Font.getFontNames(Typographie.FAMILLE).size();
        Typographie.installer();

        assertThat(Font.getFontNames(Typographie.FAMILLE)).hasSize(apresPremiere);
    }
}
