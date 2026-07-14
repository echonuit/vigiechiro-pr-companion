package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.MessageObservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le **fil de discussion** avec le validateur ([PanneauDiscussion], #1417 lire / #1418 répondre).
///
/// Bâti comme un **nœud**, pas comme une fenêtre : c'est ce qui le rend vérifiable ici sans qu'un
/// `showAndWait()` fige le test (leçon #1013 / #1405).
@ExtendWith(ApplicationExtension.class)
class PanneauDiscussionTest {

    private static final String MOI = "u-moi";

    /// Raison de blocage : la détection n'existe pas côté plateforme — il n'y a personne à qui parler.
    private static final Optional<String> PAS_SUR_LA_PLATEFORME =
            Optional.of("Cette détection n'existe pas sur VigieChiro.");

    /// Rien ne bloque : l'utilisateur peut écrire.
    private static final Optional<String> PEUT_ECRIRE = Optional.empty();

    @Test
    @DisplayName("#1418 : rien à lire ET rien à dire → le panneau reste FERMÉ (il ne vole pas de largeur"
            + " au spectrogramme pour ne rien dire)")
    void rien_a_lire_ni_a_dire_panneau_ferme() {
        PanneauDiscussion panneau = new PanneauDiscussion();

        panneau.afficher(List.of(), MOI, PAS_SUR_LA_PLATEFORME);

        assertThat(panneau.racine().isVisible()).isFalse();
        assertThat(panneau.racine().isManaged())
                .as("non seulement invisible, mais non géré : il ne prend AUCUNE place")
                .isFalse();
    }

    @Test
    @DisplayName("#1418 : fil vide mais détection connue de VigieChiro → le panneau S'OUVRE : on peut y"
            + " ouvrir la discussion")
    void fil_vide_mais_ecriture_possible_panneau_ouvert() {
        PanneauDiscussion panneau = new PanneauDiscussion();

        panneau.afficher(List.of(), MOI, PEUT_ECRIRE);

        assertThat(panneau.racine().isVisible())
                .as("depuis #1418, il n'y a plus seulement quelque chose à LIRE : il y a quelque chose à DIRE")
                .isTrue();
        assertThat(textes(panneau)).isEmpty();
    }

    @Test
    @DisplayName("#1417 : un fil existe → le panneau s'ouvre et dit QUI parle, dans l'ordre du serveur")
    void fil_affiche_qui_parle() {
        PanneauDiscussion panneau = new PanneauDiscussion();

        panneau.afficher(
                List.of(
                        new MessageObservation(
                                1L,
                                7L,
                                0,
                                "u-validateur",
                                "Médiane basse pour un Eptser, non ?",
                                Instant.parse("2026-07-11T21:04:00Z")),
                        new MessageObservation(2L, 7L, 1, MOI, "Je repasse le son.", null)),
                MOI,
                PEUT_ECRIRE);

        assertThat(panneau.racine().isVisible()).isTrue();
        assertThat(textes(panneau))
                .as("l'ordre du serveur ($push) est l'ordre chronologique : il est conservé tel quel")
                .containsSubsequence("Médiane basse pour un Eptser, non ?", "Je repasse le son.");
        assertThat(textes(panneau))
                .as("savoir qui parle est la moitié de l'information dans une discussion")
                .anyMatch(texte -> texte.startsWith("Le validateur"))
                .anyMatch(texte -> texte.startsWith("Vous"));
    }

    @Test
    @DisplayName("#1417 : changer de ligne pour une détection sans fil vide le panneau — pas de fil"
            + " fantôme de la ligne précédente")
    void changer_de_ligne_vide_le_panneau() {
        PanneauDiscussion panneau = new PanneauDiscussion();
        panneau.afficher(List.of(new MessageObservation(1L, 7L, 0, MOI, "Vu.", null)), MOI, PEUT_ECRIRE);
        assertThat(textes(panneau)).isNotEmpty();

        panneau.afficher(List.of(), MOI, PAS_SUR_LA_PLATEFORME);

        assertThat(textes(panneau))
                .as("le fil précédent est effacé : afficher la discussion d'une AUTRE détection serait pire"
                        + " que de n'en afficher aucune")
                .isEmpty();
        assertThat(panneau.racine().isVisible()).isFalse();
    }

    @Test
    @DisplayName("#1418 : l'envoi coupé → le fil reste LISIBLE, mais la saisie est désactivée : un champ"
            + " qui ne mènerait à rien serait pire qu'un champ absent")
    void saisie_desactivee_quand_ecriture_impossible() {
        PanneauDiscussion panneau = new PanneauDiscussion();

        panneau.afficher(
                List.of(new MessageObservation(1L, 7L, 0, "u-validateur", "C'est Pipnat.", null)),
                MOI,
                Optional.of("L'envoi de messages au validateur est désactivé."));

        assertThat(panneau.racine().isVisible())
                .as("lire et répondre sont deux fonctionnalités distinctes : couper l'une laisse l'autre")
                .isTrue();
        assertThat(panneau.saisie().isDisabled())
                .as("on ne peut pas répondre, et l'enveloppe du bouton en dit la raison (#789)")
                .isTrue();
    }

    /// Tous les textes affichés dans le fil (entêtes et corps de messages confondus).
    private static List<String> textes(PanneauDiscussion panneau) {
        ScrollPane cadre = (ScrollPane) panneau.racine().getChildren().get(1);
        VBox messages = (VBox) cadre.getContent();
        return messages.getChildren().stream()
                .flatMap(bulle -> ((VBox) bulle).getChildren().stream())
                .map(noeud -> ((Label) noeud).getText())
                .toList();
    }
}
