package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Ce que [BancDeRecette#semer] promet à qui l'appelle **deux fois**.
///
/// ## Le défaut, et pourquoi il ne se voyait pas
///
/// `semer` affectait son champ au lieu de l'ajouter. Deux appels laissaient donc le **second seul**,
/// en silence : rien ne rougissait à l'appel, rien au montage, et le défaut paraissait trois gestes
/// plus loin sous la forme d'un nœud introuvable - `the query "#boutonImporterNuit" returned no
/// nodes`. On cherche alors du côté de l'écran, du FXML, de la feature ; pas du côté d'un `.semer()`
/// qui a l'air d'en ajouter un.
///
/// La forme fluide invite à l'erreur, et sa voisine le montre : `remplacer(Module...)` **accumule**
/// (`remplacements.addAll`). Toutes les autres méthodes de ce constructeur règlent une valeur unique -
/// une taille, un exécuteur - où remplacer est le sens attendu. `semer` avait l'air d'ajouter et
/// remplaçait (#5350).
///
/// ## Ce que ces cas gardent, et ce qu'ils ne gardent pas
///
/// Ils gardent que les deux semis **s'exécutent**, et dans l'ordre déclaré. Ils ne gardent pas qu'un
/// semis voie ce que le précédent a écrit : les deux reçoivent le même injecteur, et cela découle de
/// la signature plutôt que d'un choix à éprouver.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecetteSemisTest {

    private final List<String> passages = new ArrayList<>();

    @Start
    void start(Stage stage) throws IOException {
        BancDeRecette.surLeChrome()
                // SYNCHRONE : ces cas assertent, ils ne filment pas.
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .semer(injecteur -> passages.add("premier"))
                .semer(injecteur -> passages.add("second"))
                .montrer(stage);
    }

    @Test
    @DisplayName("#5350 : DEUX semis s'exécutent tous les deux, le second n'efface pas le premier")
    void deux_semis_s_executent_tous_les_deux() {
        // Sur la version qui remplace, `passages` ne porte que « second » : le premier n'a jamais été
        // appelé, et c'est exactement ce qui laissait un écran monter sans son carré.
        assertThat(passages)
                .as("les deux semis déclarés doivent avoir tourné ; un appel qui en efface un autre est"
                        + " un travail perdu que rien ne signale")
                .containsExactly("premier", "second");
    }
}
