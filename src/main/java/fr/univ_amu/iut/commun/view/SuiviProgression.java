package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/// Socle des **opérations longues à suivi visuel** (#1597) : tout ce qui ne dépend pas de l'endroit où
/// l'avancement paraît.
///
/// Il porte le **contenu** (titre, barre déterminée, libellé d'étape, bouton « Annuler ») et
/// l'**orchestration** (démarrage du suivi, exécution hors fil JavaFX, issue unique). Ses deux
/// implémentations ne se distinguent que par un geste :
///
/// - [DialogueProgression] ouvre une **fenêtre** au-dessus du propriétaire ;
/// - [PanneauProgression] **greffe** le contenu dans une zone que l'appelant fournit.
///
/// **Pourquoi deux** (#2642) : une opération lancée depuis une modale doit s'y afficher, sinon
/// l'utilisateur voit deux fenêtres pour un seul geste, alors que la modale hôte a déjà sa zone de
/// statut. Une fenêtre reste juste quand il n'y a **pas** de modale hôte - un bouton de barre d'outils,
/// par exemple. Le choix appartient donc à l'appelant, qui seul sait d'où il part.
public abstract class SuiviProgression implements SuiviOperation {

    private final ExecuteurTache executeur;

    protected SuiviProgression(ExecuteurTache executeur) {
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    /// Met le `contenu` à l'écran et rend de quoi l'en **retirer**. Le `jeton` est fourni pour que la
    /// présentation puisse traiter sa propre fermeture comme un renoncement (fermer la fenêtre = annuler).
    protected abstract Runnable presenter(Window proprietaire, String titre, VBox contenu, JetonAnnulation jeton);

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

        Runnable retrait = presenter(proprietaire, titre, contenu(titre, progression, jeton), jeton);

        executer(retrait, jeton, progression, travail, succes, annule, echec);
    }

    /// Le **contenu** du suivi, **sans son support** : titre, barre liée à la progression, libellé d'étape
    /// et bouton « Annuler ».
    ///
    /// Séparé de [#lancer] pour la même raison que `ConfirmationNavigation#dialogue` (#1468) : une capture
    /// de documentation doit pouvoir montrer **ce contenu-ci**, celui que l'utilisateur verra, sans ouvrir
    /// de fenêtre ni lancer de travail. Reconstruire un fac-similé dans l'outil de capture n'engagerait
    /// personne - c'est ainsi que des dialogues documentés ont dérivé du produit.
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
        annuler.getStyleClass().add("bouton-secondaire");
        annuler.setOnAction(evenement -> jeton.annuler());
        HBox actions = new HBox(annuler);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox contenu = new VBox(12, lblTitre, barre, lblMessage, actions);
        contenu.setPadding(new Insets(20));
        contenu.setPrefWidth(420);
        return contenu;
    }

    /// Le suivi **figé sur une étape**, pour une capture de documentation (#1865) : le vrai contenu, la
    /// vraie barre, le vrai bouton, à un instant choisi. Aucun support, aucun travail, aucun réseau.
    ///
    /// Il **délègue** à [#contenu] plutôt que d'assembler un fac-similé : c'est ce qui garantit que
    /// l'image reste juste quand le suivi change. « Annuler » y est présent et cliquable, mais son jeton
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

    /// Orchestration **sans support** (testable) : démarre le suivi, exécute le `travail` hors fil avec le
    /// relais de progression et le jeton, puis **retire** le contenu avant de conclure. Exactement une
    /// issue est appliquée ; une **annulation** retire puis appelle `annule`, sans passer par `succes` ni
    /// `echec`.
    <T> void executer(
            Runnable retrait,
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
                    retrait.run();
                    succes.accept(resultat);
                },
                () -> {
                    retrait.run();
                    annule.run();
                },
                erreur -> {
                    retrait.run();
                    echec.accept(erreur);
                });
    }
}
