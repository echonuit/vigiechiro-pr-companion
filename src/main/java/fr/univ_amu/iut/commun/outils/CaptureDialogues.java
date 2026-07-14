package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;

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
        enregistrer(dialogueDoublon(), sortie.resolve("apercu-import-doublon.png"));
        enregistrer(dialogueEcrasement(), sortie.resolve("apercu-import-ecrasement.png"));
        enregistrer(alerteGardeSaisie(), sortie.resolve("apercu-navigation-garde-saisie.png"));
    }

    private static void enregistrer(Dialog<?> dialogue, Path fichier) {
        ApercuFx.enregistrerDialog(dialogue, styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Confirmation « nuit déjà importée » (#147) : même formulation que [importation `ConfirmationsImport`].
    private static Dialog<?> dialogueDoublon() {
        return confirmation("Cette nuit (carré 640380, point A1, passage n°2, 2026) semble déjà\n"
                + "avoir été importée le 22/06/2026.\n\n"
                + "Importer quand même comme nouveau passage ?");
    }

    /// Seconde confirmation d'**écrasement** destructif (#279) : le détail de ce qui est définitivement perdu.
    private static Dialog<?> dialogueEcrasement() {
        return confirmation("⚠ Suppression DÉFINITIVE du passage existant et de ses 342 séquence(s).\n"
                + "Dont 87 validation(s) Tadarida (correction, référence, commentaire)\n"
                + "définitivement perdue(s). Action irréversible.\n\n"
                + "Confirmer l'écrasement ?");
    }

    /// Garde « quitter sans enregistrer » (#178) : le message par défaut présenté par le Navigateur avant de
    /// quitter un écran à saisie non enregistrée.
    private static Dialog<?> alerteGardeSaisie() {
        return confirmation("Des modifications non enregistrées seront perdues.\n" + "Quitter cet écran quand même ?");
    }

    /// Confirmation « CONFIRMATION + OK / Annuler » (le patron des confirmations de l'application). Le
    /// message est **pré-découpé** par des `\n` : hors `showAndWait`, le `DialogPane` d'un snapshot ne
    /// contraint pas la largeur, donc l'enroulement automatique n'opère pas — on maîtrise les retours à la
    /// ligne pour que tout le texte soit visible.
    private static Dialog<?> confirmation(String message) {
        Dialog<Void> dialogue = new Dialog<>();
        dialogue.setHeaderText("Confirmation");
        dialogue.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogue.getDialogPane().setContent(new Label(message));
        return dialogue;
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
