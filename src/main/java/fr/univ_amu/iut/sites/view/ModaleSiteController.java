package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.LibelleRetour;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.commun.view.ValidationFormulaire;
import fr.univ_amu.iut.commun.viewmodel.EtatConnexion;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.CarreExistantViewModel;
import fr.univ_amu.iut.sites.viewmodel.SiteEditViewModel;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/// Controller de la **modale de déclaration / d'édition d'un site** (`ModaleSite.fxml`, #1431). Jumeau
/// de [ModalePointController] : lie les champs en bidirectionnel au [SiteEditViewModel], reflète son
/// état de présentation (titre, libellé du bouton, activation, message d'erreur, surlignage du carré
/// invalide), se ferme elle-même, et joue le `Runnable` de l'appelant après un enregistrement réussi.
///
/// Elle remplace **deux** `Dialog<T>` bâtis à la main (`MesSitesController` pour créer,
/// `SiteDetailController` pour modifier). Ceux-ci se terminaient par un `showAndWait` : leurs gestes
/// étaient donc **injouables dans un test** - y compris **déclarer un site**, qui est pourtant l'entrée
/// du produit.
public class ModaleSiteController {

    private static final String STYLE_CHAMP_INVALIDE = "champ-invalide";

    private final SiteEditViewModel viewModel;

    /// Action à jouer après un enregistrement réussi (rafraîchir la liste des sites, ou la fiche).
    private Runnable apresSucces = () -> {};

    @FXML
    private VBox racine;

    @FXML
    private Label titreModale;

    @FXML
    private TextField champCarre;

    @FXML
    private TextField champNom;

    @FXML
    private ComboBox<Protocole> champProtocole;

    @FXML
    private TextArea champCommentaire;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    /// Enveloppe non désactivée du bouton : porte l'infobulle du grisage (#789, #1970).
    @FXML
    private StackPane enveloppeValider;

    @FXML
    private Button boutonValider;

    /// Portée de l'édition (#1380) : posé sous le champ « Nom convivial », qu'il commente.
    @FXML
    private Label lblPorteeEdition;

    /// Enveloppe non désactivée du bouton de vérification : porte l'infobulle du grisage (#789).
    @FXML
    private StackPane enveloppeVerifierCarre;

    @FXML
    private Button btnVerifierCarre;

    /// Ce que la plateforme a répondu sur le carré saisi (#3458).
    @FXML
    private Label messageCarreExistant;

    /// La position collée depuis une carte (#4577).
    @FXML
    private TextField champPosition;

    /// « Situer » : contrairement à « Vérifier sur Vigie-Chiro », il ne demande **rien au réseau**, donc
    /// il n'est jamais fermé pour cause de connexion.
    @FXML
    private Button btnSituer;

    /// Ce que la position a donné : distinct de [#messageCarreExistant], qui porte la réponse du
    /// portail. Les confondre effacerait l'un par l'autre.
    @FXML
    private Label messagePosition;

    /// Ligne du geste « Récupérer ce carré » (#3806) : elle n'existe qu'après un verdict « il existe
    /// déjà », et se retire de la mise en page le reste du temps.
    @FXML
    private HBox ligneRecupererCarre;

    @FXML
    private Button btnRecupererCarre;

    /// Ce que l'appelant fait d'un carré rapatrié : ouvrir sa fiche, et y porter le compte rendu. La
    /// modale ne le sait pas - elle se contente de fermer derrière elle.
    private Consumer<RapatriementCarre.Resultat.Rapatrie> apresRapatriement = rapatrie -> {};

    /// Exécuteur du socle (#1014) : interroger la plateforme est un appel **réseau**, il ne doit pas
    /// tourner sur le fil JavaFX. Synchrone en test (déterministe), en arrière-plan en production.
    private final ExecuteurTache executeur;

    /// Une recherche est en cours : le bouton se grise le temps de l'appel, sans quoi deux clics rapides
    /// en lanceraient deux.
    ///
    /// **Une propriété, et non un `setDisable`** : `disableProperty` est **liée** au carré valide, et
    /// JavaFX refuse d'affecter une valeur liée (« A bound value cannot be set »). Le patron du dépôt est
    /// de faire entrer l'occupation **dans** le binding (#1254), pas de la poser par-dessus.
    private final BooleanProperty rechercheEnCours = new SimpleBooleanProperty(this, "rechercheEnCours", false);

