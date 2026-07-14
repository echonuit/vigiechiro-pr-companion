package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.FormatAvisValidateur;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MessageObservation;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Le **fil de discussion** de l'observation sélectionnée, donné à lire à côté du spectrogramme (#1417).
///
/// **Pourquoi pas une modale.** Un `showAndWait()` fige un test headless (leçon #1013 / #1405), et surtout
/// un fil se lit *en écoutant* : l'obliger à passer par une fenêtre qui masque le son, c'est séparer deux
/// gestes que l'utilisateur fait ensemble. Le panneau vit donc à droite du lecteur.
///
/// Il n'apparaît que **s'il y a quelque chose à lire** : sur la grande majorité des détections, personne
/// n'a jamais écrit, et un cadre vide permanent volerait de la largeur au spectrogramme pour ne rien dire.
///
/// En **lecture** seule : répondre viendra avec #1418 (une écriture définitive, qui demande sa propre
/// confirmation).
final class PanneauDiscussion {

    /// Largeur du panneau : assez pour une phrase, assez peu pour ne pas écraser le spectrogramme.
    private static final double LARGEUR = 320;

    private final VBox racine = new VBox(6);
    private final VBox messages = new VBox(10);

    /// Zone de rédaction (#1418). Séparée du fil : on lit en haut, on écrit en bas.
    private final TextArea saisie = new TextArea();

    private final Button envoyer = new Button("Envoyer au validateur…");

    /// Enveloppe du bouton : un `Button` désactivé n'affiche **pas** de tooltip, l'explication du blocage
    /// se pose donc sur le conteneur, qui reçoit le survol (#789).
    private final StackPane enveloppeEnvoyer = new StackPane(envoyer);

    /// Texte du tooltip : ce que fait l'action, ou pourquoi elle est bloquée.
    private final StringProperty explication = new SimpleStringProperty();

    /// Observation dont le fil est affiché (cible de l'envoi), ou `null`.
    private Long selection;

    /// Confirmation d'une écriture **irréversible** : porteur injectable (#1013), stub en test.
    private final ConfirmateurModifiable confirmateur =
            new ConfirmateurModifiable(new ConfirmationNavigation("Envoyer ce message ?"));

    /// Compte rendu : porteur injectable (#1405), double capturant en test.
    private final NotificateurModifiable notificateur = new NotificateurModifiable();

    PanneauDiscussion() {
        racine.setId("panneauDiscussion");
        racine.getStyleClass().add("panneau-discussion");
        racine.setPrefWidth(LARGEUR);
        racine.setMinWidth(LARGEUR);
        racine.setVisible(false);
        racine.setManaged(false);

        Label titre = new Label("Discussion avec le validateur");
        titre.getStyleClass().add("titre-discussion");

        messages.setId("filDiscussion");
        ScrollPane cadre = new ScrollPane(messages);
        cadre.setFitToWidth(true);
        VBox.setVgrow(cadre, javafx.scene.layout.Priority.ALWAYS);

        saisie.setPromptText("Écrire au validateur…");
        saisie.setPrefRowCount(3);
        saisie.setWrapText(true);
        saisie.setId("saisieMessage");
        envoyer.setId("boutonEnvoyerMessage");
        enveloppeEnvoyer.setId("enveloppeEnvoyerMessage");
        IndicateurBlocage.expliquer(enveloppeEnvoyer, explication);

        // Rien à envoyer tant que rien n'est écrit : le bouton le dit plutôt que de ne rien faire au clic.
        envoyer.disableProperty().bind(saisie.textProperty().isEmpty().or(saisie.disabledProperty()));

        racine.getChildren().addAll(titre, cadre, saisie, enveloppeEnvoyer);
    }

    /// Branche l'envoi. Le geste est **définitif** : le confirmateur doit le dire, et l'appel réseau
    /// tourne hors du fil JavaFX, sous le voile du chrome.
    private void armerEnvoi(DiscussionValidateur discussion, ExecuteurTache executeur) {
        envoyer.setOnAction(evenement -> {
            String texte = saisie.getText().strip();
            if (texte.isEmpty() || selection == null) {
                return;
            }
            // Ce qui part ne se retire pas : le serveur ajoute par $push, et aucune route ne permet de
            // supprimer ni de modifier un message. La confirmation doit le DIRE, pas demander « êtes-vous
            // sûr ? » : on ne consent qu'à ce qu'on a compris.
            if (!confirmateur.confirmer("Ce message sera visible par le validateur du MNHN et ne pourra"
                    + " PAS être supprimé ni modifié :\n\n« " + texte + " »\n\nL'envoyer ?")) {
                return;
            }
            Long cible = selection;
            executeur.executer(
                    () -> discussion.poster(cible, texte),
                    reponse -> restituer(reponse, discussion),
                    echec -> notificateur.notifier(
                            NiveauNotification.AVERTISSEMENT, "Envoi impossible", message(echec)));
        });
    }

    /// Sur le fil JavaFX : le message est parti (ou non), et le fil se recharge.
    private void restituer(ReponseApi<String> reponse, DiscussionValidateur discussion) {
        reponse.echec()
                .ifPresentOrElse(
                        echec -> notificateur.notifier(
                                NiveauNotification.AVERTISSEMENT,
                                "Message non envoyé",
                                echec + "\n\nRien n'a été publié : votre texte est toujours là."),
                        () -> saisie.clear());
        afficher(discussion.fil(selection), discussion.idProfilConnecte(), discussion.pourquoiPasEcrire(selection));
    }

