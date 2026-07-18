package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.viewmodel.ReactivationModaleViewModel;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la modale **« Réactiver ce passage »** (`ReactivationModale.fxml`, #1780).
///
/// La réactivation (réseau + base) part sur l'[ExecuteurTache], hors du fil JavaFX ; la modale suit ses
/// **deux phases** sur deux barres - la régénération / le rebranchement des séquences, puis l'acquisition
/// de l'ancrage. La barre d'ancrage n'apparaît que quand cette phase démarre : avant #1780, une barre
/// unique restait figée à 100 % pendant l'ancrage silencieux, et « Annuler » y semblait défaire tout le
/// travail déjà fait.
///
/// À la fin, le compte rendu (honnête, lacunes comprises) s'affiche **dans** la modale ; à la fermeture -
/// bouton « Fermer », croix ou Échap - l'écran appelant se recharge ([#rafraichirSiReactive]), car l'audio
/// a pu revenir et le passage redevenir écoutable.
public class ReactivationModaleController {

    private final ReactivationModaleViewModel viewModel;
    private final ExecuteurTache executeur;

    /// Vrai pendant l'opération : neutralise « Fermer » et fait apparaître « Annuler ».
    private final SimpleBooleanProperty operationEnCours = new SimpleBooleanProperty(false);

    /// Vrai dès que la phase d'ancrage émet son premier point : révèle la seconde barre. Une réactivation
    /// ordinaire (sans ancrage) ne l'allume jamais.
    private final SimpleBooleanProperty ancrageDemarre = new SimpleBooleanProperty(false);

    /// L'étape courante, en clair, au-dessus des barres.
    private final SimpleStringProperty etape = new SimpleStringProperty("");

    /// Jeton de l'opération en cours, câblé sur « Annuler » (#1252). Null hors opération.
    private JetonAnnulation jetonCourant;

    /// Rafraîchissement de l'écran appelant, joué à la fermeture **si** une réactivation s'est conclue.
    private Runnable apresSucces = () -> {};

    @FXML
    private VBox racine;

    @FXML
    private Label lblEtape;

    @FXML
    private HBox zoneRegeneration;

    @FXML
    private ProgressBar barreRegeneration;

    @FXML
    private Label lblRegeneration;

    @FXML
    private HBox zoneAncrage;

    @FXML
    private ProgressBar barreAncrage;

    @FXML
    private Label lblAncrage;

    @FXML
    private Label lblErreur;

    @FXML
    private Label lblCompteRendu;

    @FXML
    private Button boutonAnnuler;

    @FXML
    private Button boutonFermer;

    @Inject
    public ReactivationModaleController(ReactivationModaleViewModel viewModel, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    @FXML
    private void initialize() {
        lblEtape.textProperty().bind(etape);
        lblEtape.visibleProperty().bind(etape.isNotEmpty());
        lblEtape.managedProperty().bind(etape.isNotEmpty());

        barreRegeneration
                .progressProperty()
                .bind(viewModel.progressionRegeneration().fractionProperty());
        lblRegeneration.textProperty().bind(viewModel.progressionRegeneration().messageProperty());
        // La phase disque est visible dès le lancement et le reste après (barre pleine + compte rendu).
        BooleanBinding regenerationVisible = operationEnCours.or(viewModel.reactiveProperty());
        zoneRegeneration.visibleProperty().bind(regenerationVisible);
        zoneRegeneration.managedProperty().bind(regenerationVisible);

        barreAncrage.progressProperty().bind(viewModel.progressionAncrage().fractionProperty());
        lblAncrage.textProperty().bind(viewModel.progressionAncrage().messageProperty());
        // La phase réseau n'existe que sur un passage reconstruit : la barre n'apparaît qu'à son démarrage.
        zoneAncrage.visibleProperty().bind(ancrageDemarre);
        zoneAncrage.managedProperty().bind(ancrageDemarre);

        lblErreur.textProperty().bind(viewModel.erreurProperty());
        lblErreur.visibleProperty().bind(viewModel.erreurProperty().isNotEmpty());
        lblErreur.managedProperty().bind(viewModel.erreurProperty().isNotEmpty());
        lblCompteRendu.textProperty().bind(viewModel.compteRenduProperty());
        lblCompteRendu.visibleProperty().bind(viewModel.compteRenduProperty().isNotEmpty());
        lblCompteRendu.managedProperty().bind(viewModel.compteRenduProperty().isNotEmpty());
        // La modale est dimensionnée sur le contenu visible à l'ouverture : une seule barre. Ce qui paraît
        // ensuite la fait grandir - la barre d'ancrage poussait les boutons hors de la fenêtre, et le compte
        // rendu ses dernières lignes sous la ligne de flottaison (cf. reconstruction #1534). La fenêtre suit
        // désormais les DEUX révélations, par le patron commun.
        Modales.suivreLaCroissance(racine, ancrageDemarre, viewModel.compteRenduProperty());

        boutonAnnuler.visibleProperty().bind(operationEnCours);
        boutonAnnuler.managedProperty().bind(operationEnCours);
        boutonFermer.disableProperty().bind(operationEnCours);
    }

    /// Lance la réactivation dès l'ouverture (appelé par [NavigationPassage] après le chargement du FXML).
    /// `travail` reçoit les deux relais de progression (régénération, ancrage) et le jeton, et rend le
    /// rapport ; il s'exécute **hors du fil JavaFX**. `rafraichirLAppelant` recharge M-Passage à la fermeture.
    public void demarrer(Travail travail, Runnable rafraichirLAppelant) {
        this.apresSucces = Objects.requireNonNull(rafraichirLAppelant, "rafraichirLAppelant");
        lancer(Objects.requireNonNull(travail, "travail"));
    }

    private void lancer(Travail travail) {
        operationEnCours.set(true);
        etape.set("Étape : régénération des séquences");
        viewModel.progressionRegeneration().demarrer("Régénération…");
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        Consumer<Progression> progresRegeneration = executeur.relaisProgression(
                point -> viewModel.progressionRegeneration().appliquer(point));
        Consumer<Progression> progresAncrage = executeur.relaisProgression(point -> {
            if (!ancrageDemarre.get()) {
                ancrageDemarre.set(true);
                etape.set("Étape : ancrage réseau");
                viewModel.progressionAncrage().demarrer(point.libelle());
            }
            viewModel.progressionAncrage().appliquer(point);
        });
        executeur.executer(
                () -> travail.executer(progresRegeneration, progresAncrage, jeton),
                rapport -> {
                    operationEnCours.set(false);
                    etape.set("Terminé.");
                    viewModel.restituer(rapport);
                },
                () -> {
                    operationEnCours.set(false);
                    etape.set("Annulée.");
                    viewModel.signalerAnnulation();
                },
                erreur -> {
                    operationEnCours.set(false);
                    etape.set("Interrompue.");
                    viewModel.signalerErreur(erreur);
                });
    }

    /// « Annuler » : demande l'arrêt de l'opération en cours (#1252). Le travail hors fil s'arrête au
    /// prochain point de contrôle ; rien n'est défait (la réactivation ajoute de l'audio, elle n'en supprime pas).
    @FXML
    private void annuler() {
        if (jetonCourant != null) {
            jetonCourant.annuler();
        }
    }

    /// Ferme la modale. Le rafraîchissement de l'appelant est branché sur la **fermeture** de la fenêtre
    /// ([#rafraichirSiReactive], via `setOnHidden`), pour jouer quelle que soit la façon de fermer.
    @FXML
    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }

    /// Rafraîchit l'écran appelant **si** une réactivation s'est conclue : M-Passage se recharge (volumes,
    /// boutons), l'audio ayant pu revenir. Branché sur `setOnHidden`, il joue à **toute** fermeture (bouton,
    /// croix, Échap).
    public void rafraichirSiReactive() {
        if (viewModel.reactiveProperty().get()) {
            apresSucces.run();
        }
    }

    /// **Aperçu de documentation** (#1780) : place la modale dans l'état « les deux phases en cours » - la
    /// barre de régénération pleine, la barre d'ancrage à mi-course, l'étape nommée - pour la capture, **sans
    /// lancer de vrai travail**. Réservé aux outils de capture
    /// ([fr.univ_amu.iut.passage.outils.CapturePassage]) : l'application, elle, passe par [#demarrer]. Sur le
    /// fil JavaFX.
    public void apercuPhasesEnCours(
            String etapeLibelle,
            String libelleRegeneration,
            double fractionRegeneration,
            String libelleAncrage,
            double fractionAncrage) {
        operationEnCours.set(true);
        etape.set(etapeLibelle);
        viewModel.progressionRegeneration().demarrer(libelleRegeneration);
        viewModel.progressionRegeneration().appliquer(new Progression(libelleRegeneration, fractionRegeneration));
        ancrageDemarre.set(true);
        viewModel.progressionAncrage().demarrer(libelleAncrage);
        viewModel.progressionAncrage().appliquer(new Progression(libelleAncrage, fractionAncrage));
    }

    /// Le travail de réactivation, fourni par l'appelant : il reçoit les deux relais de progression
    /// (régénération puis ancrage) et le jeton d'annulation, s'exécute **hors du fil JavaFX**, et rend le
    /// rapport. La modale ne connaît ainsi ni le service ni l'`idPassage` - seulement comment présenter.
    @FunctionalInterface
    public interface Travail {
        RapportReactivation executer(
                Consumer<Progression> progresRegeneration, Consumer<Progression> progresAncrage, JetonAnnulation jeton);
    }
}
