package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.di.Amorcage;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.ActionAPropos;
import fr.univ_amu.iut.commun.view.AlerteDemarrage;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

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

    /// Trace commune des trois ecritures : PMD refuse le litteral repete, et un seul endroit
    /// suffit pour changer la forme de la trace.
    private static final String TRACE = "Apercu ecrit dans ";

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
        enregistrerAPropos(sortie.resolve("apercu-a-propos.png"));
        enregistrerDossierOccupe(sortie.resolve("apercu-demarrage-dossier-occupe.png"));
    }

    /// Refus de démarrage quand une autre fenêtre occupe déjà le dossier de travail (#2731).
    ///
    /// Le dialogue et la phrase viennent tous deux de la **production** ([AlerteDemarrage#dialogue],
    /// [Amorcage#messageDossierOccupe]) : rien n'est recopié ici, et une reformulation du refus
    /// changera l'aperçu toute seule (ADR 0025).
    ///
    /// L'**occupant** est en revanche figé. Le vrai porte un identifiant de processus et un
    /// horodatage : les rendre ferait changer l'aperçu à chaque régénération, et un diff qui bouge
    /// sans que rien n'ait bougé finit par ne plus être lu. Même compromis que l'aperçu « À propos »,
    /// qui fige le dossier de travail.
    private static void enregistrerDossierOccupe(Path fichier) {
        String occupantFige = "processus 4821, depuis 2026-08-03T21:14:07";
        Alert alerte = AlerteDemarrage.dialogue(
                "VigieChiro Companion est déjà ouvert", ApercuFx.enrouler(Amorcage.messageDossierOccupe(occupantFige)));
        alerte.getDialogPane().setPrefWidth(540);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println(TRACE + fichier.toAbsolutePath());
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
        System.out.println(TRACE + fichier.toAbsolutePath());
    }

    /// Dialogue **« À propos »** (#2144), avec le message que l'action produit réellement.
    ///
    /// Le texte n'est pas recopié : on **exécute** [ActionAPropos] avec un notificateur qui le capte.
    /// Ce fichier porte déjà la trace d'un message recopié « à l'identique » qui avait fini par
    /// diverger (#1468) - une capture qui recompose son sujet ment tôt ou tard (ADR 0025).
    ///
    /// Le dossier de travail est figé : sans cela l'aperçu porterait le chemin de la machine qui l'a
    /// rendu, et changerait à chaque régénération.
    private static void enregistrerAPropos(Path fichier) {
        String resume = new ActionAPropos(
                        new VersionApplication(),
                        new Workspace(Path.of("/home/naturaliste/Documents/VigieChiro-Companion")))
                .resume();
        Alert alerte = new Alert(Alert.AlertType.INFORMATION, ApercuFx.enrouler(resume), ButtonType.OK);
        alerte.setHeaderText(ActionAPropos.ENTETE);
        alerte.getDialogPane().setPrefWidth(540);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println(TRACE + fichier.toAbsolutePath());
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