    private static String message(Throwable echec) {
        return echec.getMessage() != null ? echec.getMessage() : echec.toString();
    }

    /// Zone de rédaction, exposée aux tests : c'est elle qui se désactive quand l'envoi est impossible.
    TextArea saisie() {
        return saisie;
    }

    /// Bouton d'envoi, exposé aux tests.
    Button envoyer() {
        return envoyer;
    }

    /// Arme l'envoi sur une observation donnée, **sans table ni écran** : c'est ce qui permet de jouer le
    /// geste jusqu'à l'appel réseau (bouchonné) sans monter toute la vue « Sons & validation ».
    void armerPourTest(DiscussionValidateur discussion, ExecuteurTache executeur, Long idObservation) {
        armerEnvoi(discussion, executeur);
        selection = idObservation;
        afficher(
                discussion.fil(idObservation),
                discussion.idProfilConnecte(),
                discussion.pourquoiPasEcrire(idObservation));
    }

    /// Porteur de confirmation exposé aux tests (#1013).
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    /// Porteur de compte rendu exposé aux tests (#1405).
    NotificateurModifiable notificateur() {
        return notificateur;
    }

    /// Nœud à insérer dans le panneau d'écoute.
    VBox racine() {
        return racine;
    }

    /// **Installe** le fil dans l'écran : il prend place dans `hote`, à droite du lecteur, et suit la
    /// sélection de `table`. Le chargement passe par le ViewModel — la vue ne touche jamais la base.
    ///
    /// Installé ici plutôt qu'épelé dans le contrôleur (patron de [MenuCertitude#installer]) : celui-ci est
    /// au plafond de NcssCount, et ce câblage forme une unité cohésive qui n'a rien à y faire.
    static PanneauDiscussion installer(
            StackPane hote,
            TableView<LigneObservationAudio> table,
            AudioViewModel viewModel,
            ExecuteurTache executeur) {
        PanneauDiscussion panneau = new PanneauDiscussion();
        DiscussionValidateur discussion = viewModel.discussion();
        hote.getChildren().add(panneau.racine());
        hote.visibleProperty().bind(panneau.racine().visibleProperty());
        hote.managedProperty().bind(panneau.racine().managedProperty());
        panneau.armerEnvoi(discussion, executeur);
        table.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, ancienne, nouvelle) -> panneau.recharger(nouvelle, discussion));
        panneau.recharger(null, discussion);
        return panneau;
    }

    /// Recharge le fil de `ligne`, et l'état de la saisie.
    private void recharger(LigneObservationAudio ligne, DiscussionValidateur discussion) {
        selection = ligne == null ? null : ligne.idObservation();
        afficher(discussion.fil(selection), discussion.idProfilConnecte(), discussion.pourquoiPasEcrire(selection));
    }

    /// Affiche `fil`, et arme (ou non) la saisie. Le panneau s'ouvre s'il y a **quelque chose à lire**
    /// *ou* **quelque chose à dire** : depuis #1418, une détection connue de VigieChiro sur laquelle
    /// personne n'a encore écrit reste un endroit où l'on peut ouvrir une discussion.
    ///
    /// Quand l'envoi est impossible, la saisie est **désactivée et l'enveloppe en dit la raison**
    /// (affordance #789) — un champ qui ne mènerait à rien serait pire qu'un champ absent.
    void afficher(List<MessageObservation> fil, String idProfilConnecte, Optional<String> pourquoiPasEcrire) {
        messages.getChildren().clear();
        fil.forEach(message -> messages.getChildren().add(bulle(message, idProfilConnecte)));

        boolean peutEcrire = pourquoiPasEcrire.isEmpty();
        saisie.setDisable(!peutEcrire);
        explication.set(pourquoiPasEcrire.orElse(
                "Envoie ce message au validateur du MNHN. Définitif : il ne pourra pas être supprimé."));
        if (!peutEcrire) {
            saisie.clear();
        }

        boolean ouvert = !fil.isEmpty() || peutEcrire;
        racine.setVisible(ouvert);
        racine.setManaged(ouvert);
    }

    /// Un message : qui, quand, quoi. Savoir **qui parle** est la moitié de l'information dans une
    /// discussion — l'entête le dit en clair, et le style distingue nos messages de ceux de l'expert.
    private static VBox bulle(MessageObservation message, String idProfilConnecte) {
        Label entete = new Label(entete(message, idProfilConnecte));
        entete.getStyleClass().add(message.deMoi(idProfilConnecte) ? "message-de-moi" : "message-du-validateur");

        Label texte = new Label(message.texte());
        texte.setWrapText(true);
        texte.setMaxWidth(LARGEUR - 30);

        VBox bulle = new VBox(2, entete, texte);
        bulle.getStyleClass().add("bulle-message");
        return bulle;
    }

    /// « Vous · 11/07/2026 21:04 », ou l'auteur seul quand le serveur n'a pas daté le message.
    private static String entete(MessageObservation message, String idProfilConnecte) {
        String auteur = FormatAvisValidateur.auteur(message, idProfilConnecte);
        String quand = FormatAvisValidateur.quand(message);
        return quand.isEmpty() ? auteur : auteur + " · " + quand;
    }
}
