package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les **dialogues programmatiques** (confirmations et modales de saisie) que le harness de
/// captures d'écran ne pouvait pas illustrer avant [ApercuFx#enregistrerDialog] (#534) : ces modales
/// sont montrées par `showAndWait` et n'ont pas de `.fxml`. On les **reconstruit** ici à l'identique
/// (mêmes libellés, mêmes boutons) avec des données de démo, puis on les rend hors-écran en appliquant
/// les feuilles de style partagées (palette + base), sans jamais ouvrir de fenêtre modale.
///
/// Trois états, rattachés dans le manifeste à la **vue parente** de chaque dialogue :
/// - `apercu-import-doublon.png` / `apercu-import-ecrasement.png` : confirmations d'import (#147/#279) ;
/// - `apercu-navigation-garde-saisie.png` : garde « quitter sans enregistrer » (#178).
///
/// Reconstruction en JavaFX pur (aucun type de feature) pour rester dans `commun` sans dépendre de
/// `sites` / `qualification` / `importation` (contrainte de dépendances). Lancement headless :
/// `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureDialogues {

    private CaptureDialogues() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException probleme) {
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

    private static void capturer() {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        enregistrer(messageGardeSaisie(), sortie.resolve("apercu-navigation-garde-saisie.png"));
    }

    /// Rend le dialogue **de production** ([ConfirmationNavigation#dialogue]) portant le message réel.
    ///
    /// Seule la **largeur** est imposée : hors `showAndWait`, un `DialogPane` ne contraint pas la sienne, et
    /// le texte s'étalerait sur une ligne interminable. On ne touche pas au texte.
    private static void enregistrer(String message, Path fichier) {
        // Le dialogue est celui de la PRODUCTION ([ConfirmationNavigation#dialogue]) : même type, mêmes
        // boutons, même titre. Seul le message est enroulé (cf. [#enrouler]).
        Alert alerte = new ConfirmationNavigation().dialogue(ApercuFx.enrouler(message));
        alerte.getDialogPane().setPrefWidth(540);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Garde « quitter sans enregistrer » (#178), avec le **vrai** message : celui que [GardeQuitter]
    /// présente réellement, lu à la source (#1468). Il était recopié à la main ici - « à l'identique »,
    /// disait le commentaire, ce qui n'engageait personne.
    private static String messageGardeSaisie() {
        return new GardeQuitter() {
            @Override
            public boolean aSaisieNonEnregistree() {
                return true;
            }
        }.messageConfirmationQuitter();
    }

    /// Feuilles de style partagées (palette + base, dans `commun/view`) pour que les dialogues aient le
    /// même thème indigo que l'application. Les CSS spécifiques aux features ne sont pas appliquées (elles
    /// introduiraient une dépendance interdite depuis `commun`).
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css")) {
            var url = ConfirmationNavigation.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        return feuilles;
    }
}
