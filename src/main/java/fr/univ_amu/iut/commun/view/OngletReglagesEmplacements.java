package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.ServiceEmplacements;
import fr.univ_amu.iut.commun.model.ServiceEmplacements.Emplacements;
import fr.univ_amu.iut.commun.model.SondeAccessibilite;
import fr.univ_amu.iut.commun.model.SondeAccessibilite.Verdict;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/// Onglet « Emplacements » des réglages (#1038, [ADR 1038]) : où vivent le **dossier de travail** et la
/// **base**. Premier onglet **personnalisé** contribué par le socle, parce que choisir un dossier
/// demande un sélecteur natif, une sonde d'accessibilité et un avis de redémarrage - rien de tout cela
/// ne se décrit avec un [DescripteurReglage].
///
/// ## Ce que l'écran dit, et ne cache pas
///
/// Changer un emplacement **ne déplace pas les données** : cela change le pointeur lu au prochain
/// démarrage. L'écran le dit avant le choix. C'est le même principe que pour l'audio (ADR 0048) :
/// l'application ne bouge pas les fichiers de l'utilisateur à sa place.
///
/// ## Pourquoi trois porteurs injectables
///
/// Le geste **commence** par un `DirectoryChooser` natif, qui fige un test TestFX headless exactement
/// comme un `Alert.showAndWait()`. La désignation ([SelecteurFichierModifiable]) et le compte rendu
/// ([NotificateurModifiable]) sont donc remplaçables par des doubles, et la **sortie** de l'application
/// aussi ([#definirSortie]) : `Platform.exit()` en dur tuerait le runtime TestFX.
public final class OngletReglagesEmplacements implements OngletReglagesPersonnalise {

    private final ServiceEmplacements service;
    private final SelecteurFichierModifiable selecteur;
    private final NotificateurModifiable notificateur;

    /// Sortie de l'application, déclenchée par le bouton « Quitter » de l'avis de redémarrage. Le vrai
    /// `Platform.exit()` par défaut, un double capturant en test.
    private Runnable sortie = Platform::exit;

    /// Dossiers choisis, en attente d'être écrits. Initialisés sur les emplacements effectifs.
    private final ObjectProperty<Path> travailChoisi = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> dossierBaseChoisi = new SimpleObjectProperty<>();

    /// Une configuration personnalisée **existe sur le disque** (#3543). Le bouton « Rétablir » s'y
    /// lie au lieu d'être grisé une fois pour toutes au montage : sa valeur d'alors ne se reprenait
    /// jamais, `formulairePersonnalise()` mettant sa racine en cache.
    ///
    /// ⚠️ Elle ne se dérive **pas** des chemins choisis. Avant « Appliquer », ceux-ci ne sont qu'une
    /// intention, et un bouton lié à eux s'allumerait sur un choix que rien n'a écrit. Elle bascule
    /// donc dans [#appliquer()] et [#reinitialiser()], **après** que le service a rendu la main.
    private final BooleanProperty configurationPersonnalisee = new SimpleBooleanProperty();

    private VBox racine;
    private VBox avisRedemarrage;

