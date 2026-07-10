package fr.univ_amu.iut.connexion.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/// Controller de la **modale « Connexion VigieChiro »** (`ConnexionModale.fxml`, #727/#741).
///
/// Guide l'utilisateur en trois étapes pour relier l'app à son compte, **sans fichier externe** :
/// 1. ouvrir VigieChiro (navigateur système, via [OuvreurDeLien]) ;
/// 2. installer le **marque-page** qui copie le token (bouton « Copier le marque-page ») ;
/// 3. coller le token, vérifié via `GET /moi` **hors du fil JavaFX** (thread virtuel).
///
/// Pur câblage : lie les contrôles aux propriétés du [ConnexionViewModel].
public class ConnexionModaleController {

    /// Page d'accueil de la plateforme (connexion GitHub/Google), ouverte à l'étape 1.
    private static final String URL_VIGIECHIRO = "https://vigiechiro.herokuapp.com";

    /// Marque-page (bookmarklet) copié à l'étape 2 : lit le token du `localStorage` de VigieChiro et le
    /// place dans le presse-papier (repli `prompt` si l'API clipboard du navigateur est indisponible).
    private static final String MARQUE_PAGE = "javascript:(function(){"
            + "var t=localStorage.getItem('auth-session-token');"
            + "if(!t){alert('Aucun token : connectez-vous sur VigieChiro puis recliquez ce marque-page.');return;}"
            + "if(navigator.clipboard){navigator.clipboard.writeText(t).then("
            + "function(){alert('Token VigieChiro copie ('+t.length+' caracteres).');},"
            + "function(){window.prompt('Copiez votre token VigieChiro :',t);});}"
            + "else{window.prompt('Copiez votre token VigieChiro :',t);}})();";

    /// Familles de couleur sémantiques (design system) du bandeau de statut et du badge d'identité.
    private static final String STATUT_INFO = "badge-info";

    private static final String STATUT_SUCCES = "badge-succes";

    private static final String STATUT_DANGER = "badge-danger";

    private final ConnexionViewModel viewModel;
    private final OuvreurDeLien ouvreurDeLien;

    /// Vrai le temps de l'appel réseau de connexion : grise le bouton (et le champ) sans se battre avec
    /// le binding sur l'état connecté.
    private final BooleanProperty verificationEnCours = new SimpleBooleanProperty(false);

    @FXML
    private VBox racine;

    @FXML
    private TextField champToken;

    @FXML
    private Label labelIdentite;

    @FXML
    private Label bandeauStatut;

    @FXML
    private Button boutonConnecter;

    @FXML
    private Button boutonDeconnecter;

    /// Enveloppes (non désactivées) des boutons : portent le tooltip d'explication du blocage, qu'un
    /// Button désactivé n'affiche pas. Cf. [IndicateurBlocage] (#789).
    @FXML
    private StackPane enveloppeConnecter;

    @FXML
    private StackPane enveloppeDeconnecter;

