package fr.univ_amu.iut.passage.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.view.VueCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.passage.model.ChoixRebranchement;
import fr.univ_amu.iut.passage.model.CompteRenduChiffreReactivation;
import fr.univ_amu.iut.passage.model.ModeRebranchement;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import fr.univ_amu.iut.passage.viewmodel.ReactivationModaleViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
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

    /// L'**issue** de l'opération, en clair, au-dessus des barres : « Terminé. », « Annulée. »,
    /// « Interrompue. ». Vide pendant l'opération, où les blocs de phase se nomment eux-mêmes.
    private final SimpleStringProperty etape = new SimpleStringProperty("");

    /// Jeton de l'opération en cours, câblé sur « Annuler » (#1252). Null hors opération.
    private JetonAnnulation jetonCourant;

    /// La réponse que le fil de fond attend, quand une question est posée (#2577). `null` hors question.
    private CompletableFuture<ModeRebranchement> reponseAttendue;

    /// Rafraîchissement de l'écran appelant, joué à la fermeture **si** une réactivation s'est conclue.
    private Runnable apresSucces = () -> {};

    @FXML
    private VBox racine;

    @FXML
    private Label lblEtape;

    @FXML
    private VBox zoneRegeneration;

    @FXML
    private ProgressBar barreRegeneration;

    @FXML
    private Label lblRegeneration;

    @FXML
    private VBox zoneAncrage;

    @FXML
    private ProgressBar barreAncrage;

    @FXML
    private Label lblAncrage;

    @FXML
    private Label lblErreur;

    /// Zone de la question « copier ou référencer » (#2577), repliée hors demande.
    @FXML
    private VBox zoneQuestion;

    @FXML
    private Label lblQuestion;

    @FXML
    private VBox zoneCompteRendu;

    @FXML
    private PanneauCompteRendu compteRenduChiffre;

    @FXML
    private Button boutonAnnuler;

    @FXML
    private Button boutonFermer;

    /// Enveloppe non désactivée autour de « Fermer » : c'est elle qui porte le tooltip, un bouton grisé
    /// n'en affichant aucun (#789).
    @FXML
    private StackPane enveloppeFermer;

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
        // Le compte rendu se **construit** : sa forme dépend de ce qu'il y a à dire (ADR 0031), donc il ne
        // peut pas être lié comme un libellé. La modale plafonne ses détails pour rester lisible ; la ligne
        // de commande, elle, les rend tous.
        viewModel.compteRenduProperty().addListener((obs, avant, apres) -> afficherCompteRendu(apres));
        afficherCompteRendu(viewModel.compteRenduProperty().get());
        viewModel.rapportProperty().addListener((obs, avant, apres) -> afficherCompteRenduChiffre(apres));
        afficherCompteRenduChiffre(viewModel.rapportProperty().get());
        // La modale est dimensionnée sur le contenu visible à l'ouverture : une seule barre. Ce qui paraît
        // ensuite la fait grandir - la barre d'ancrage poussait les boutons hors de la fenêtre, et le compte
        // rendu ses dernières lignes sous la ligne de flottaison (cf. reconstruction #1534). La fenêtre suit
        // désormais les TROIS révélations, par le patron commun - la bande chiffrée (#2358) en fait partie,
        // sans quoi elle repousserait à son tour les boutons hors de la fenêtre.
        // #3453, troisième cas : le message d'erreur paraît sur `erreurProperty`, qui n'était ni
        // déclarée ici, ni le moteur d'une zone déclarée. La fenêtre ne suivait donc pas - et c'est le
        // pire moment pour déborder, puisque c'est là qu'on a quelque chose à lire.
        Modales.suivreLaCroissance(
                racine,
                ancrageDemarre,
                viewModel.compteRenduProperty(),
                viewModel.rapportProperty(),
                viewModel.erreurProperty());

        boutonAnnuler.visibleProperty().bind(operationEnCours);
        boutonAnnuler.managedProperty().bind(operationEnCours);
        boutonFermer.disableProperty().bind(operationEnCours);
        // Pendant l'opération, le seul bouton mis en avant de la modale est aussi le seul sur lequel on ne
        // peut pas cliquer. L'enveloppe dit pourquoi, et dit surtout que l'attente a un terme (#789).
        IndicateurBlocage.expliquer(
                enveloppeFermer,
                Bindings.when(operationEnCours)
                        .then("Disponible à la fin de l'opération. « Annuler » l'interrompt sans rien défaire.")
                        .otherwise("Ferme la fenêtre et recharge le passage."));
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
        // Pendant l'opération, la ligne d'étape se tait : les blocs de phase se nomment eux-mêmes, et elle
        // ne faisait que redire le nom de celui qui venait d'apparaître. Elle reprend la parole à la fin,
        // pour l'issue - la seule chose que les barres ne disent pas.
        etape.set("");
        viewModel.progressionRegeneration().demarrer("Régénération…");
        JetonAnnulation jeton = new JetonAnnulation();
        jetonCourant = jeton;
        Consumer<Progression> progresRegeneration = executeur.relaisProgression(
                point -> viewModel.progressionRegeneration().appliquer(point));
        Consumer<Progression> progresAncrage = executeur.relaisProgression(point -> {
            if (!ancrageDemarre.get()) {
                ancrageDemarre.set(true);
                viewModel.progressionAncrage().demarrer(point.libelle());
            }
            viewModel.progressionAncrage().appliquer(point);
        });
        executeur.executer(
                () -> travail.executer(progresRegeneration, progresAncrage, this::demanderLeMode, jeton),
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

    /// La question « copier ou référencer », **posée dans cette modale** au moment où la procédure sait
    /// qu'elle a un objet (#2577).
    ///
    /// Appelée **hors du fil JavaFX** : on revient sur le fil FX pour montrer la question, et on y fait
    /// attendre l'appelant. C'est une attente voulue - la procédure ne peut pas continuer sans la réponse,
    /// et le faire croire en choisissant à sa place est précisément ce qu'on corrige.
    ///
    /// Renoncer pendant la question compte comme une annulation : la réponse est alors sans objet, et on
    /// rend le défaut plutôt que de laisser le fil de fond bloqué à jamais.
    private ModeRebranchement demanderLeMode(Path dossierSource, boolean horsEspaceDeTravail) {
        CompletableFuture<ModeRebranchement> reponse = new CompletableFuture<>();
        reponseAttendue = reponse;
        Platform.runLater(() -> {
            lblQuestion.setText(question(dossierSource, horsEspaceDeTravail));
            zoneQuestion.setVisible(true);
            zoneQuestion.setManaged(true);
        });
        try {
            return reponse.get();
        } catch (InterruptedException interrompu) {
            Thread.currentThread().interrupt();
            return ModeRebranchement.COPIE;
        } catch (ExecutionException echec) {
            return ModeRebranchement.COPIE;
        }
    }

    private static String question(Path dossierSource, boolean horsEspaceDeTravail) {
        String ou = "Des fichiers déjà transformés ont été trouvés dans « " + dossierSource.getFileName() + " ». ";
        return horsEspaceDeTravail
                ? ou
                        + "Ce dossier est en dehors de votre dossier de travail : ces fichiers sont les vôtres."
                        + " Les laisser où ils sont évite un doublon, mais cette nuit ne sera plus écoutable"
                        + " quand ce support sera absent - et le redeviendra dès qu'il reviendra."
                : ou + "Les laisser où ils sont, ou en faire une copie dans votre dossier de travail ?";
    }

    @FXML
    private void laisserEnPlace() {
        repondre(ModeRebranchement.REFERENCE);
    }

    @FXML
    private void copier() {
        repondre(ModeRebranchement.COPIE);
    }

    private void repondre(ModeRebranchement mode) {
        zoneQuestion.setVisible(false);
        zoneQuestion.setManaged(false);
        if (reponseAttendue != null) {
            reponseAttendue.complete(mode);
            reponseAttendue = null;
        }
    }

    /// « Annuler » : demande l'arrêt de l'opération en cours (#1252). Le travail hors fil s'arrête au
    /// prochain point de contrôle ; rien n'est défait (la réactivation ajoute de l'audio, elle n'en supprime pas).
    @FXML
    private void annuler() {
        // Une question en attente doit être libérée AVANT : sans ça, le fil de fond resterait bloqué sur
        // une réponse qui ne viendra jamais, et l'annulation n'arriverait jamais à son point de contrôle.
        repondre(ModeRebranchement.COPIE);
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
    /// barre de régénération pleine, la barre d'ancrage à mi-course - **sans lancer de vrai travail**.
    /// Réservé aux outils de capture ([fr.univ_amu.iut.passage.outils.CapturePassage]) et au **garde-fou de
    /// dimensionnement** : c'est le seul moyen d'obtenir l'état « deux phases, pas encore de compte rendu »,
    /// que l'exécuteur synchrone d'un test traverse trop vite. L'application, elle, passe par [#demarrer].
    /// Sur le fil JavaFX.
    public void apercuPhasesEnCours(
            String libelleRegeneration, double fractionRegeneration, String libelleAncrage, double fractionAncrage) {
        operationEnCours.set(true);
        etape.set("");
        viewModel.progressionRegeneration().demarrer(libelleRegeneration);
        viewModel.progressionRegeneration().appliquer(new Progression(libelleRegeneration, fractionRegeneration));
        ancrageDemarre.set(true);
        viewModel.progressionAncrage().demarrer(libelleAncrage);
        viewModel.progressionAncrage().appliquer(new Progression(libelleAncrage, fractionAncrage));
    }

    /// Nombre de détails montrés par constat avant de résumer. La **modale** en décide : elle doit rester
    /// lisible, là où la sortie de la CLI les rend tous parce qu'elle se filtre (ADR 0031).
    private static final int DETAILS_MONTRES = 5;

    /// Publie le compte rendu **chiffré** (#2358) d'une réactivation aboutie, ou masque la bande.
    ///
    /// Il ne remplace pas le compte rendu textuel : celui-ci reste la surface de l'**annulation**, où il
    /// n'y a rien à ventiler, et reste ce que la commande `reactiver` rend en lignes.
    ///
    /// Le pied ne propose pas d'action : depuis cette modale, la seule suite est de fermer pour revenir
    /// au passage, ce que le bouton de la modale fait déjà. Y ajouter « Ouvrir le passage » proposerait
    /// d'aller là où l'on est.
    private void afficherCompteRenduChiffre(RapportReactivation rapport) {
        boolean aChiffrer = aQuelqueChoseAVentiler(rapport);
        if (aChiffrer) {
            compteRenduChiffre.afficher(CompteRenduChiffreReactivation.de(rapport, List.of()));
        }
        compteRenduChiffre.setVisible(aChiffrer);
        compteRenduChiffre.setManaged(aChiffrer);
        // Le textuel se réévalue ici aussi : les deux propriétés sont publiées l'une après l'autre, et
        // c'est celle qui arrive en dernier qui doit trancher, quel que soit l'ordre.
        afficherCompteRendu(viewModel.compteRenduProperty().get());
    }

    /// Remplace le compte rendu affiché. On reconstruit plutôt qu'on ne met à jour : un compte rendu est
    /// immuable et publié d'un bloc, il n'y a rien à rafraîchir en place.
    /// `true` si le rapport a des **proportions** à montrer, donc si la bande a lieu d'être.
    ///
    /// Un passage **reconstruit** n'en a pas : la réactivation n'a pas eu lieu, l'application connaît le
    /// nom des séquences sans pouvoir les relier aux fichiers. Une barre « 0 sur 30 » y ferait croire à
    /// une tentative qui aurait échoué, là où il n'y a pas eu de tentative. Ce cas garde le compte rendu
    /// textuel, qui l'explique.
    private static boolean aQuelqueChoseAVentiler(RapportReactivation rapport) {
        return rapport != null && rapport.voie() != VoieReactivation.RECONSTRUIT;
    }

    private void afficherCompteRendu(CompteRendu rendu) {
        // La bande chiffrée dit déjà tout d'une réactivation aboutie : laisser le textuel dessous
        // afficherait deux fois les mêmes faits, l'un sous l'autre. Il ne reste que pour ce qu'elle ne
        // couvre pas - l'annulation et le passage reconstruit, où il n'y a rien à ventiler.
        if (aQuelqueChoseAVentiler(viewModel.rapportProperty().get())) {
            zoneCompteRendu.getChildren().clear();
            zoneCompteRendu.setVisible(false);
            zoneCompteRendu.setManaged(false);
            return;
        }
        zoneCompteRendu
                .getChildren()
                .setAll(VueCompteRendu.rendre(rendu, DETAILS_MONTRES).getChildren());
        zoneCompteRendu.getStyleClass().setAll(VueCompteRendu.CLASSE_RACINE);
        zoneCompteRendu.setVisible(!rendu.estVide());
        zoneCompteRendu.setManaged(!rendu.estVide());
    }

    /// **Aperçu de documentation** (#1943) : place la modale dans son état **final**, compte rendu affiché,
    /// à partir d'un vrai [RapportReactivation]. Le texte n'est pas fabriqué ici - il passe par le
    /// ViewModel, donc par la mise en forme réelle (ADR 0025). Sur le fil JavaFX.
    public void apercuCompteRendu(RapportReactivation rapport) {
        operationEnCours.set(false);
        etape.set("");
        viewModel.progressionRegeneration().appliquer(new Progression("Régénération 1815/1815", 1.0));
        viewModel.restituer(rapport);
    }

    /// Le travail de réactivation, fourni par l'appelant : il reçoit les deux relais de progression
    /// (régénération puis ancrage) et le jeton d'annulation, s'exécute **hors du fil JavaFX**, et rend le
    /// rapport. La modale ne connaît ainsi ni le service ni l'`idPassage` - seulement comment présenter.
    @FunctionalInterface
    public interface Travail {
        RapportReactivation executer(
                Consumer<Progression> progresRegeneration,
                Consumer<Progression> progresAncrage,
                ChoixRebranchement choix,
                JetonAnnulation jeton);
    }
}