    @Inject
    public OngletReglagesEmplacements(ServiceEmplacements service) {
        this.service = Objects.requireNonNull(service, "service");
        this.selecteur = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(this::fenetre));
        this.notificateur = new NotificateurModifiable(new NotificationDialogue(this::fenetre));
    }

    @Override
    public String idFeature() {
        return "emplacements";
    }

    @Override
    public int ordre() {
        return 15; // juste après « Général » (10), avant les onglets des features.
    }

    @Override
    public String titre() {
        return "Emplacements";
    }

    @Override
    public String iconeLiteral() {
        return "fas-folder-open";
    }

    @Override
    public List<DescripteurReglage> reglages() {
        return List.of(); // tout est dans le formulaire personnalisé.
    }

    @Override
    public Node formulairePersonnalise() {
        if (racine != null) {
            return racine;
        }
        Emplacements courant = service.emplacementsCourants();
        travailChoisi.set(courant.espaceDeTravail());
        dossierBaseChoisi.set(courant.base().getParent());

        racine = new VBox();
        racine.getStyleClass().add("emplacements");
        racine.getChildren().add(intro());
        racine.getChildren().add(avertissementDeplacement());
        racine.getChildren()
                .add(ligne(
                        "Dossier de travail",
                        "Les sessions et leur audio possédé.",
                        travailChoisi,
                        courant.espaceDeTravailParDefaut()));
        racine.getChildren()
                .add(ligne(
                        "Base de données",
                        "Le fichier vigiechiro.db : observations, validations, liens Vigie-Chiro.",
                        dossierBaseChoisi,
                        courant.baseParDefaut().getParent()));
        configurationPersonnalisee.set(courant.personnalise());
        racine.getChildren().add(boutons());
        avisRedemarrage = avisRedemarrage();
        racine.getChildren().add(avisRedemarrage);
        return racine;
    }

    private Node intro() {
        Label titre = new Label("Où l'application range ses données");
        titre.getStyleClass().add("emplacements-titre");
        return titre;
    }

    private Node avertissementDeplacement() {
        Label texte =
                new Label("Choisir un emplacement change seulement où l'application ira lire au prochain démarrage : "
                        + "vos données ne sont pas déplacées. Si vous pointez la base vers un dossier vide, "
                        + "l'application y démarrera sur une base neuve, l'ancienne restant intacte à son ancien "
                        + "emplacement. Pour l'emporter, copiez le fichier vous-même.");
        texte.setWrapText(true);
        texte.getStyleClass().add("emplacements-avertissement");
        return texte;
    }

    private Node ligne(String titre, String aide, ObjectProperty<Path> choix, Path parDefaut) {
        Label libelle = new Label(titre);
        libelle.getStyleClass().add("emplacements-libelle");

        Label description = new Label(aide);
        description.setWrapText(true);
        description.getStyleClass().add("emplacements-aide");

        Label chemin = new Label();
        chemin.getStyleClass().add("emplacements-chemin");
        chemin.textProperty().bind(choix.map(Path::toString));

        // #3882 : la suite du geste se passe AILLEURS - explorateur de fichiers, terminal, message où
        // l'on demande de l'aide - et un Label n'est pas seulement en lecture seule, il n'est pas
        // sélectionnable du tout. Ce qui part dans le presse-papier est ce que l'écran AFFICHE, et non
        // la valeur du modèle : une copie qui diverge de ce qu'on lit serait pire que pas de copie,
        // puisqu'on la collerait sans la relire.
        Button copier = new Button("Copier");
        // Deux classes, deux rôles (#3952). `emplacements-copier` est le SÉLECTEUR dont les tests se
        // servent pour retrouver ces boutons ; elle ne porte aucun style, et ne doit pas en porter.
        // `bouton-secondaire` est la classe du socle, celle que le jumeau du Lot emploie déjà.
        //
        // ⚠️ Elle n'aurait rien fait avant #3966 : cet écran était le seul des 24 à ne pas charger
        // `design.css`, où elle est définie. La poser plus tôt aurait donné une classe inerte, qu'un
        // test aurait certifiée.
        copier.getStyleClass().addAll("emplacements-copier", "bouton-secondaire");
        // Le pictogramme de la copie, comme dans EtapeTeleversement.fxml. Posé en `graphic` et non
        // écrit dans le libellé : un caractère dépend des polices installées (ADR 0035).
        copier.setGraphic(new FontIcon("fas-copy"));
        copier.setOnAction(evenement -> PressePapier.copier(chemin.getText()));
        copier.setTooltip(new Tooltip("Copier ce chemin dans le presse-papier."));

        Button choisir = new Button("Choisir…");
        choisir.getStyleClass().add("emplacements-choisir");
        choisir.setOnAction(evenement -> choisir(titre, choix));

        // ⚠️ C'est ce Region qui absorbe la place restante, et non le libellé. Donné au libellé - comme
        // il l'était avant #3882 - le `hgrow` lui faisait occuper toute la rangée et rejetait les
        // boutons contre le bord droit. « Choisir… » peut se le permettre, il ne désigne rien ;
        // « Copier » non, il doit se lire contre le chemin qu'il copie. C'est le défaut trouvé en
        // regardant l'aperçu de #3464, et il se serait reproduit ici à l'identique.
        Region ressort = new Region();
        HBox.setHgrow(ressort, Priority.ALWAYS);

        HBox rangee = new HBox(chemin, copier, ressort, choisir);
        rangee.getStyleClass().add("emplacements-rangee");

        Label rappel = new Label("Par défaut : " + parDefaut);
        rappel.getStyleClass().add("emplacements-defaut");

        VBox bloc = new VBox(libelle, description, rangee, rappel);
        bloc.getStyleClass().add("emplacements-bloc");
        return bloc;
    }

    private void choisir(String quoi, ObjectProperty<Path> choix) {
        Optional<Path> designe =
                selecteur.choisirDossier("Choisir le dossier : " + quoi, Optional.ofNullable(choix.get()));
        if (designe.isEmpty()) {
            return;
        }
        Path dossier = designe.get();
        Verdict verdict = SondeAccessibilite.sonder(dossier);
        if (verdict.accessible()) {
            choix.set(dossier);
            masquerAvis();
        } else {
            notificateur.notifier(
                    NiveauNotification.AVERTISSEMENT,
                    "Dossier inutilisable",
                    "Ce dossier est " + verdict.motif() + ".");
        }
    }

    private Node boutons() {
        Button appliquer = new Button("Appliquer");
        appliquer.getStyleClass().addAll("emplacements-appliquer", "bouton-primaire");
        appliquer.setOnAction(evenement -> appliquer());

        Button reinitialiser = new Button("Rétablir les emplacements par défaut");
        reinitialiser.getStyleClass().add("emplacements-reinitialiser");
        // Lié, et non posé : rien à rétablir tant qu'aucune configuration n'a été écrite, et le
        // bouton suit désormais l'écriture au lieu de l'instant du montage.
        reinitialiser.disableProperty().bind(configurationPersonnalisee.not());
        reinitialiser.setOnAction(evenement -> reinitialiser());

        HBox rangee = new HBox(appliquer, reinitialiser);
        rangee.getStyleClass().add("emplacements-boutons");
        return rangee;
    }

    private void appliquer() {
        try {
            service.enregistrer(travailChoisi.get(), dossierBaseChoisi.get());
            configurationPersonnalisee.set(true);
            afficherAvis();
        } catch (IOException echec) {
            notificateur.notifier(
                    NiveauNotification.AVERTISSEMENT,
                    "Enregistrement impossible",
                    "Les emplacements n'ont pas pu être écrits : " + echec.getMessage());
        }
    }

    private void reinitialiser() {
        try {
            service.reinitialiser();
            Emplacements defaut = service.emplacementsCourants();
            travailChoisi.set(defaut.espaceDeTravail());
            dossierBaseChoisi.set(defaut.base().getParent());
            configurationPersonnalisee.set(false);
            afficherAvis();
        } catch (IOException echec) {
            notificateur.notifier(
                    NiveauNotification.AVERTISSEMENT,
                    "Réinitialisation impossible",
                    "Les emplacements n'ont pas pu être rétablis : " + echec.getMessage());
        }
    }

    private VBox avisRedemarrage() {
        // Avis d'action partagé (#2258) : le bouton « Quitter » lit la sortie COURANTE (remplaçable en
        // test), d'où le passage par une lambda plutôt que la référence directe `sortie`.
        VBox avis = AvisRedemarrage.creer(
                "Le nouvel emplacement s'appliquera au prochain démarrage de l'application.", () -> sortie.run());
        avis.setVisible(false);
        avis.setManaged(false);
        return avis;
    }

    private void afficherAvis() {
        avisRedemarrage.setVisible(true);
        avisRedemarrage.setManaged(true);
    }

    private void masquerAvis() {
        if (avisRedemarrage != null) {
            avisRedemarrage.setVisible(false);
            avisRedemarrage.setManaged(false);
        }
    }

    private Window fenetre() {
        if (racine == null || racine.getScene() == null) {
            return null;
        }
        return racine.getScene().getWindow();
    }

    /// Remplace la désignation de dossier (double répondant en test).
    void definirSelecteur(SelecteurFichier remplacant) {
        selecteur.definir(remplacant);
    }

    /// Remplace le compte rendu (double capturant en test).
    void definirNotificateur(Notificateur remplacant) {
        notificateur.definir(remplacant);
    }

    /// Remplace la sortie de l'application (double capturant en test, pour ne pas tuer TestFX).
    void definirSortie(Runnable remplacant) {
        this.sortie = Objects.requireNonNull(remplacant, "sortie");
    }

    /// Zone racine du formulaire, exposée aux tests pour vérifier l'avis de redémarrage.
    Region racineTest() {
        return racine;
    }
}
