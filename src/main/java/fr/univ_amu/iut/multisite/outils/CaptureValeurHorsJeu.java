package fr.univ_amu.iut.multisite.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.multisite.view.MultisiteController;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Photographie l'état **« cette valeur ne ramène rien »** du critère Lieu (#3169) : une valeur cochée
/// qui sort du jeu offert **reste cochée**, rendue en fin de liste et grisée en italique
/// ([fr.univ_amu.iut.commun.view.CritereListe#CLASSE_VALEUR_HORS_JEU]).
///
/// C'est la garantie **visible** que le socle ne relâche pas un filtre tout seul : la retirer
/// élargirait la vue en silence, le défaut que tout le palier 1 du chantier #3092 a corrigé. Elle
/// n'avait aucune capture, faute d'un jeu de démonstration sachant la produire.
///
/// ## Pourquoi « Carte & passages » plutôt que « Sons & validation »
///
/// L'état demande qu'un lieu **coché** sorte du jeu offert **pendant qu'il reste des lignes**. La graine
/// de « Sons & validation » ne pose qu'un carré et qu'un point : avec une seule valeur par dimension,
/// soit tout reste, soit tout part. L'étendre aurait déplacé douze aperçus (#3169).
///
/// Celle de « Carte & passages » porte déjà ce qu'il faut, sans rien changer : le point **640381 · B2**
/// n'a que des nuits de **2026**, quand **640380 · A1** en a une de **2025**. Cocher B2 puis restreindre
/// à 2025 fait sortir B2 du jeu en laissant la ligne de A1.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh`.
public final class CaptureValeurHorsJeu {

    private static final String LIEU_A_COCHER = "640381 · B2";
    private static final String ANNEE_QUI_LE_FAIT_SORTIR = "2025";

    private CaptureValeurHorsJeu() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Injector injecteur = CaptureMultisite.preparer();
        Path fichier =
                Path.of(System.getProperty("capture.outDir", ".github/assets")).resolve("apercu-valeur-hors-jeu.png");

        FXMLLoader loader = new FXMLLoader(MultisiteController.class.getResource("Multisite.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        // Une scene montee : sans elle, les puces ne calculent aucune largeur et le menu reste vide.
        new Scene(vue, 1100, 620).getRoot().applyCss();

        MenuButton puceLieu = poserPuce(vue, "Lieu", ".critere-multiple");
        peupler(puceLieu);
        cocher(puceLieu, LIEU_A_COCHER);

        poserPuce(vue, "Année", null);
        saisirAnnee(vue);

        // Second peuplement : c'est LUI qui produit l'etat. Le domaine se recalcule sur les lignes que
        // les AUTRES criteres laissent passer (#3095), B2 n'y figure plus, et la valeur cochee bascule
        // en fin de liste avec sa classe « valeur-hors-jeu ».
        peupler(puceLieu);
        exigerHorsJeu(puceLieu);

        if (!ApercuFx.enregistrerMenuOuvert(puceLieu, fichier)) {
            System.out.println("[capture-valeur-hors-jeu] popup non rendu (headless) : " + fichier + " ignore.");
            return;
        }
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Ajoute la puce `libelle` par le menu « + Filtre », et rend son `MenuButton` quand elle en a un.
    private static MenuButton poserPuce(Parent vue, String libelle, String classeCss) {
        if (!(vue.lookup("#menuAjoutFiltre") instanceof MenuButton menuAjout)) {
            throw new IllegalStateException("Menu « + Filtre » introuvable : aucune puce ne peut etre posee.");
        }
        ApercuFx.exigerParLibelle("le menu « + Filtre »", menuAjout.getItems(), MenuItem::getText, libelle)
                .fire();
        if (classeCss == null) {
            return null;
        }
        return vue.lookupAll(classeCss).stream()
                .filter(MenuButton.class::isInstance)
                .map(MenuButton.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Puce « " + libelle + " » absente apres son ajout."));
    }

    /// Declenche le peuplement du menu, que le socle fait a l'ouverture et non a la creation (#3095).
    private static void peupler(MenuButton puce) {
        EventHandler<Event> ouverture = puce.getOnShowing();
        if (ouverture == null) {
            throw new IllegalStateException("La puce ne peuple pas son menu a l'ouverture : rien a photographier.");
        }
        ouverture.handle(new Event(Event.ANY));
    }

    private static void cocher(MenuButton puce, String valeur) {
        puce.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> valeur.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Valeur « " + valeur
                        + " » absente du menu Lieu : la graine ne produit plus l'etat attendu. Offert : "
                        + puce.getItems().stream().map(MenuItem::getText).toList()))
                // `setSelected` et non `fire()` : le socle ecoute `selectedProperty`, c'est donc ce
                // chemin-la qui applique le predicat (CritereListe#entree).
                .setSelected(true);
    }

    /// Saisit l'annee dans l'editeur de la puce « Annee », c'est-a-dire par le meme chemin que
    /// l'utilisateur : le champ publie son predicat sur sa propriete texte.
    private static void saisirAnnee(Parent vue) {
        Node puces = vue.lookup("#pucesFiltres");
        if (puces == null) {
            throw new IllegalStateException("Conteneur des puces introuvable.");
        }
        puces.lookupAll(".text-field").stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .reduce((premier, dernier) -> dernier)
                .orElseThrow(() -> new IllegalStateException("Editeur de la puce « Annee » introuvable."))
                .setText(ANNEE_QUI_LE_FAIT_SORTIR);
    }

    /// Refuse d'ecrire une image qui ne montrerait pas l'etat annonce.
    ///
    /// Sans ce controle, un changement de graine ou de libelle rendrait une capture d'apparence normale
    /// sous une legende qui parle d'un marquage absent - le mode de panne d'une capture qui ment.
    private static void exigerHorsJeu(MenuButton puce) {
        boolean marquee =
                puce.getItems().stream().anyMatch(item -> item.getStyleClass().contains("valeur-hors-jeu"));
        if (!marquee) {
            throw new IllegalStateException("Menu apres restriction : "
                    + puce.getItems().stream().map(MenuItem::getText).toList()
                    + " - aucune valeur marquee « hors jeu » dans le menu Lieu :"
                    + " la capture montrerait autre chose que ce que sa legende annonce.");
        }
    }

    /// Injecteur (partiel) utilise par cet outil, expose pour le garde-fou de cablage (test).
    public static Injector creerInjecteur() {
        return CaptureMultisite.creerInjecteur();
    }
}