    private final Optional<EtatConnexion> etatConnexion;

    @Inject
    public ModaleSiteController(
            SiteEditViewModel viewModel, ExecuteurTache executeur, Optional<EtatConnexion> etatConnexion) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        // Vide dans un injecteur partiel sans la feature `connexion` : aucun jeton ne peut y exister,
        // et « fermé » y est la réponse juste. Cf. `CommunModule`.
        this.etatConnexion = Objects.requireNonNull(etatConnexion, "etatConnexion");
    }

    /// Interroge la plateforme **hors du fil JavaFX**, puis applique le verdict.
    ///
    /// Le bouton se désactive pendant l'appel : sans cela, deux clics rapides lanceraient deux
    /// recherches. Un échec technique remet le geste à portée, et le dit.
    @FXML
    private void situerPosition() {
        // Synchrone, sans `executeur` : le carroyage est embarqué et la recherche tient en quelques
        // millisecondes. Passer par le fil de fond ferait payer un aller-retour d'ordonnancement pour
        // un calcul local, et laisserait croire à un appel réseau qu'il n'y a pas.
        viewModel.situerPosition();
    }

    @FXML
    private void verifierCarre() {
        rechercheEnCours.set(true);
        // Le numéro est saisi ICI, sur le fil JavaFX, pour que l'échec sache lui aussi de quel carré il
        // parle : le ViewModel écarte un résultat qui ne porte plus sur ce qui est à l'écran.
        String demande = viewModel.numeroCarreProperty().get();
        executeur.executer(
                viewModel::chercherCarreExistant,
                resultat -> {
                    viewModel
                            .carre()
                            .appliquer(resultat, viewModel.numeroCarreProperty().get());
                    rechercheEnCours.set(false);
                },
                echec -> {
                    rechercheEnCours.set(false);
                    viewModel
                            .carre()
                            .appliquer(
                                    CarreExistantViewModel.ResultatRechercheCarre.indisponible(demande),
                                    viewModel.numeroCarreProperty().get());
                });
    }

    /// Récupère le carré **hors du fil JavaFX**, puis conclut.
    ///
    /// Succès : la modale se **ferme** et l'appelant ouvre la fiche du carré - le formulaire n'a plus
    /// lieu d'être, puisque le site existe désormais. Tout autre résultat laisse la modale ouverte avec
    /// son compte rendu : il reste quelque chose à faire ici.
    @FXML
    private void recupererCarre() {
        btnRecupererCarre.setDisable(true);
        executeur.executer(
                viewModel::rapatrierCarre,
                resultat -> {
                    btnRecupererCarre.setDisable(false);
                    if (resultat instanceof RapatriementCarre.Resultat.Rapatrie rapatrie) {
                        apresRapatriement.accept(rapatrie);
                        fermer();
                        return;
                    }
                    viewModel.carre().appliquerRapatriement(resultat);
                },
                echec -> {
                    btnRecupererCarre.setDisable(false);
                    viewModel
                            .carre()
                            .appliquerRapatriement(new RapatriementCarre.Resultat.Indisponible(
                                    "la récupération s'est interrompue : " + echec.getMessage()));
                });
    }

    @FXML
    private void initialize() {
        // Une modale est dimensionnée à son ouverture ; un bandeau de retour qui paraît ensuite
        // pousserait les boutons du bas hors du cadre. On fait suivre à la fenêtre la croissance de
        // son contenu (ADR 2493, #1534).
        Modales.suivreLaCroissance(racine, bandeauRetour.managedProperty());
        titreModale.textProperty().bind(viewModel.titreProperty());
        LibelleRetour.installer(lblPorteeEdition, viewModel.porteeEditionProperty());
        champCarre.textProperty().bindBidirectional(viewModel.numeroCarreProperty());
        champNom.textProperty().bindBidirectional(viewModel.nomProperty());
        champCommentaire.textProperty().bindBidirectional(viewModel.commentaireProperty());

        // Filtre de saisie : uniquement des chiffres, au plus 6 (format du carré Vigie-Chiro, R1).
        champCarre.setTextFormatter(
                new TextFormatter<>(modif -> modif.getControlNewText().matches("\\d{0,6}") ? modif : null));

        champProtocole.getItems().setAll(Protocole.values());
        champProtocole.valueProperty().bindBidirectional(viewModel.protocoleProperty());
        champProtocole.setConverter(new StringConverter<>() {
            @Override
            public String toString(Protocole protocole) {
                return protocole == null ? "" : protocole.libelle();
            }

            @Override
            public Protocole fromString(String libelle) {
                return Protocole.parLibelle(libelle);
            }
        });

        boutonValider.textProperty().bind(viewModel.libelleBoutonProperty());
        // On EMPÊCHE plutôt que d'avertir après coup (#790) : le bouton reste fermé tant que le carré n'a
        // pas ses six chiffres, et le champ rougit dès qu'il est saisi mais incomplet.
        boutonValider.disableProperty().bind(viewModel.peutEnregistrer().not());
        // Le motif du grisage se dit ici et non dans un message d'erreur (#1970) : la garde du ViewModel
        // teste EXACTEMENT ce prédicat, donc son message n'aurait jamais pu être lu par personne.
        // Le motif du gris a maintenant DEUX causes - carré incomplet, ou carré déjà pris là-bas - et
        // elles ne se corrigent pas de la même façon. Le ViewModel les distingue ; l'infobulle se
        // recalcule sur ce qui les fait changer (#1970, #3806).
        IndicateurBlocage.expliquer(
                enveloppeValider,
                Bindings.createStringBinding(
                        () -> viewModel.peutEnregistrer().get()
                                ? "Enregistrer ce site de suivi."
                                : viewModel.motifEnregistrementFerme(),
                        viewModel.peutEnregistrer(),
                        viewModel.carre().recuperable(),
                        viewModel.enCreation()));
        ValidationFormulaire.marquerInvalide(champCarre, viewModel.carreInvalideEtSaisi());

        // « Ce carré existe-t-il déjà ? » (#3458). Le geste n'apparaît que si la recherche est installée
        // (hors application complète, l'Optional est vide) ; il se grise tant que le carré n'a pas ses
        // six chiffres, puisqu'il n'y aurait rien à chercher.
        //
        // La visibilité est POSÉE et non liée, à dessein : l'ADR 3539 range ce cas dans ses deux familles
        // légitimes - un drapeau de fonctionnalité (`Optional<Service>.isPresent()`, dont la bascule ne
        // prend effet qu'au prochain démarrage) et un contrôle de modale (rebâtie à chaque ouverture).
        // Le fait qui la rend vraie ne peut pas changer pendant la vie de cette fenêtre.
        enveloppeVerifierCarre.setVisible(viewModel.carre().disponible());
        enveloppeVerifierCarre.setManaged(viewModel.carre().disponible());
        // Fermé aussi TANT QU'AUCUN JETON n'est disponible (#4210). Sans cela le geste était offert :
        // on tapait six chiffres, on cliquait, on payait un aller-retour réseau, et l'encart répondait
        // « Vérification impossible » - alors que l'application savait avant le clic qu'elle n'avait pas
        // de jeton. Empêcher plutôt qu'avertir (#789, heuristique 5 de Nielsen), comme pour
        // « Récupérer depuis Vigie-Chiro » (#4194).
        //
        // Seule la VÉRIFICATION se ferme. Déclarer un carré hors connexion reste possible : c'est le
        // travail hors ligne, et le fermer ferait de la plateforme une condition pour saisir chez soi.
        //
        // Le script promettait l'inverse (« le bouton reste offert, non grisé »), au nom d'un
        // argument emprunté à `ControleCarreStoc` - le contrôle AUTOMATIQUE des coordonnées d'un point,
        // qui est en effet un confort. Ce bouton-ci passe par `chercherCarre`. Restait « on s'est
        // peut-être connecté entre-temps » : depuis #4205 le geste se rouvre tout seul dès qu'un jeton
        // arrive, sans rouvrir la fenêtre.
        BooleanExpression connecte = etatConnexion
                .<BooleanExpression>map(EtatConnexion::connecteProperty)
                .orElseGet(() -> new SimpleBooleanProperty(false));
        btnVerifierCarre
                .disableProperty()
                .bind(viewModel.carreValide().not().or(rechercheEnCours).or(connecte.not()));
        IndicateurBlocage.expliquer(
                enveloppeVerifierCarre,
                Bindings.when(connecte)
                        .then(Bindings.when(viewModel.carreValide())
                                .then("Demander à Vigie-Chiro si ce carré y est déjà déclaré.")
                                .otherwise("Renseignez d'abord un numéro de carré à 6 chiffres."))
                        .otherwise("Vous n'êtes pas connecté à Vigie-Chiro : rien ne peut être vérifié."
                                + " Vous pouvez déclarer ce carré sans vérifier, ou vous connecter depuis"
                                + " le menu principal, entrée « Se connecter à Vigie-Chiro… »."));
        champPosition.textProperty().bindBidirectional(viewModel.position().texte());
        LibelleRetour.installer(messagePosition, viewModel.position().retour());
        Modales.suivreLaCroissance(racine, messagePosition.managedProperty());
        // Fermé tant qu'il n'y a rien à situer, et JAMAIS pour cause de connexion : le carroyage est
        // embarqué. C'est la difference avec « Vérifier sur Vigie-Chiro », dont le motif ci-dessus parle
        // de jeton.
        btnSituer.disableProperty().bind(champPosition.textProperty().isEmpty());
        IndicateurBlocage.expliquer(
                btnSituer,
                Bindings.when(champPosition.textProperty().isEmpty())
                        .then("Collez d'abord une position, latitude puis longitude.")
                        .otherwise("Déduire le carré de cette position. Aucune connexion nécessaire."));
        LibelleRetour.installer(messageCarreExistant, viewModel.carre().retourProperty());
        // Le geste suit le verdict : visible seulement quand il y a un carré à récupérer, et retiré de la
        // mise en page sinon - un bouton grisé en permanence sur un carré libre n'aurait rien à dire.
        ligneRecupererCarre.visibleProperty().bind(viewModel.carre().recuperable());
        ligneRecupererCarre.managedProperty().bind(viewModel.carre().recuperable());
        Modales.suivreLaCroissance(racine, ligneRecupererCarre.managedProperty());
        Modales.suivreLaCroissance(racine, messageCarreExistant.managedProperty());

        // #1917 : bandeau partagé (ADR 0023). Le libellé s'appelait « messageErreur » et ne pouvait
        // donc rien porter d'autre qu'un échec ; la sévérité vit maintenant dans la valeur.
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);
        viewModel.carreInvalideEtSaisi().addListener((observable, avant, invalide) -> majStyleCarre());
    }

    /// Ouvre la modale en **déclaration** d'un nouveau site.
    public void demarrerCreation(Runnable apresSucces) {
        demarrerCreation(apresSucces, rapatrie -> {});
    }

    /// Ouvre la modale en **déclaration**, en disant ce qu'il advient d'un carré **rapatrié** (#3806) :
    /// l'appelant ouvre sa fiche et y porte le compte rendu, la modale se contente de fermer.
    public void demarrerCreation(
            Runnable apresSucces, Consumer<RapatriementCarre.Resultat.Rapatrie> apresRapatriement) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        this.apresRapatriement = Objects.requireNonNull(apresRapatriement, "apresRapatriement");
        viewModel.preparerCreation();
        majStyleCarre();
    }

    /// Ouvre la modale en **édition** du site donné (champs pré-remplis).
    public void demarrerEdition(Site site, Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
        viewModel.preparerEdition(site);
        majStyleCarre();
    }

    /// Le ViewModel exposé aux tests : la validation se vérifie **sans IHM**.
    SiteEditViewModel viewModel() {
        return viewModel;
    }

    @FXML
    private void valider() {
        if (viewModel.enregistrer()) {
            apresSucces.run();
            fermer();
        }
        // Un refus métier (carré déjà déclaré) laisse la modale ouverte : le motif s'affiche À CÔTÉ du
        // champ fautif, et la saisie est conservée. C'est ce que l'alerte d'après coup ne permettait pas.
    }

    @FXML
    private void annuler() {
        fermer();
    }

    /// Surligne le champ carré uniquement quand il est saisi **et** invalide (R1).
    private void majStyleCarre() {
        champCarre.getStyleClass().remove(STYLE_CHAMP_INVALIDE);
        if (viewModel.carreInvalideEtSaisi().get()) {
            champCarre.getStyleClass().add(STYLE_CHAMP_INVALIDE);
        }
    }

    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }
}
