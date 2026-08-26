package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.RenduPng;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Brique reutilisable qui rend une [Scene] JavaFX hors-ecran et l'ecrit en PNG, sans laisser de
/// fenetre a l'ecran. Le patron : placer un ecran (ou le chrome complet) dans une `Scene`, forcer
/// sa mise en page, capturer via [javafx.scene.Scene#snapshot] puis convertir en image AWT par
/// [SwingFXUtils] (d'ou la dependance `javafx.swing`). A appeler sur le thread JavaFX.
///
/// Toute feature peut s'en servir pour produire les apercus de ses ecrans (cf.
/// `fr.univ_amu.iut.sites.outils.CaptureEcrans`).
public final class ApercuFx {

    /// Classe CSS par laquelle un FXML **assume** qu'un libelle se raccourcisse quand la place
    /// manque. Le critere vit dans [LisibiliteCapture] ; la constante reste ici, ou les outils de
    /// capture la cherchent depuis toujours.
    public static final String ABREGEABLE = LisibiliteCapture.ABREGEABLE;

    private ApercuFx() {}

    /// L'element dont le libelle vaut `attendu`, ou une **erreur** nommant ce qui etait offert.
    ///
    /// Les outils de capture designent leurs controles par leur libelle (« Lieu », « Taxon parent »,
    /// « Apparence »), parce que c'est ce que l'utilisateur voit. Ils le cherchaient avec
    /// `findFirst().ifPresent(...)`, qui **s'abstient en silence** : un libelle renomme laissait alors
    /// produire l'apercu **sans le geste**, publie sous une legende affirmant le contraire. Rien ne
    /// distingue une capture fausse d'une bonne, et la galerie porte cette image jusqu'a ce que
    /// quelqu'un la regarde.
    ///
    /// Le message nomme les libelles **presents**, parce que la correction consiste toujours a lire la
    /// liste : le libelle a change, il n'a pas disparu.
    ///
    /// @param ou ce qu'on fouillait, pour situer l'erreur (« le menu + Filtre », « les onglets »)
    public static <T> T exigerParLibelle(String ou, List<T> candidats, Function<T, String> libelle, String attendu) {
        return candidats.stream()
                .filter(candidat -> attendu.equals(libelle.apply(candidat)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("« " + attendu + " » introuvable dans " + ou
                        + " : la capture montrerait un ecran sans ce geste. Libelles presents : "
                        + candidats.stream().map(libelle).toList()));
    }

    /// Capture `scene` hors-ecran et l'ecrit en PNG dans `fichier` (cree les dossiers parents).
    ///
    /// La scene est attachee a un [Stage] transitoire que l'on montre brievement : cela garantit une
    /// passe de layout/CSS complete (les controles virtualises comme `TableView` peuplent leurs
    /// lignes) avant le `snapshot`, qui reste deterministe. Le stage est referme aussitot.
    public static void enregistrerPng(Scene scene, Path fichier) {
        preparerRendu(scene);
        Stage stageTransitoire = new Stage();
        stageTransitoire.setScene(scene);
        stageTransitoire.show();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        // Ce contrôle reste ICI et n'a pas suivi dans [RenduPng] (#2746) : une capture de
        // documentation ne doit jamais partir avec un libellé tronqué, mais un export utilisateur
        // porte sur une scène redessinée que personne ne peut corriger. Voir RenduPng.
        LisibiliteCapture.refuserToutTexteIllisible(scene);
        // Mesure PENDANT que la scene est en place (des bornes n'ont de sens qu'apres layout), ecriture
        // APRES la fermeture du stage. Cf. [#deposerZoneCarte] : y toucher au disque est deja trop.
        String zoneCarte = ZoneCarteApercu.rectangleDe(scene).orElse(null);
        WritableImage image = scene.snapshot(null);
        stageTransitoire.hide();
        RenduPng.ecrire(image, fichier);
        ZoneCarteApercu.deposer(zoneCarte, fichier);
    }

    /// Met la scene dans l'etat ou l'application la montre : police embarquee et feuilles de socle.
    ///
    /// ## Ce que `Typographie.installer()` ne suffisait pas a faire
    ///
    /// Enregistrer une famille de police ne la **selectionne** pas : c'est `base.css` qui la demande.
    /// Or les outils de capture montent la vue **seule**, sans le chrome qui la portait.
    ///
    /// **Mesure (2026-08-06)** : apres #3364, la CI a regenere 138 apercus ; seuls les 37 qui montent
    /// le chrome ont change. `apercu-saison.png` et `apercu-audit.png` sont ressortis identiques **au
    /// bit pres**, leur ecart CI/local intact a 28,8 % des pixels. Sur `apercu-accueil.png`, qui porte
    /// le chrome, l'ecart CI/local est tombe a **zero pixel**. La police embarquee marchait ; elle
    /// n'atteignait pas ces scenes-la.
    ///
    /// En passant par [Habillage] - le meme que les fenetres de l'application - un apercu montre
    /// l'ecran tel qu'il est vu, par construction (ADR 3374).
    private static void preparerRendu(Scene scene) {
        Habillage.poser(scene);
    }

    /// Variante de [#enregistrerPng] pour les scenes dont le contenu se prepare de facon
    /// **asynchrone** (p. ex. une `AudioView` qui charge un WAV en fond et peint un spectrogramme).
    ///
    /// Le [Stage] transitoire est montre **avant** d'executer `preparation`, qui peut attendre la fin
    /// du chargement via une boucle d'evenements imbriquee. On `snapshot` ensuite la scene **sans
    /// recreer de Stage** : c'est essentiel car la Headless Platform JavaFX 26 refuse un `new Stage()`
    /// apres `enterNestedEventLoop` (le toolkit est laisse dans un etat ou son controle de thread
    /// echoue). En montrant l'unique Stage avant la boucle, on contourne ce defaut. A appeler sur le
    /// thread JavaFX.
    public static void capturerApresPreparation(Scene scene, Runnable preparation, Path fichier) {
        // Ce point d'entree n'installait meme pas la police : les captures asynchrones (AudioView)
        // echappaient donc entierement a #3364.
        preparerRendu(scene);
        Stage stageTransitoire = new Stage();
        stageTransitoire.setScene(scene);
        stageTransitoire.show();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        preparation.run();
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        LisibiliteCapture.refuserToutTexteIllisible(scene);
        String zoneCarte = ZoneCarteApercu.rectangleDe(scene).orElse(null);
        WritableImage image = scene.snapshot(null);
        stageTransitoire.hide();
        RenduPng.ecrire(image, fichier);
        ZoneCarteApercu.deposer(zoneCarte, fichier);
    }

    /// Capture un **menu ouvert** (le popup d'un [MenuButton]) hors-écran, et l'écrit en PNG.
    ///
    /// Un `MenuButton` fermé n'affiche que son bouton : les entrées qu'il contient - leurs libellés, leurs
    /// icônes, leurs grisages - ne se voient sur **aucun** aperçu. Or c'est là que se logent les défauts que
    /// seule une capture révèle : un glyphe qui ne se rend pas, un libellé trop long, une entrée restée
    /// active alors qu'elle devrait être grisée.
    ///
    /// Le menu montré est **le vrai** : ses items sont repris tels quels dans un [ContextMenu] transitoire,
    /// jamais reconstruits. Textes, visibilités et grisages restent donc ceux de l'application, ce qu'une
    /// reconstruction à l'identique ne garantirait pas (ADR 0025 : une capture passe par le code de
    /// production, elle ne le reconstruit pas).
    ///
    /// Le menu source **n'est pas altéré**, ce que la copie défensive et [ApercuFxMenuTest] tiennent :
    /// un appelant peut capturer autre chose ensuite, et `CaptureMultisite` le fait.
    ///
    /// En *headless*, un popup peut ne pas se rendre. La méthode renvoie alors `false` sans rien écrire,
    /// à charge pour l'appelant de le dire et de continuer : un aperçu manquant ne doit pas faire échouer
    /// tout un job de capture.
    ///
    /// Le popup se rend dans la scène de son **hôte** : les feuilles de style du menu y sont reportées
    /// (#3169), sans quoi une entrée qui porte son sens par une classe CSS - « hors jeu », grisée et en
    /// italique - se capture identique aux autres. Un garde d'appelant ne voit pas cet écart : il
    /// vérifie que la classe est posée, pas que quelque chose a changé à l'écran.
    ///
    /// @param menu le bouton de menu dont on veut montrer le contenu déployé
    /// @param fichier le PNG à écrire
    /// @return `true` si l'aperçu a été écrit, `false` si le popup ne s'est pas rendu
    public static boolean enregistrerMenuOuvert(MenuButton menu, Path fichier) {
        List<MenuItem> items = List.copyOf(menu.getItems());
        ContextMenu apercu = new ContextMenu();
        apercu.getItems().addAll(items);

        Stage hote = new Stage();
        Scene sceneHote = new Scene(new javafx.scene.layout.StackPane(), 500, 300);
        feuillesDe(menu).forEach(sceneHote.getStylesheets()::add);
        // Apres les feuilles heritees : `base.css` se glisse derriere `palette.css`, donc devant
        // aucune d'elles (#3374).
        Habillage.poser(sceneHote);
        hote.setScene(sceneHote);
        hote.show();
        apercu.show(hote);
        try {
            Scene scenePopup = apercu.getScene();
            if (scenePopup == null || scenePopup.getRoot() == null) {
                return false;
            }
            javafx.scene.Parent racine = scenePopup.getRoot();
            racine.applyCss();
            racine.layout();
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
            RenduPng.ecrire(racine.snapshot(params, null), fichier);
            return true;
        } finally {
            apercu.hide();
            hote.hide();
            apercu.getItems().clear();
        }
    }

    /// Capture un [javafx.scene.control.DialogPane] hors-ecran en l'enveloppant dans une scene transitoire
    /// et en y appliquant des feuilles de styles (comme palette.css). A appeler sur le thread JavaFX.
    /// Largeur d'enroulement des messages de dialogue, en caractères.
    private static final int LARGEUR_LIGNE = 70;

    /// Insère des retours à la ligne dans un message de dialogue, **sans en changer un mot**.
    ///
    /// Hors `showAndWait`, un `DialogPane` ne contraint pas sa largeur : son libellé reste sur une ligne
    /// unique, que le snapshot coupe par une ellipse. L'enroulement automatique de JavaFX n'opère pas dans
    /// ce contexte - c'est la raison pour laquelle les anciennes captures **réécrivaient** leurs messages,
    /// retours à la ligne compris. Ici, on part du **vrai** message et on se contente de le **couper aux
    /// espaces** : aucun mot n'est ajouté, retiré ni modifié.
    ///
    /// Les retours à la ligne **déjà présents** sont préservés : chaque paragraphe est enroulé pour lui
    /// même. Sans cela, un message en plusieurs paragraphes (celui de la publication, #1865) verrait ses
    /// coupures comptées comme des mots et son découpage partir de travers.
    ///
    /// Vit ici plutôt que dans un outil : c'est une contrainte du **harnais de capture**, pas d'un écran,
    /// et deux outils en ont désormais besoin.
    public static String enrouler(String message) {
        List<String> paragraphes = new ArrayList<>();
        for (String paragraphe : message.split("\n", -1)) {
            paragraphes.add(enroulerParagraphe(paragraphe));
        }
        return String.join("\n", paragraphes);
    }

    private static String enroulerParagraphe(String paragraphe) {
        StringBuilder enroule = new StringBuilder();
        int longueurLigne = 0;
        for (String mot : paragraphe.split(" ")) {
            if (longueurLigne > 0 && longueurLigne + mot.length() > LARGEUR_LIGNE) {
                enroule.append('\n');
                longueurLigne = 0;
            } else if (longueurLigne > 0) {
                enroule.append(' ');
                longueurLigne++;
            }
            enroule.append(mot);
            longueurLigne += mot.length();
        }
        return enroule.toString();
    }

    /// Les feuilles de style qui gouvernent `menu`, à reporter sur l'hôte du popup.
    ///
    /// Lues sur la vue plutôt que codées en dur : l'outil de capture monte déjà le vrai écran, avec les
    /// feuilles que son FXML lui donne. Nommer `design.css` ici en ferait une seconde source de vérité,
    /// qui divergerait le jour où un écran en ajoute une - `Multisite.fxml` en déclare **trois**.
    ///
    /// Elles se cherchent **sur les ancêtres autant que sur la scène** : l'attribut `stylesheets` d'un
    /// FXML garnit le nœud **racine** ([javafx.scene.Parent#getStylesheets]), pas la scène. Ne regarder
    /// que la scène rendait une liste vide sur tous les écrans du dépôt - premier correctif de cette
    /// méthode, écrit puis mesuré faux.
    static List<String> feuillesDe(MenuButton menu) {
        List<String> feuilles = new java.util.ArrayList<>();
        if (menu.getScene() != null) {
            feuilles.addAll(menu.getScene().getStylesheets());
        }
        for (javafx.scene.Node noeud = menu; noeud != null; noeud = noeud.getParent()) {
            if (noeud instanceof javafx.scene.Parent parent) {
                parent.getStylesheets().stream()
                        .filter(f -> !feuilles.contains(f))
                        .forEach(feuilles::add);
            }
        }
        return List.copyOf(feuilles);
    }

    /// Capture un [javafx.scene.control.DialogPane] hors-ecran, sur un fond qui simule la modalite.
    ///
    /// Cette methode prenait la liste des feuilles de style en parametre, et ses huit appelants y
    /// passaient tous le meme couple `palette.css` + `base.css`, recopie chez chacun. Depuis #3374,
    /// [Habillage] pose cette paire lui-meme, **au niveau ou la palette vit** - ce que huit copies
    /// independantes ne pouvaient pas garantir. Le parametre n'offrait donc plus qu'une facon de se
    /// tromper : il a ete retire, et les huit helpers avec.
    public static void enregistrerDialogPane(javafx.scene.control.DialogPane pane, Path fichier) {
        javafx.scene.layout.StackPane conteneur = new javafx.scene.layout.StackPane(pane);
        // Fond sombre translucide pour simuler le background de l'application modale
        conteneur.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4); -fx-padding: 30;");
        Scene scene = new Scene(conteneur);
        Habillage.poser(scene);
        // Et le panneau lui-même (#1499) : habiller la scène hôte ne suffit pas. Un `DialogPane` porte
        // ses propres feuilles, et c'est là que vivent les règles `.dialog-pane`. Sans cette ligne, la
        // capture montrait le rendu Modena par défaut - icône système comprise - alors que le dialogue
        // de production, lui, était habillé : l'aperçu mentait dans le sens rassurant inverse.
        Habillage.poser(pane);
        // applyCss() AVANT layout() (#1468) : sans passe CSS, les libellés n'ont pas encore leurs métriques
        // de police, et un texte à enrouler reste sur une ligne unique - que le snapshot coupe par une
        // ellipse. C'est ce qui obligeait les captures de dialogue à pré-découper leurs messages à la main.
        pane.applyCss();
        pane.layout();
        enregistrerPng(scene, fichier);
    }

    /// Capture un [javafx.scene.control.Dialog] hors-ecran en extrayant son [DialogPane].
    /// A appeler sur le thread JavaFX.
    public static void enregistrerDialog(javafx.scene.control.Dialog<?> dialog, Path fichier) {
        enregistrerDialogPane(dialog.getDialogPane(), fichier);
    }
}
