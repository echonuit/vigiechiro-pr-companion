package fr.univ_amu.iut.recette;

import fr.univ_amu.iut.commun.view.BoutonsDeDialogue;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.commun.view.NotificationDialogue;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

/// La question de confirmation et le compte rendu **de la production**, à l'image d'un clip, et
/// auxquels le scénario répond par un clic comme le ferait l'utilisateur.
///
/// Un scénario qui répond par une lambda joue le bon parcours et ne le montre pas : la question et
/// le compte rendu existent dans des listes, pas à l'écran.
///
/// Les deux dialogues sont construits par les fabriques de la production, même type, même
/// habillage, même texte, puis ouverts en `show()`. Le geste **attend la réponse** dans une boucle
/// d'événements imbriquée, ce que `showAndWait` fait en production : rien n'est écrit avant le clic
/// sur « Confirmer », et le compte rendu ne paraît qu'après. Ce qui se voit est juste, l'ordre
/// aussi. Ce qui ne se voit pas : le dialogue n'est pas modal pour sa fenêtre.
public final class DialoguesALImage {

    private static final long APPARITION_MS = 10_000L;

    private final Supplier<Window> fenetre;
    private final List<Alert> ouverts = new CopyOnWriteArrayList<>();
    private final List<String> questions = new CopyOnWriteArrayList<>();
    private final List<String> comptesRendus = new CopyOnWriteArrayList<>();

    /// @param fenetre la fenêtre du banc, propriétaire des dialogues : ils s'y centrent
    public DialoguesALImage(Supplier<Window> fenetre) {
        this.fenetre = fenetre;
    }

    /// Le confirmateur à brancher sur l'écran : il montre la question et attend le clic.
    public Confirmateur question() {
        return message -> {
            questions.add(message);
            Alert dialogue = new ConfirmationNavigation().dialogue(message);
            return attendreLaReponse(dialogue) == BoutonsDeDialogue.CONFIRMER;
        };
    }

    /// Le notificateur à brancher sur l'écran : il montre le compte rendu et attend qu'on le ferme.
    public Notificateur compteRendu() {
        return new Notificateur() {
            @Override
            public void notifier(NiveauNotification niveau, String entete, String message) {
                comptesRendus.add(entete + " | " + message);
                attendreLaReponse(NotificationDialogue.sansProprietaire().dialogue(niveau, entete, message));
            }

            @Override
            public void notifier(NiveauNotification niveau, String entete, CompteRenduChiffre compteRendu) {
                notifier(niveau, entete, compteRendu.toString());
            }
        };
    }

    private ButtonType attendreLaReponse(Alert dialogue) {
        dialogue.initOwner(fenetre.get());
        Object cle = new Object();
        dialogue.setOnHidden(evenement -> {
            ouverts.remove(dialogue);
            Platform.exitNestedEventLoop(cle, dialogue.getResult());
        });
        ouverts.add(dialogue);
        dialogue.show();
        return (ButtonType) Platform.enterNestedEventLoop(cle);
    }

    /// Attend qu'un dialogue dont le texte contient `fragment` soit **à l'écran**.
    ///
    /// @param fragment ce que le dialogue attendu dit
    public void attendre(String fragment) {
        Attente.queSurLeFil(
                () -> leDialogueQuiDit(fragment).isPresent(),
                () -> "un dialogue qui dit « " + fragment + " » est à l'écran ; ouverts : " + textesOuverts(),
                APPARITION_MS);
    }

    /// Clique `bouton` dans le dialogue qui dit `fragment`, pointeur visible, et attend qu'il se ferme.
    ///
    /// @param robot le robot du banc
    /// @param fragment ce que le dialogue visé dit
    /// @param bouton le bouton à cliquer, tel que [BoutonsDeDialogue] le nomme
    public void repondre(FxRobot robot, String fragment, ButtonType bouton) {
        attendre(fragment);
        Alert dialogue = Attente.surLeFil(
                () -> leDialogueQuiDit(fragment).orElseThrow(), "retrouver le dialogue à cliquer", APPARITION_MS);
        Node cible = Attente.surLeFil(
                () -> dialogue.getDialogPane().lookupButton(bouton), "trouver le bouton du dialogue", APPARITION_MS);
        GesteVisible.cliquer(robot, cible);
        WaitForAsyncUtils.waitForFxEvents();
        Attente.queSurLeFil(
                () -> !dialogue.isShowing(),
                "le dialogue « " + fragment + " » se ferme après le clic sur « " + bouton.getText() + " »",
                APPARITION_MS);
    }

    /// Les questions posées depuis le début du cas, dans l'ordre.
    public List<String> questions() {
        return List.copyOf(questions);
    }

    /// Les comptes rendus rendus depuis le début du cas, en-tête et message.
    public List<String> comptesRendus() {
        return List.copyOf(comptesRendus);
    }

    /// Ferme ce qui serait resté ouvert. À appeler en fin de cas : un dialogue laissé là retiendrait
    /// le geste qui l'attend, et le cas suivant hériterait d'une fenêtre qui n'est pas la sienne.
    public void toutFermer() {
        Platform.runLater(() -> new ArrayList<>(ouverts).forEach(Alert::close));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Optional<Alert> leDialogueQuiDit(String fragment) {
        return ouverts.stream()
                .filter(Alert::isShowing)
                .filter(dialogue -> texte(dialogue).contains(fragment))
                .findFirst();
    }

    private List<String> textesOuverts() {
        return ouverts.stream().map(DialoguesALImage::texte).toList();
    }

    private static String texte(Alert dialogue) {
        return String.valueOf(dialogue.getHeaderText()) + " | " + dialogue.getContentText();
    }
}
