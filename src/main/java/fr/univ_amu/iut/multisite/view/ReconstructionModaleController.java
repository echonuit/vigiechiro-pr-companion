package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.multisite.viewmodel.ReconstructionViewModel;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la modale **« Reconstruire un passage manquant »** (`ReconstructionModale.fxml`, #1396).
///
/// Les deux appels au réseau (lister les participations, en reconstruire une) sont **bloquants** : ils
/// partent sur l'[ExecuteurTache], et la modale se fige le temps du travail plutôt que d'accepter un
/// second clic (`operationEnCours`). Le résultat, succès comme refus, revient **dans la modale** : un
/// point d'écoute inconnu ici ou une analyse non terminée sont des réponses, pas des incidents.
///
/// Après une reconstruction réussie, la fermeture rafraîchit l'écran appelant : la nuit **apparaît** dans
/// la table des passages, ce qui est la preuve visible que la reconstruction a eu lieu.
public class ReconstructionModaleController {

    /// Ce que la colonne « Point d'écoute » dit d'une nuit dont le point n'existe pas encore ici. La
    /// reconstruction est alors refusée : rattacher une nuit au mauvais point serait une donnée fausse.
    private static final String POINT_INCONNU = "Inconnu ici : créez le site et le point";

    private static final String POINT_CONNU = "Connu";

    /// Début de nuit tel qu'on le lit : « 03/07/2026 à 22:00 ».
    private static final DateTimeFormatter FORMAT_NUIT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final ReconstructionViewModel viewModel;
    private final ExecuteurTache executeur;

    /// Vrai pendant un appel réseau : neutralise les deux boutons (pas de double reconstruction).
    private final SimpleBooleanProperty operationEnCours = new SimpleBooleanProperty(false);

    /// Jeton de la reconstruction en cours, câblé sur le bouton « Annuler » (#1252). Null hors opération.
    private JetonAnnulation jetonCourant;

    /// Rafraîchissement de l'écran appelant, exécuté à la fermeture **si** une nuit a été reconstruite.
    private final SimpleObjectProperty<Runnable> apresSucces = new SimpleObjectProperty<>(() -> {});

    @FXML
    private VBox racine;

    @FXML
    private TableView<ParticipationOrpheline> tableOrphelines;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colCarre;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colPoint;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colNuit;

    @FXML
    private TableColumn<ParticipationOrpheline, String> colPointConnu;

    @FXML
    private Label lblVide;

    @FXML
    private Label lblMessage;

    @FXML
    private Label lblErreur;

    @FXML
    private Label lblCompteRendu;

    @FXML
    private Button boutonReconstruire;

    @FXML
    private Button boutonFermer;

    @FXML
    private StackPane enveloppeReconstruire;

    @FXML
    private HBox zoneProgression;

    @FXML
    private ProgressBar barreProgression;

    @FXML
    private Label lblProgression;

    @FXML
    private Button boutonAnnuler;

    /// Import groupé (#1708) : reconstruit **toutes** les nuits manquantes en une passe.
    @FXML
    private Button boutonReconstruireTout;

    /// Barre + libellé de la progression **globale** de l'import groupé (« Nuit X / N »), au-dessus de la
    /// progression de la nuit courante ([#zoneProgression]). Visible seulement pendant un lot.
    @FXML
    private HBox zoneProgressionGlobale;

    @FXML
    private ProgressBar barreProgressionGlobale;

    @FXML
    private Label lblProgressionGlobale;

    /// Vrai pendant un import **groupé** : pilote l'apparition de la barre globale (une reconstruction unique
    /// n'a pas de niveau « lot »).
    private final SimpleBooleanProperty lotEnCours = new SimpleBooleanProperty(false);

    @Inject
    public ReconstructionModaleController(ReconstructionViewModel viewModel, ExecuteurTache executeur) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    @FXML
    private void initialize() {
        TableDonnees.uniformiser(tableOrphelines);
        colCarre.setCellValueFactory(cellule -> texte(cellule.getValue().numeroCarre()));
        colPoint.setCellValueFactory(cellule -> texte(cellule.getValue().codePoint()));
        colNuit.setCellValueFactory(
                cellule -> texte(nuitLisible(cellule.getValue().dateDebut())));
        colPointConnu.setCellValueFactory(
                cellule -> texte(cellule.getValue().pointLocalConnu() ? POINT_CONNU : POINT_INCONNU));
        tableOrphelines.setItems(viewModel.orphelines());

        lblMessage.textProperty().bind(viewModel.messageProperty());
        lblMessage.visibleProperty().bind(viewModel.messageProperty().isNotEmpty());
        lblMessage.managedProperty().bind(viewModel.messageProperty().isNotEmpty());
        lblErreur.textProperty().bind(viewModel.erreurProperty());
        lblErreur.visibleProperty().bind(viewModel.erreurProperty().isNotEmpty());
        lblErreur.managedProperty().bind(viewModel.erreurProperty().isNotEmpty());
        lblCompteRendu.textProperty().bind(viewModel.compteRenduProperty());
        lblCompteRendu.visibleProperty().bind(viewModel.compteRenduProperty().isNotEmpty());
        lblCompteRendu.managedProperty().bind(viewModel.compteRenduProperty().isNotEmpty());
        // Le compte rendu de fin (avec ses lacunes) est plus haut que la modale dimensionnée pour la
        // table : comme il apparaît APRÈS le premier `sizeToScene` (à l'ouverture), ses dernières lignes
        // passaient sous la ligne de flottaison (#1534). On agrandit la fenêtre quand il paraît.
        viewModel.compteRenduProperty().addListener((observable, avant, apres) -> {
            if (apres != null && !apres.isBlank()) {
                Platform.runLater(this::ajusterHauteurModale);
            }
        });

        // Le bouton ne s'ouvre que sur une nuit choisie DONT le point est connu ici, et l'enveloppe dit
        // laquelle des deux conditions manque (#789) plutôt que de laisser un bouton gris sans raison.
        BooleanBinding reconstructible = Bindings.createBooleanBinding(
                () -> {
                    ParticipationOrpheline choisie =
                            tableOrphelines.getSelectionModel().getSelectedItem();
                    return choisie != null && choisie.pointLocalConnu();
                },
                tableOrphelines.getSelectionModel().selectedItemProperty());
        boutonReconstruire.disableProperty().bind(reconstructible.not().or(operationEnCours));
        boutonFermer.disableProperty().bind(operationEnCours);
        IndicateurBlocage.expliquer(
                enveloppeReconstruire,
                Bindings.when(reconstructible)
                        .then("Rapatrie cette nuit en passage archivé : observations comprises, sans audio.")
                        .otherwise(motifDeBlocage()));

        // Barre + libellé de progression (socle ProgressionOperation, avec ETA). La zone n'a de sens que
        // pendant l'opération et après un succès (barre à 100 %, « Terminé. ») : masquée sinon.
        barreProgression.progressProperty().bind(viewModel.progression().fractionProperty());
        lblProgression.textProperty().bind(viewModel.progression().messageProperty());
        BooleanBinding progressionVisible = operationEnCours.or(viewModel.reconstruitProperty());
        zoneProgression.visibleProperty().bind(progressionVisible);
        zoneProgression.managedProperty().bind(progressionVisible);
        // « Annuler » n'apparaît que pendant l'opération.
        boutonAnnuler.visibleProperty().bind(operationEnCours);
        boutonAnnuler.managedProperty().bind(operationEnCours);

        // Import groupé (#1708) : barre GLOBALE (« Nuit X / N ») au-dessus de celle de la nuit courante,
        // visible seulement pendant un lot. Le bouton « Reconstruire tout » compte les nuits listées et se
        // grise s'il n'y en a aucune (ou pendant une opération en cours).
        barreProgressionGlobale
                .progressProperty()
                .bind(viewModel.progressionGlobale().fractionProperty());
        lblProgressionGlobale.textProperty().bind(viewModel.progressionGlobale().messageProperty());
        zoneProgressionGlobale.visibleProperty().bind(lotEnCours);
        zoneProgressionGlobale.managedProperty().bind(lotEnCours);
        boutonReconstruireTout
                .textProperty()
                .bind(Bindings.concat("Reconstruire tout (", Bindings.size(viewModel.orphelines()), ")"));
        boutonReconstruireTout
                .disableProperty()
                .bind(Bindings.isEmpty(viewModel.orphelines()).or(operationEnCours));

        charger();
    }

    /// Motif affiché quand le bouton est bloqué : dit **quelle** condition manque, et comment la lever.
    private StringBinding motifDeBlocage() {
        return Bindings.createStringBinding(
                () -> tableOrphelines.getSelectionModel().getSelectedItem() == null
                        ? "Choisissez la nuit à reconstruire dans la liste."
                        : "Le point d'écoute de cette nuit n'existe pas sur cette machine. Créez d'abord le"
                                + " site et le point (Mes sites), puis revenez : rattacher une nuit au mauvais"
                                + " point serait une donnée fausse.",
                tableOrphelines.getSelectionModel().selectedItemProperty());
    }

    /// Lecture des participations (réseau) hors du fil JavaFX ; l'échec (hors connexion, plateforme
    /// injoignable) rejoint le message de la modale au lieu de disparaître dans le fil de fond.
    private void charger() {
        operationEnCours.set(true);
        executeur.executer(
                viewModel::charger,
                chargees -> {
                    operationEnCours.set(false);
                    lblVide.setText("Aucune nuit manquante.");
                    viewModel.appliquer(chargees);
                },
                erreur -> {
                    operationEnCours.set(false);
                    lblVide.setText("Liste indisponible.");
                    viewModel.signalerErreur(erreur);
                });
    }

    @FXML
    private void reconstruire() {
        ParticipationOrpheline choisie = tableOrphelines.getSelectionModel().getSelectedItem();
        if (choisie == null) {
            return;
        }
        operationEnCours.set(true);
        viewModel.progression().demarrer("Reconstruction en cours…");
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        Consumer<Progression> progres =
                executeur.relaisProgression(point -> viewModel.progression().appliquer(point));
        executeur.executer(
                () -> viewModel.reconstruire(choisie, progres, jeton),
                rapport -> {
                    operationEnCours.set(false);
                    viewModel.restituer(choisie, rapport);
                },
                () -> {
                    operationEnCours.set(false);
                    viewModel.progression().reinitialiser();
                    viewModel.signalerAnnulation();
                },
                erreur -> {
                    operationEnCours.set(false);
                    viewModel.progression().reinitialiser();
                    viewModel.signalerErreur(erreur);
                });
    }

    /// « Reconstruire tout » (#1708) : hydrate **toutes** les nuits manquantes en une passe, hors du fil
    /// JavaFX. Deux barres suivent l'avancée - la **globale** (« Nuit X / N ») et celle de la **nuit
    /// courante** - et « Annuler » interrompt le lot proprement (la nuit en cours est compensée). Au retour,
    /// on recharge la liste : les nuits reconstruites disparaissent, les ignorées (point inconnu, analyse non
    /// terminée) restent.
    @FXML
    private void reconstruireTout() {
        List<ParticipationOrpheline> aTraiter = List.copyOf(viewModel.orphelines());
        if (aTraiter.isEmpty()) {
            return;
        }
        operationEnCours.set(true);
        lotEnCours.set(true);
        viewModel.progressionGlobale().demarrer("Import groupé en cours…");
        viewModel.progression().demarrer("Préparation…");
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        Consumer<Progression> progresGlobal = executeur.relaisProgression(
                point -> viewModel.progressionGlobale().appliquer(point));
        Consumer<Progression> progresNuit =
                executeur.relaisProgression(point -> viewModel.progression().appliquer(point));
        executeur.executer(
                () -> viewModel.reconstruireTout(aTraiter, progresGlobal, progresNuit, jeton),
                bilan -> {
                    operationEnCours.set(false);
                    lotEnCours.set(false);
                    viewModel.restituerLot(bilan);
                    charger(); // recharge : les reconstruites disparaissent, les ignorées restent
                },
                () -> {
                    operationEnCours.set(false);
                    lotEnCours.set(false);
                    viewModel.progression().reinitialiser();
                    viewModel.progressionGlobale().reinitialiser();
                    viewModel.signalerAnnulation();
                },
                erreur -> {
                    operationEnCours.set(false);
                    lotEnCours.set(false);
                    viewModel.progression().reinitialiser();
                    viewModel.progressionGlobale().reinitialiser();
                    viewModel.signalerErreur(erreur);
                });
    }

    /// **Aperçu de documentation** (#1708) : place la modale dans l'état « import groupé en cours » - les
    /// **deux** barres de progression (le **lot** « Nuit X / N » et la **nuit courante ») visibles, et
    /// « Annuler » offert - pour la capture, **sans lancer de vrai lot**. Réservé aux outils de capture
    /// (`fr.univ_amu.iut.multisite.outils.CaptureMultisite`) : l'application, elle, passe par
    /// [#reconstruireTout]. Sur le fil JavaFX.
    public void apercuImportGroupeEnCours(
            String libelleLot, double fractionLot, String libelleNuit, double fractionNuit) {
        operationEnCours.set(true);
        lotEnCours.set(true);
        viewModel.progressionGlobale().demarrer(libelleLot);
        viewModel.progressionGlobale().appliquer(new Progression(libelleLot, fractionLot));
        viewModel.progression().demarrer(libelleNuit);
        viewModel.progression().appliquer(new Progression(libelleNuit, fractionNuit));
    }

    /// « Annuler » : demande l'arrêt de la reconstruction en cours (#1252). Le travail hors fil s'arrête au
    /// prochain point de contrôle et la compensation défait ce qui a déjà été écrit - aucun passage partiel.
    @FXML
    private void annuler() {
        if (jetonCourant != null) {
            jetonCourant.annuler();
        }
    }

    /// Agrandit la modale pour afficher le compte rendu en entier (#1534) : il paraît après le premier
    /// dimensionnement, si bien que ses dernières lignes - les lacunes - étaient coupées. Sur le fil
    /// JavaFX, une fois la mise en page du texte calculée.
    private void ajusterHauteurModale() {
        if (racine.getScene() != null && racine.getScene().getWindow() instanceof Stage modale) {
            modale.sizeToScene();
        }
    }

    /// Ferme la modale. Le rafraîchissement de l'écran appelant n'est **plus** fait ici : il est branché sur
    /// la **fermeture** de la fenêtre ([#rafraichirSiReconstruit], via `setOnHidden`), pour se déclencher
    /// quelle que soit la façon de fermer - bouton « Fermer », croix, ou Échap (#1647).
    @FXML
    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }

    /// Rafraîchit l'écran appelant **si** une nuit a été reconstruite : la table des passages se recharge et
    /// la nuit rapatriée y apparaît. Branché sur `setOnHidden` de la modale ([NavigationMultisite]), il joue
    /// donc à **toute** fermeture, et non seulement au bouton « Fermer » (#1647 : sinon une fermeture par la
    /// croix laissait la table périmée).
    public void rafraichirSiReconstruit() {
        if (viewModel.reconstruitProperty().get()) {
            apresSucces.get().run();
        }
    }

    /// Appelé par [NavigationMultisite] juste après le chargement du FXML.
    public void demarrer(Runnable rafraichirLAppelant) {
        apresSucces.set(Objects.requireNonNull(rafraichirLAppelant, "rafraichirLAppelant"));
    }

    private static ObservableValue<String> texte(String valeur) {
        return new SimpleObjectProperty<>(valeur == null ? "" : valeur);
    }

    /// La plateforme rend un horodatage ISO (`2026-07-03T22:00:00+02:00`) : illisible dans un tableau.
    /// On le rend en date et heure du début de nuit ; si le format surprend, on affiche la valeur brute
    /// plutôt que rien.
    private static String nuitLisible(String horodatage) {
        if (horodatage == null || horodatage.isBlank()) {
            return "";
        }
        try {
            return OffsetDateTime.parse(horodatage).format(FORMAT_NUIT);
        } catch (DateTimeParseException surprise) {
            return horodatage;
        }
    }
}
