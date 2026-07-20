package fr.univ_amu.iut.commun.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;

/// Aperçu du **menu ☰ du chrome**, déployé (#2144, #2108).
///
/// Ce menu n'avait aucune capture, alors qu'il porte une douzaine d'entrées contribuées par le socle
/// et les features - sauvegarde, restauration, purge, réglages, journaux, « À propos ». Chacune
/// pouvait donc changer de libellé, perdre son icône ou disparaître sans qu'aucun aperçu ne le
/// montre : le menu le plus riche de l'application était le seul angle mort de la galerie.
///
/// Le menu photographié est **le vrai**. On monte le chrome complet, on laisse
/// `ConstructeurMenuOutils` le peupler depuis les `ActionMenu` réellement injectées, puis
/// [ApercuFx#enregistrerMenuOuvert] déploie ce `MenuButton`-là. Rien n'est reconstruit à la main :
/// une capture qui recompose son sujet finit par mentir sur le produit (ADR 0025), et le dépôt en a
/// déjà fait l'expérience.
public final class CaptureMenuOutils {

    private static final String CHROME = "/fr/univ_amu/iut/commun/view/MainView.fxml";

    /// `fx:id` du bouton de menu dans le chrome. Une faute ici ne casse pas la compilation : elle
    /// donne un aperçu manquant, d'où l'échec explicite plus bas.
    private static final String ID_MENU = "menuOutils";

    private CaptureMenuOutils() {}

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
        Throwable probleme = erreur.get();
        if (probleme != null) {
            probleme.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-menu-outils");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = CaptureAccueil.creerInjecteur();
        // Le chrome interroge la base au montage (compteurs du tableau de bord) : sans schéma, le
        // chargement du FXML échoue sur « no such table ».
        new fr.univ_amu.iut.commun.persistence.MigrationSchema(injecteur.getInstance(SourceDeDonnees.class)).migrer();

        Parent chrome = chargerChrome(injecteur);
        // Le menu doit appartenir à une scène affichée pour que son popup se rende : un MenuButton
        // détaché n'a pas de fenêtre où se déployer.
        Stage stage = new Stage();
        stage.setScene(new Scene(chrome, 1180, 760));
        stage.show();

        MenuButton menu = (MenuButton) chrome.lookup("#" + ID_MENU);
        if (menu == null) {
            throw new IllegalStateException(
                    "Bouton de menu « " + ID_MENU + " » introuvable dans le chrome : fx:id renommé ?");
        }

        Path fichier = sortie.resolve("apercu-menu-outils.png");
        if (ApercuFx.enregistrerMenuOuvert(menu, fichier)) {
            System.out.println("Apercu du menu outils ecrit dans " + fichier.toAbsolutePath());
        } else {
            System.out.println("[capture-menu] popup non rendu : " + fichier + " ignore.");
        }
    }

    private static Parent chargerChrome(Injector injecteur) throws IOException {
        FXMLLoader loader = new FXMLLoader(CaptureMenuOutils.class.getResource(CHROME));
        loader.setControllerFactory(injecteur::getInstance);
        return loader.load();
    }
}
