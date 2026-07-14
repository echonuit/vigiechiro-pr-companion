package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Câblage Guice du menu ☰ (#930) : le socle agrège les [ActionMenu] via `Multibinder<ActionMenu>` et
/// les ordonne par (groupe, ordre). On vérifie la séquence historique des entrées socle.
class ActionMenuWiringTest {

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Le socle contribue les 6 entrées du menu ☰, dans l'ordre (groupe puis ordre)")
    void agrege_et_ordonne(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        Injector injector = RacineInjecteur.creer();

        Set<ActionMenu> actions = injector.getInstance(Key.get(new TypeLiteral<Set<ActionMenu>>() {}));

        List<String> ordonnees = actions.stream()
                .sorted(Comparator.comparing(ActionMenu::groupe).thenComparingInt(ActionMenu::ordre))
                .map(action -> action.getClass().getSimpleName())
                .toList();

        // Les variantes « complètes » (#1346) suivent immédiatement leur variante « base seule » : les deux
        // se lisent par paire, la complète en second car elle est plus lourde et plus rare.
        //
        // « Repartir d'une base neuve » (#1419) ferme le groupe : c'est l'action la plus lourde et la plus
        // rare, et les quatre qui la précèdent en sont le prérequis. L'ordre du menu se lit comme la
        // procédure elle-même — on sauvegarde, puis seulement on recommence.
        assertThat(ordonnees)
                .containsExactly(
                        "ActionSauvegarder",
                        "ActionSauvegarderComplet",
                        "ActionRestaurer",
                        "ActionRestaurerComplet",
                        "ActionResetGuide",
                        "ActionPurger",
                        "ActionSourceEspece",
                        "ActionOuvrirReglages",
                        "ActionConnexion");
    }
}
