package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.di.Amorcage;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.BilanRestauration;
import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import fr.univ_amu.iut.commun.persistence.PlacementRacine;
import fr.univ_amu.iut.commun.view.ActionAPropos;
import fr.univ_amu.iut.commun.view.AlerteDemarrage;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.ContenuChoixSauvegarde;
import fr.univ_amu.iut.commun.view.GardeQuitter;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.NotificationDialogue;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
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
        enregistrerRestaurationDeplacee(sortie.resolve("apercu-restauration-nuits-deplacees.png"));
        enregistrerSauvegardeTropRecente(sortie.resolve("apercu-restauration-version-trop-recente.png"));
        enregistrerChoixSauvegarde(sortie.resolve("apercu-restauration-choix-sauvegarde.png"));
    }

    /// Compte rendu d'une restauration complète qui a **déplacé** des nuits (#2727).
    ///
    /// C'est le plus exposé des comptes rendus du produit : un compte, un paragraphe, puis **deux
    /// listes** de chemins, servis juste après un geste destructeur. Le texte n'est pas recopié, il
    /// vient de [BilanRestauration#enClair] ; seuls les chemins sont figés, pour que l'aperçu ne
    /// change pas à chaque régénération.
    private static void enregistrerRestaurationDeplacee(Path fichier) {
        BilanRestauration bilan = new BilanRestauration(
                true,
                List.of(
                        new PlacementRacine(
                                "/media/disque-terrain/Car640380-2026-Pass2-Z1",
                                "/home/naturaliste/Documents/VigieChiro-Companion/Car640380-2026-Pass2-Z1"),
                        new PlacementRacine(
                                "/media/disque-terrain/Car130711-2026-Pass1-A1",
                                "/home/naturaliste/Documents/VigieChiro-Companion/Car130711-2026-Pass1-A1")),
                List.of("/media/carte-sd/Car640380-2026-Pass3-Z2"));
        enregistrerCompteRendu(
                NiveauNotification.AVERTISSEMENT,
                "Sauvegarde restaurée, à un détail près",
                "La base et les dossiers de son ont été restaurés.\n\n" + bilan.enClair(),
                fichier);
    }

    /// Refus d'une sauvegarde écrite par une version plus récente (#2730). Rien n'a été touché, et
    /// c'est ce que l'aperçu doit rendre lisible.
    private static void enregistrerSauvegardeTropRecente(Path fichier) {
        enregistrerCompteRendu(
                NiveauNotification.AVERTISSEMENT,
                "Restauration impossible",
                "Cette sauvegarde a été écrite par une version plus récente de l'application (schéma 41,"
                        + " cette version connaît le 38). Rien n'a été touché. Mettez l'application à jour,"
                        + " puis recommencez.",
                fichier);
    }

    /// Rend le dialogue **de production** du port de compte rendu ([NotificationDialogue#dialogue]),
    /// sans l'ouvrir.
    private static void enregistrerCompteRendu(NiveauNotification niveau, String entete, String message, Path fichier) {
        Alert alerte = new NotificationDialogue().dialogue(niveau, entete, ApercuFx.enrouler(message));
        alerte.getDialogPane().setPrefWidth(620);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println(TRACE + fichier.toAbsolutePath());
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

    /// Fenêtre « quelle sauvegarde restaurer ? » (#3197).
    ///
    /// Elle remplace un `FileChooser` **natif**, où l'utilisateur désignait un `.db` sans voir ni sa
    /// date, ni sa taille, ni ce que l'ensemble occupait - au moment précis où le geste écrase sa base.
    /// Les entrées sont figées ici pour que l'aperçu ne change pas à chaque régénération, mais leurs
    /// **natures** sont les trois réelles : c'est le filet de migration, celui que personne n'a demandé,
    /// qui explique le total.
    private static void enregistrerChoixSauvegarde(Path fichier) {
        List<InventaireSauvegardes.Entree> entrees = List.of(
                new InventaireSauvegardes.Entree(
                        "vigiechiro-sauvegarde-20260801-101500.db",
                        Instant.parse("2026-08-01T10:15:00Z"),
                        412L * 1024 * 1024,
                        InventaireSauvegardes.Nature.BASE),
                new InventaireSauvegardes.Entree(
                        "vigiechiro-avant-migration-V39.db",
                        Instant.parse("2026-07-02T08:00:00Z"),
                        398L * 1024 * 1024,
                        InventaireSauvegardes.Nature.FILET_MIGRATION),
                new InventaireSauvegardes.Entree(
                        "vigiechiro-avant-migration-V37.db",
                        Instant.parse("2026-05-14T21:30:00Z"),
                        351L * 1024 * 1024,
                        InventaireSauvegardes.Nature.FILET_MIGRATION),
                new InventaireSauvegardes.Entree(
                        "vigiechiro-sauvegarde-complete-20260410-090000",
                        Instant.parse("2026-04-10T09:00:00Z"),
                        6L * 1024 * 1024 * 1024,
                        InventaireSauvegardes.Nature.COMPLETE));
        ContenuChoixSauvegarde contenu = new ContenuChoixSauvegarde(entrees, entree -> {}, () -> {}, () -> {});
        Scene scene = new Scene(contenu.racine());
        scene.getStylesheets().addAll(styles());
        ApercuFx.enregistrerPng(scene, fichier);
    }

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
