package fr.univ_amu.iut.commun.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.ConfigurationAmorcage;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran « Réglages » du socle (`EcranReglages.fxml`, #927) en PNG. Comme l'accueil
/// (cf. [CaptureAccueil]), l'écran appartient au socle `commun` et **agrège** les
// [fr.univ_amu.iut.commun.view.OngletReglages]
/// publiés par les features : on utilise donc l'injecteur applicatif complet
/// ([RacineInjecteur#creer()]). Tant qu'aucune feature ne contribue d'onglet (P1.2), l'aperçu
/// illustre l'état vide (« Aucun réglage disponible ») ; il se remplira quand les features en
/// déclareront (P1.3), sans toucher cet outil.
///
/// Rendu hors-écran par [ApercuFx] dans `.github/assets/`. Lancement headless :
/// `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureEcranReglages {

    private static final String ECRAN = "/fr/univ_amu/iut/commun/view/EcranReglages.fxml";

    private CaptureEcranReglages() {}

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

    /// Rend un onglet nommé dans son propre PNG, à la taille commune de la galerie.
    private static void rendreOnglet(Injector injecteur, String titre, Path fichier) throws IOException {
        rendreOnglet(injecteur, titre, fichier, 760, 520);
    }

    /// Même chose, à une taille choisie : un onglet plus long qu'un autre ne se documente pas dans le
    /// cadre du plus court.
    private static void rendreOnglet(Injector injecteur, String titre, Path fichier, int largeur, int hauteur)
            throws IOException {
        Parent ecran = chargerFxml(injecteur, ECRAN);
        Scene scene = new Scene(ecran, largeur, hauteur);
        selectionnerOnglet(ecran, titre);
        ApercuFx.enregistrerPng(scene, fichier);
    }

    /// Sélectionne l'onglet portant ce titre, pour qu'un aperçu montre autre chose que le premier.
    ///
    /// **Exige** de le trouver : la sélection s'abstenait en silence, si bien qu'un onglet renommé faisait
    /// écrire tous les aperçus sur le PREMIER onglet, chacun sous la légende d'un autre.
    private static void selectionnerOnglet(Parent ecran, String titre) {
        TabPane onglets = (TabPane) ecran.lookup("#onglets");
        onglets.getSelectionModel()
                .select(ApercuFx.exigerParLibelle("les onglets des réglages", onglets.getTabs(), Tab::getText, titre));
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-reglages");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        // #3703 : le dossier d'amorçage se pose comme le workspace, et pour la même raison.
        // L'onglet « Emplacements » ne lit pas que la propriété de workspace : il lit aussi la
        // configuration d'amorçage persistée (ADR 1038), qui vit hors de la base et hors du dossier
        // temporaire. Un poste qui a servi à une recette en a une ; un runner de CI n'en a pas.
        // L'aperçu montrait donc la machine qui le régénère - 6 404 pixels d'écart entre les deux.
        // Et le **dossier personnel** avec, sans quoi la ligne « Par défaut : … » affiche encore le
        // nom d'utilisateur de la machine. C'est la deuxième source d'indéterminisme de cet écran, et
        // elle a survécu à la première correction de #3703 : je l'ai vue en OUVRANT l'image, pas en
        // relisant le code.
        System.setProperty("user.home", "/home/observateur");
        Path amorcage = Files.createTempDirectory("vc-capture-amorcage");
        System.setProperty(ConfigurationAmorcage.PROP_DOSSIER, amorcage.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = RacineInjecteur.creer();
        // Les contrôles de réglages lisent/écrivent la table app_setting : on migre le schéma pour que
        // ReglagesReactifs trouve ses tables (base neuve = tous les défauts).
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        // La base est désormais liée au workspace temporaire (singleton Guice, déjà résolu ci-dessus).
        // On efface la surcharge pour que l'onglet « Emplacements » affiche les emplacements PAR DÉFAUT
        // (`<home>/Documents/VigieChiro-Companion`) plutôt que le dossier temporaire de capture, dont le
        // nom aléatoire ferait diverger l'aperçu à chaque régénération. `emplacementsCourants()` résout
        // les chemins sans toucher la base ; les autres onglets lisent `app_setting` via la source déjà
        // liée, pas via une nouvelle résolution.
        System.clearProperty("vigiechiro.workspace");

        Parent ecran = chargerFxml(injecteur, ECRAN);
        ApercuFx.enregistrerPng(new Scene(ecran, 760, 520), sortie.resolve("apercu-reglages.png"));

        // Un onglet par domaine, et l'aperçu n'en montrait qu'un : le premier. Les réglages des autres
        // onglets n'étaient donc **documentés nulle part** (#2061). On rend aussi l'onglet « Import »,
        // dont l'option de conservation des originaux porte une conséquence que l'utilisateur doit
        // pouvoir lire avant de l'activer.
        // Les onglets qui portent une aide : c'est elle qui dit ce que le réglage engage (#2085), et un
        // aperçu qui ne montre que le premier onglet ne la documente nulle part.
        rendreOnglet(injecteur, "Import", sortie.resolve("apercu-reglages-import.png"));
        rendreOnglet(injecteur, "Dépôt", sortie.resolve("apercu-reglages-depot.png"));
        // Onglet « Emplacements » (#1038) : où vivent le dossier de travail et la base. Il porte
        // l'avertissement « le pointeur change, pas les données », qui doit être lisible avant le choix.
        rendreOnglet(injecteur, "Emplacements", sortie.resolve("apercu-reglages-emplacements.png"));
        // Le MÊME onglet, avec une configuration personnalisée (#3703). C'est le seul état où
        // « Rétablir les emplacements par défaut » est **actif** - #3543 l'a rendu atteignable, et
        // aucun aperçu ne le montrait : sur un runner il n'y a jamais de configuration personnalisée,
        // donc la galerie ne documentait que le bouton grisé.
        new ConfigurationAmorcage(
                        Optional.of(Path.of("/home/observateur/VigieChiro-Companion")),
                        Optional.of(Path.of("/home/observateur/VigieChiro-Companion/vigiechiro.db")))
                .enregistrerDans(amorcage);
        // MEME injecteur : en creer un neuf ici resoudrait le workspace par defaut - le vrai dossier de
        // l utilisateur - puisque la surcharge vient d etre effacee.  relit la
        // configuration a chaque appel, le rendu suffit donc a montrer le nouvel etat.
        // Plus haute que les autres, et c'est le sujet : à 520 px le bouton « Rétablir » tombe sous
        // la ligne de flottaison, et la capture ne montrerait pas l'état pour lequel elle existe.
        rendreOnglet(
                injecteur, "Emplacements", sortie.resolve("apercu-reglages-emplacements-personnalises.png"), 760, 760);
        // Onglet « Fonctionnalités » (#1057) : les interrupteurs par feature désactivable, et l'avis
        // « effet au prochain démarrage » (composant partagé AvisRedemarrage, #2258).
        rendreOnglet(injecteur, "Fonctionnalités", sortie.resolve("apercu-reglages-fonctionnalites.png"));

        System.out.println("Apercu des reglages ecrit dans " + sortie.toAbsolutePath());
    }

    private static Parent chargerFxml(Injector injecteur, String ressource) {
        FXMLLoader loader = new FXMLLoader(CaptureEcranReglages.class.getResource(ressource));
        loader.setControllerFactory(injecteur::getInstance);
        try {
            return loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + ressource, echec);
        }
    }
}
