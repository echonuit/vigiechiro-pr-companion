package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Les deux règles du chemin des **puces à cocher** que rien ne gardait (#3134).
///
/// Trouvées par PIT pendant #3128, et confirmées par un second signal : muter le prédicat neutre en
/// `false` survivait, et les **trois** `setText` du libellé survivaient aussi.
///
/// Ce ne sont pas des détails. « Rien de coché n'écarte rien » est la règle que la Javadoc de
/// [CritereListe] pose en premier, et celle sur laquelle tout le chantier #3092 s'appuie : c'est elle
/// qui fait qu'une valeur non replacée **élargit** le filtre au lieu de le vider (#3056, #3093). Et le
/// libellé est du **visible** : un geste testé n'est pas un écran regardé.
@ExtendWith(ApplicationExtension.class)
class CritereListeSemantiqueTest {

    private static final String INVITE = "Choisir un lieu";

    private static CritereFiltre<String> critere() {
        return CritereListe.multiple(
                ClesCriteres.LIEU, "Lieu", INVITE, () -> List.of("Aix", "Venelles", "Gardanne"), (Function<
                                String, String>)
                        ligne -> ligne);
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
    @DisplayName("#3134 : une puce fraîchement ajoutée n'écarte AUCUNE ligne")
    void une_puce_fraichement_ajoutee_n_ecarte_rien() {
        // Garde le prédicat publié **à la création** de l'éditeur.
        AtomicReference<Predicate<String>> courant = new AtomicReference<>();

        critere().editeur(courant::set);

        assertThat(courant.get())
                .as("un prédicat non nul est publié dès l'ouverture de l'éditeur")
                .isNotNull();
        for (String ligne : List.of("Aix", "Venelles", "Gardanne", "une valeur qui n'est même pas offerte")) {
            assertThat(courant.get().test(ligne))
                    .as("« %s » doit passer tant que rien n'est coché", ligne)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("#3134 : tout décocher rend la puce neutre, elle ne vide pas la table")
    void tout_decocher_rend_la_puce_neutre() {
        // C'est CE chemin que PIT signalait, et non le précédent : à la création, l'éditeur publie son
        // prédicat neutre en direct, tandis que décocher repasse par `predicat()`, dont la branche
        // « rien de retenu » n'était gardée nulle part.
        //
        // Le mutant y rend « ligne -> false » : la table se vide au moment précis où l'on relâche le
        // filtre, soit l'inverse exact de ce qu'on demandait. Et comme la puce reste posée sans rien de
        // coché, rien à l'écran n'expliquerait le vide.
        AtomicReference<Predicate<String>> courant = new AtomicReference<>();
        CritereFiltre<String> critere = critere();
        Node editeur = critere.editeur(courant::set);

        cocher(editeur, "Aix");
        assertThat(courant.get().test("Aix")).isTrue();
        assertThat(courant.get().test("Venelles"))
                .as("prérequis : la puce filtre réellement une fois cochée")
                .isFalse();

        decocher(editeur, "Aix");

        for (String ligne : List.of("Aix", "Venelles", "Gardanne")) {
            assertThat(courant.get().test(ligne))
                    .as("« %s » doit repasser : rien de coché n'écarte rien", ligne)
                    .isTrue();
        }
    }

    private static void decocher(Node editeur, String valeur) {
        ((MenuButton) editeur)
                .getItems().stream()
                        .filter(CheckMenuItem.class::isInstance)
                        .map(CheckMenuItem.class::cast)
                        .filter(item -> valeur.equals(item.getText()))
                        .findFirst()
                        .orElseThrow()
                        .setSelected(false);
    }

    @Test
    @DisplayName("#3134 : le bouton dit CE QUI EST RETENU, dans les trois cas")
    void le_bouton_dit_ce_qui_est_retenu() {
        // Le commentaire du code dit pourquoi ces trois branches existent : « une puce qui afficherait
        // toujours "Taxon parent" obligerait à la déplier pour savoir ce qu'elle fait ». Les trois
        // `setText` survivaient pourtant à la mutation, faute d'assertion sur le libellé.
        CritereFiltre<String> critere = critere();
        MenuButton bouton = (MenuButton) critere.editeur(ignore -> {});

        assertThat(bouton.getText())
                .as("rien de coché : la puce invite, elle n'annonce pas un filtre")
                .isEqualTo(INVITE);

        cocher(bouton, "Aix");
        assertThat(bouton.getText())
                .as("une seule valeur : on la nomme, la compter n'apprendrait rien")
                .isEqualTo("Aix");

        cocher(bouton, "Venelles");
        assertThat(bouton.getText())
                .as("au-delà, on compte : les énumérer déborderait la puce")
                .isEqualTo("2 sélectionnés");

        cocher(bouton, "Gardanne");
        assertThat(bouton.getText()).isEqualTo("3 sélectionnés");
    }
}
