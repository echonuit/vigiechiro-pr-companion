package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Modale d'**opération longue annulable** (#1597) : un titre, une **barre de progression** déterminée,
/// un libellé d'étape (avec ETA) et un bouton « Annuler ». Elle exécute un `travail` **hors du fil
/// JavaFX** via l'[ExecuteurTache], relaie sa progression sur la barre, honore l'annulation par le jeton,
/// se **ferme** à la fin, puis remet le résultat (`succes`) ou l'erreur (`echec`) sur le fil JavaFX.
///
/// Socle **réutilisable** : là où [IndicateurOccupation] pose un voile opaque (« ça travaille »), cette
/// modale **dit où on en est** et **laisse renoncer** — ce qu'attend une opération de plusieurs dizaines
/// de secondes (réactivation avec ancrage, #1571, import des observations, #1622, et toute opération
/// longue future). C'est le patron que la modale de reconstruction assemblait inline, offert en un point.
/// Implémente [SuiviOperation], que les gestes testent avec un double synchrone sans fenêtre.
public final class DialogueProgression implements SuiviOperation {

    private final ExecuteurTache executeur;

    public DialogueProgression(ExecuteurTache executeur) {
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    /// Ouvre la modale au-dessus de `proprietaire` et lance `travail` (qui reçoit le **relais de
    /// progression** à passer au service et le **jeton d'annulation**). La modale se ferme à la fin ;
    /// `succes`, `annule` ou `echec` est alors appelé sur le fil JavaFX. Une **annulation** ferme puis
    /// appelle `annule` (pour effacer un état « en cours »), sans passer par `echec` — renoncer n'est pas
    /// échouer.
    @Override
    public <T> void lancer(
            Window proprietaire,
            String titre,
            BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
            Consumer<T> succes,
            Runnable annule,
            Consumer<Throwable> echec) {
        Objects.requireNonNull(titre, "titre");
        ProgressionOperation progression = new ProgressionOperation();
        JetonAnnulation jeton = new JetonAnnulation();

        VBox contenu = contenu(titre, progression, jeton);

        Stage modale = new Stage();
        modale.initOwner(proprietaire);
        modale.initModality(Modality.WINDOW_MODAL);
        modale.setTitle(titre);
        modale.setScene(new Scene(contenu));
        // Fermer la fenêtre = renoncer : on demande l'annulation plutôt que de laisser le travail orphelin.
        modale.setOnCloseRequest(evenement -> jeton.annuler());
        modale.show();

        executer(modale::close, jeton, progression, travail, succes, annule, echec);
    }

    /// Le **contenu** de la modale, **sans la fenêtre** : titre, barre liée à la progression, libellé
    /// d'étape et bouton « Annuler ».
    ///
    /// Séparé de [#lancer] pour la même raison que [ConfirmationNavigation#dialogue] (#1468) : une capture
    /// de documentation doit pouvoir montrer **ce contenu-ci**, celui que l'utilisateur verra, sans avoir à
    /// ouvrir une fenêtre ni à lancer un travail. Reconstruire un fac-similé dans l'outil de capture
    /// n'engagerait personne - c'est ainsi que des dialogues documentés ont dérivé du produit.
    ///
    /// Le contenu est **inerte** tant que rien n'alimente `progression` : c'est l'appelant qui pose l'état,
    /// que ce soit le travail réel ou une capture qui fige une étape.
    static VBox contenu(String titre, ProgressionOperation progression, JetonAnnulation jeton) {
        Label lblTitre = new Label(titre);
        lblTitre.setStyle("-fx-font-weight: bold;");
        ProgressBar barre = new ProgressBar();
        barre.setMaxWidth(Double.MAX_VALUE);
        barre.progressProperty().bind(progression.fractionProperty());
        Label lblMessage = new Label();
        lblMessage.setWrapText(true);
        lblMessage.textProperty().bind(progression.messageProperty());
        Button annuler = new Button("Annuler");
        annuler.setOnAction(evenement -> jeton.annuler());
        HBox actions = new HBox(annuler);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox contenu = new VBox(12, lblTitre, barre, lblMessage, actions);
        contenu.setPadding(new Insets(20));
        contenu.setPrefWidth(420);
        return contenu;
    }

    /// La modale **figée sur une étape**, pour une capture de documentation (#1865) : le vrai contenu, la
    /// vraie barre, le vrai bouton, à un instant choisi. Aucune fenêtre, aucun travail, aucun réseau.
    ///
    /// Elle **délègue** à [#contenu] plutôt que d'assembler un fac-similé : c'est ce qui garantit que
    /// l'image reste juste quand la modale change. « Annuler » y est présent et cliquable, mais son jeton
    /// n'est relié à rien - un aperçu n'a rien à interrompre.
    ///
    /// Aucune **référence temporelle** n'est posée ([ProgressionOperation#demarrer] n'est pas appelé) :
    /// l'estimation du temps restant s'extrapole du temps écoulé, qui serait ici nul, et l'image
    /// annoncerait « ~0 s restant » à un quart d'avancement. L'aperçu montre donc l'état **avant** qu'une
    /// estimation soit possible - un état réel de l'opération, plutôt qu'une durée inventée.
    public static VBox apercu(String titre, Progression etape) {
        Objects.requireNonNull(etape, "etape");
        ProgressionOperation progression = new ProgressionOperation();
        progression.appliquer(etape);
        return contenu(titre, progression, new JetonAnnulation());
    }

    /// Orchestration **sans fenêtre** (testable) : démarre le suivi, exécute le `travail` hors fil avec le
    /// relais de progression et le jeton, puis **ferme** (`fermeture`) avant de conclure. Exactement une
    /// issue est appliquée ; une **annulation** ferme puis appelle `annule`, sans passer par `succes` ni
    /// `echec`.
    <T> void executer(
            Runnable fermeture,
            JetonAnnulation jeton,
            ProgressionOperation progression,
            BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
            Consumer<T> succes,
            Runnable annule,
            Consumer<Throwable> echec) {
        progression.demarrer("Démarrage…");
        Consumer<Progression> relais = executeur.relaisProgression(progression::appliquer);
        executeur.executer(
                () -> travail.apply(relais, jeton),
                resultat -> {
                    fermeture.run();
                    succes.accept(resultat);
                },
                () -> {
                    fermeture.run();
                    annule.run();
                },
                erreur -> {
                    fermeture.run();
                    echec.accept(erreur);
                });
    }
}
