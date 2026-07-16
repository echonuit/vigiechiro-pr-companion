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
/// de secondes (réactivation avec ancrage, #1571, et toute opération longue future). C'est le patron que
/// la modale de reconstruction assemblait inline, offert en un point.
public final class DialogueProgression {

    private final ExecuteurTache executeur;

    public DialogueProgression(ExecuteurTache executeur) {
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    /// Ouvre la modale au-dessus de `proprietaire` et lance `travail` (qui reçoit le **relais de
    /// progression** à passer au service et le **jeton d'annulation**). La modale se ferme à la fin ;
    /// `succes` ou `echec` est alors appelé sur le fil JavaFX. Une **annulation** ferme sans rien appeler
    /// (renoncer n'est pas échouer).
    public <T> void lancer(
            Window proprietaire,
            String titre,
            BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
            Consumer<T> succes,
            Consumer<Throwable> echec) {
        Objects.requireNonNull(titre, "titre");
        ProgressionOperation progression = new ProgressionOperation();
        JetonAnnulation jeton = new JetonAnnulation();

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

        Stage modale = new Stage();
        modale.initOwner(proprietaire);
        modale.initModality(Modality.WINDOW_MODAL);
        modale.setTitle(titre);
        modale.setScene(new Scene(contenu));
        // Fermer la fenêtre = renoncer : on demande l'annulation plutôt que de laisser le travail orphelin.
        modale.setOnCloseRequest(evenement -> jeton.annuler());
        modale.show();

        executer(modale::close, jeton, progression, travail, succes, echec);
    }

    /// Orchestration **sans fenêtre** (testable) : démarre le suivi, exécute le `travail` hors fil avec le
    /// relais de progression et le jeton, puis **ferme** (`fermeture`) avant de conclure. Exactement une
    /// issue est appliquée ; une **annulation** ferme sans appeler ni `succes` ni `echec`.
    <T> void executer(
            Runnable fermeture,
            JetonAnnulation jeton,
            ProgressionOperation progression,
            BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
            Consumer<T> succes,
            Consumer<Throwable> echec) {
        progression.demarrer("Démarrage…");
        Consumer<Progression> relais = executeur.relaisProgression(progression::appliquer);
        executeur.executer(
                () -> travail.apply(relais, jeton),
                resultat -> {
                    fermeture.run();
                    succes.accept(resultat);
                },
                fermeture,
                erreur -> {
                    fermeture.run();
                    echec.accept(erreur);
                });
    }
}
