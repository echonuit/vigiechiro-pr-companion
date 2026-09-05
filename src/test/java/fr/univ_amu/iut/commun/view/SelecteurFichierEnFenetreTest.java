package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.recette.Attente;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Ce que ces cas éprouvent, et pourquoi ils existent alors que le dépôt ne teste AUCUNE de ses
/// fenêtres bloquantes.
///
/// [ChoixSauvegardeJavaFx] n'a pas de test, et sa javadoc dit pourquoi : c'est là que vit le
/// `showAndWait()`, et en test on le remplace par un double. La même chose valait pour tout dialogue
/// jusqu'ici, parce que le dialogue **était natif** : rien dans le processus ne pouvait le voir.
///
/// Ce dispositif-ci est une fenêtre de l'application. TestFX la voit, la trouve et clique dedans.
/// C'est exactement l'apport du changement `selecteur-de-repli`, et le vérifier ici est la première
/// occasion que le dépôt ait eue de le faire.
///
/// **Comment on éprouve un appel bloquant.** `showAndWait()` ouvre une boucle d'événements
/// **imbriquée** sur le fil FX : le fil n'est pas figé, il traite les événements suivants. On lance
/// donc l'appel par `runLater`, on attend que la fenêtre paraisse, et le robot clique. C'est le
/// contraire d'un contournement : c'est ce que fait un utilisateur.
@ExtendWith(ApplicationExtension.class)
class SelecteurFichierEnFenetreTest {

    @TempDir
    Path bac;

    private Stage principale;

    @Start
    void demarrer(Stage stage) {
        this.principale = stage;
        // `FenetreAjustable` et non une scène dimensionnée posée en dur (ADR 4475). Le Stage du
        // harnais TestFX est PARTAGÉ par toutes les classes d'un même fork : un dimensionnement
        // explicite le fige, et la classe suivante trouve des nœuds « invisibles » très loin de la
        // cause. Le défaut est revenu quatre fois dans ce dépôt.
        FenetreAjustable.poserHabillee(stage, new Label("écran d'accueil"), 400, 300);
        FenetreAjustable.afficher(stage);
    }

    @BeforeEach
    void semer() throws IOException {
        Files.createDirectories(bac.resolve("Archives"));
        Files.writeString(bac.resolve("releve.csv"), "a,b\n");
    }

    private SelecteurFichierEnFenetre selecteur() {
        return new SelecteurFichierEnFenetre(() -> principale);
    }

    /// Lance l'appel bloquant hors du fil du test, et rend de quoi lire son résultat.
    private AtomicReference<Optional<Path>> lancer(java.util.function.Supplier<Optional<Path>> appel) {
        AtomicReference<Optional<Path>> resultat = new AtomicReference<>();
        Platform.runLater(() -> resultat.set(appel.get()));
        Attente.queSurLeFil(() -> dialogue() != null, "le dialogue de désignation paraît");
        return resultat;
    }

    /// La fenêtre du dialogue, ou `null`. Lue SUR LE FIL par [Attente#queSurLeFil] (ADR 5278).
    private Window dialogue() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(f -> f != principale)
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("choisir un dossier rend le dossier, et referme la fenêtre")
    void choisir_un_dossier_rend_le_dossier(FxRobot robot) {
        AtomicReference<Optional<Path>> resultat =
                lancer(() -> selecteur().choisirDossier("Choisir un dossier", Optional.of(bac)));

        robot.clickOn("#" + ContenuDesignation.ID_VALIDER);

        Attente.que(() -> resultat.get() != null, "l'appel bloquant a rendu la main");
        assertThat(resultat.get()).contains(bac);
        Attente.queSurLeFil(() -> dialogue() == null, "la fenêtre s'est refermée");
    }

    @Test
    @DisplayName("renoncer ne rend RIEN, comme le dialogue du système")
    void renoncer_ne_rend_rien(FxRobot robot) {
        // C'est l'exigence de fond de la spec : le dispositif choisi ne change pas ce que le geste
        // rend. Un renoncement rend vide des deux côtés, sans quoi une action se comporterait
        // différemment selon un réglage.
        AtomicReference<Optional<Path>> resultat =
                lancer(() -> selecteur().choisirDossier("Choisir un dossier", Optional.of(bac)));

        robot.clickOn("#" + ContenuDesignation.ID_ANNULER);

        Attente.que(() -> resultat.get() != null, "l'appel bloquant a rendu la main");
        assertThat(resultat.get()).isEmpty();
    }

    @Test
    @DisplayName("UNE seule fenêtre est ouverte à la fois, jamais deux empilées")
    void une_seule_fenetre_a_la_fois(FxRobot robot) {
        // Le socle refuse deux modales empilées pour un seul geste (#2642). Ce cas le constate au
        // moment où le dialogue est ouvert, et non après coup.
        AtomicReference<Optional<Path>> resultat =
                lancer(() -> selecteur().choisirDossier("Choisir un dossier", Optional.of(bac)));

        List<Window> ouvertes = Attente.surLeFil(
                () -> Window.getWindows().stream().filter(Window::isShowing).toList(),
                "compter les fenêtres ouvertes",
                5_000);

        assertThat(ouvertes).hasSize(2);

        robot.clickOn("#" + ContenuDesignation.ID_ANNULER);
        Attente.que(() -> resultat.get() != null, "l'appel bloquant a rendu la main");
    }

    @Test
    @DisplayName("enregistrer rend le dossier joint au nom saisi")
    void enregistrer_rend_le_chemin_complet(FxRobot robot) {
        AtomicReference<Optional<Path>> resultat =
                lancer(() -> selecteur().enregistrerFichier("Enregistrer", "export-2026.csv", FiltreFichier.csv()));

        // Le contrat d'`enregistrerFichier` ne prend AUCUN dossier de départ - c'est vrai du natif
        // aussi - donc le dialogue s'ouvre sur le dossier personnel. On saisit le bac pour ne pas
        // dépendre de la machine, en REMPLAÇANT le texte : `write` ajoute au champ, il ne le vide pas.
        robot.clickOn("#" + ContenuDesignation.ID_CHEMIN)
                .push(KeyCode.CONTROL, KeyCode.A)
                .write(bac.toString())
                .push(KeyCode.ENTER);
        robot.clickOn("#" + ContenuDesignation.ID_VALIDER);

        Attente.que(() -> resultat.get() != null, "l'appel bloquant a rendu la main");
        assertThat(resultat.get()).contains(bac.resolve("export-2026.csv"));
    }
}