    @Inject
    public ConnexionModaleController(ConnexionViewModel viewModel, OuvreurDeLien ouvreurDeLien) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    @FXML
    private void initialize() {
        labelIdentite.textProperty().bind(viewModel.identiteProperty());
        // Le badge d'identité vire au vert quand on est connecté, au gris sinon (affordance d'état).
        viewModel.connecteProperty().addListener((obs, ancien, connecte) -> majBadgeIdentite(connecte));
        // Verrouille la saisie quand on est connecté (ou pendant la vérification) : on comprend qu'il n'y
        // a plus rien à coller. Le bouton « Se déconnecter » fait le miroir.
        champToken.disableProperty().bind(viewModel.connecteProperty().or(verificationEnCours));
        boutonConnecter.disableProperty().bind(viewModel.connecteProperty().or(verificationEnCours));
        boutonDeconnecter.disableProperty().bind(viewModel.connecteProperty().not());
        // Tooltips d'explication du grisage (#789), posés sur les enveloppes (un Button désactivé n'en
        // affiche pas). Le texte suit l'état : cause du blocage ou description de l'action disponible.
        IndicateurBlocage.expliquer(
                enveloppeConnecter,
                Bindings.when(viewModel.connecteProperty())
                        .then("Vous êtes déjà connecté à VigieChiro : déconnectez-vous d'abord pour changer de jeton.")
                        .otherwise(Bindings.when(verificationEnCours)
                                .then("Vérification du jeton en cours…")
                                .otherwise("Se connecter à VigieChiro avec le jeton collé ci-dessus.")));
        IndicateurBlocage.expliquer(
                enveloppeDeconnecter,
                Bindings.when(viewModel.connecteProperty())
                        .then("Se déconnecter de VigieChiro (efface le jeton mémorisé sur ce poste).")
                        .otherwise("Aucune connexion active à interrompre."));
        viewModel.rafraichir();
        majBadgeIdentite(viewModel.connecteProperty().get());
    }

    /// Étape 1 : ouvre la plateforme dans le navigateur système (pour s'y connecter).
    @FXML
    private void ouvrirSite() {
        ouvreurDeLien.ouvrir(URL_VIGIECHIRO);
    }

    /// Étape 2 : copie le marque-page dans le presse-papier, à installer comme favori.
    @FXML
    private void copierMarquePage() {
        ClipboardContent contenu = new ClipboardContent();
        contenu.putString(MARQUE_PAGE);
        Clipboard.getSystemClipboard().setContent(contenu);
        afficherStatut(
                "Marque-page copié : créez un favori, collez-le comme adresse, puis cliquez-le sur l'onglet VigieChiro.",
                STATUT_INFO);
    }

    /// Étape 3 : vérifie et enregistre le token collé.
    @FXML
    private void connecter() {
        String token = champToken.getText();
        if (token == null || token.isBlank()) {
            afficherStatut("Collez d'abord votre token VigieChiro.", STATUT_INFO);
            return;
        }
        verificationEnCours.set(true);
        afficherStatut("Vérification en cours…", STATUT_INFO);
        Thread.ofVirtual().name("connexion-vigiechiro").start(() -> {
            Optional<ProfilVigieChiro> profil = viewModel.connecter(token);
            Platform.runLater(() -> {
                verificationEnCours.set(false);
                viewModel.rafraichir();
                if (profil.isPresent()) {
                    String resume = viewModel.resumeSynchro();
                    afficherStatut(
                            resume.isBlank()
                                    ? "Connexion réussie."
                                    : "Connexion réussie · référentiel à jour : " + resume + ".",
                            STATUT_SUCCES);
                    champToken.clear();
                } else {
                    afficherStatut(
                            "Token invalide ou expiré : recollez-en un depuis le site VigieChiro.", STATUT_DANGER);
                }
            });
        });
    }

    @FXML
    private void deconnecter() {
        viewModel.deconnecter();
        viewModel.rafraichir();
        champToken.clear();
        afficherStatut("Déconnecté.", STATUT_INFO);
    }

    /// Bascule le badge d'identité entre le vert « connecté » et le gris « non connecté ».
    private void majBadgeIdentite(boolean connecte) {
        labelIdentite.getStyleClass().setAll("badge", connecte ? STATUT_SUCCES : "badge-neutre");
    }

    /// Affiche le bandeau de statut avec le texte et la famille de couleur sémantique donnés
    /// (`badge-succes` / `badge-danger` / `badge-info`).
    private void afficherStatut(String texte, String classeSemantique) {
        bandeauStatut.setText(texte);
        bandeauStatut.getStyleClass().setAll("bandeau-statut", classeSemantique);
        bandeauStatut.setVisible(true);
        bandeauStatut.setManaged(true);
    }

    @FXML
    private void fermer() {
        ((Stage) racine.getScene().getWindow()).close();
    }
}
