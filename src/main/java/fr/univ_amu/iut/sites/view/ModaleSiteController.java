package fr.univ_amu.iut.sites.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ValidationFormulaire;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.viewmodel.SiteEditViewModel;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
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
    private Label messageErreur;

    @FXML
    private Button btnFermerRetour;

    @FXML
    private Button boutonValider;

    @Inject
    public ModaleSiteController(SiteEditViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
        titreModale.textProperty().bind(viewModel.titreProperty());
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
        ValidationFormulaire.marquerInvalide(champCarre, viewModel.carreInvalideEtSaisi());

        // #1917 : bandeau partagé (ADR 0023). Le libellé s'appelait « messageErreur » et ne pouvait
        // donc rien porter d'autre qu'un échec ; la sévérité vit maintenant dans la valeur.
        BandeauRetour.installer(
                bandeauRetour, messageErreur, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);
        viewModel.carreInvalideEtSaisi().addListener((observable, avant, invalide) -> majStyleCarre());
    }

    /// Ouvre la modale en **déclaration** d'un nouveau site.
    public void demarrerCreation(Runnable apresSucces) {
        this.apresSucces = Objects.requireNonNull(apresSucces, "apresSucces");
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
