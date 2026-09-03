package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.importation.viewmodel.LigneFichierImport;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le **menu de ligne** du suivi des fichiers, pendant un import (EPIC #1792).
///
/// ## La session le déclarait infilmable, et trois mesures disent le contraire
///
/// « Non automatisable (rendu du popup) », écrivait-elle. Mesuré : le clic droit ouvre une **seconde
/// fenêtre**, `isShowing()` est vrai, et `lookup(".menu-item")` rend les items. La caméra, elle, boucle
/// sur `Window.getWindows()` et cite le cas dans son propre code - « pour les fenêtres qui paraissent
/// en cours de séance : un menu, une modale ». Et `CaptureMenuLigne` photographie déjà un menu de ce
/// genre depuis #1792.
///
/// Une annotation fausse coûte plus qu'un cas non filmé : elle décourage d'essayer.
///
/// ## Pourquoi l'import doit être freiné
///
/// La table du suivi n'existe que **pendant** l'opération : elle est liée à la non-vacuité des lignes,
/// et l'import d'une fixture dure des millisecondes. Sans frein, il n'y aurait aucune ligne sur
/// laquelle cliquer.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioMenuDeLigneImportTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final int APPARITION_SECONDES = 30;

    private static final int FIN_SECONDES = 180;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// « Colonnes… » ferme la grammaire de tous les menus de ligne du produit (#1792).
    private static final String COLONNES = "Colonnes";

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(new AbstractModule() {
                    @Provides
                    @Singleton
                    ExecuteurTache executeurFreine() {
                        return new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS);
                    }
                })
                .semer(this::poserLeCarreEtSonPoint)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    private void poserLeCarreEtSonPoint(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");
    }

    @Test
    @CasDeRecette(
            value = {"S2-51", "S2-52", "S2-53"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-51 à S2-53 · le menu de ligne s'ouvre, copie un nom, et finit par « Colonnes… »")
    void le_menu_de_ligne_s_ouvre_pendant_l_import(FxRobot robot) throws TimeoutException, IOException {
        lancerUnImportFreine(robot);

        Attente.que(
                () -> robot.lookup("#tableFichiers")
                                .tryQuery()
                                .map(Node::isVisible)
                                .orElse(false)
                        && !robot.lookup("#tableFichiers")
                                .queryAs(TableView.class)
                                .getItems()
                                .isEmpty(),
                "le suivi des fichiers n'a montré aucune ligne : sans ligne, il n'y a rien sur quoi"
                        + " cliquer droit, et le geste n'a pas d'objet",
                APPARITION_SECONDES * 1000L);

        TableView<?> table = robot.lookup("#tableFichiers").queryAs(TableView.class);
        // L'assistant est plus haut que la scène : la table du suivi est sous le bord, et TestFX
        // refuse de cliquer ce qu'on ne voit pas. C'est aussi ce que le clip doit montrer - un geste
        // hors du cadre ne se filme pas.
        GesteVisible.amenerDansLeCadre(robot, "#tableFichiers");
        Respiration.surLeMomentCle(robot);
        robot.rightClickOn(table);
        WaitForAsyncUtils.waitForFxEvents();
        ContextMenu menu = table.getContextMenu();

        // ─── S2-51 · le menu s'OUVRE ─────────────────────────────────────────────────────────────
        assertThat(menu)
                .as("la table du suivi doit porter un menu de ligne : c'est ce que #1800 lui a donné,"
                        + " et ce que la grammaire des tables du produit exige")
                .isNotNull();
        assertThat(menu.isShowing())
                .as("le clic droit doit OUVRIR le menu. La session déclarait ce geste non automatisable"
                        + " faute de rendu du popup ; il s'ouvre, et dans sa propre fenêtre")
                .isTrue();

        // ─── S2-51 · et il est lisible EN ENTIER ─────────────────────────────────────────────────
        // « Le menu s'ouvre, entièrement lisible » : un item dont le libellé est vide ou coupé ne se
        // lit pas, et le cas ne serait tenu qu'à moitié.
        List<String> libelles = menu.getItems().stream()
                .map(MenuItem::getText)
                .filter(texte -> texte != null && !texte.isBlank())
                .toList();
        assertThat(libelles)
                .as("« le menu s'ouvre, ENTIÈREMENT LISIBLE » : chaque entrée doit porter un libellé"
                        + " qu'on puisse lire. Une entrée muette laisserait l'observateur deviner ce"
                        + " qu'elle fait, et un menu se lit avant de se cliquer")
                .isNotEmpty()
                .contains("Colonnes…")
                .anySatisfy(libelle -> assertThat(libelle).isEqualTo("Copier"));

        // ─── S2-52 · « Copier ▸ Nom du fichier » remplit le presse-papier ───────────────────────
        // La sélection d'abord : le menu copie la LIGNE choisie, et sans sélection il n'aurait rien à
        // copier. C'est le geste réel - on clique droit sur une ligne, donc on la désigne.
        String nomAttendu = premiereLigne(robot, table);
        robot.interact(() -> table.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> declencher(menu, "Copier", "Nom du fichier"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(pressePapier(robot))
                .as(
                        "« Copier ▸ Nom du fichier » doit placer le NOM dans le presse-papier : c'est ce"
                                + " qui permet à l'observateur de retrouver le fichier sur sa carte, hors de"
                                + " l'application.%nLa ligne montrait : %s",
                        nomAttendu)
                .isEqualTo(nomAttendu);

        assertThat(libelles.getLast())
                .as(
                        "« Colonnes… » est TOUJOURS en dernier : c'est la grammaire commune à tous les"
                                + " menus de ligne du produit, et ce qui permet de la retrouver sans lire"
                                + " (#1792).%nLe menu dit : %s",
                        libelles)
                .contains(COLONNES);

        // Et on LAISSE L'IMPORT CONCLURE. Sans cela le geste part sur un travail en cours - copie,
        // renommage et transformation freinés à 900 ms par fichier - que les classes suivantes du
        // fork subissent. Mesuré : `ordre-alternatif`, qui rejoue toute la suite dans un fork UNIQUE,
        // est passé de 19 minutes à plus de 40, son butoir, et s'est fait couper (#5165).
        //
        // C'est aussi ce que le clip doit montrer : un geste qui se termine.
        Attente.que(
                // VISIBLE, et non seulement présent : le panneau existe dans le graphe de scène
                // avant d'être montré, et `isPresent()` rendait vrai aussitôt - l'attente ne servait
                // à rien. C'est le piège que `PreambuleImport` évite en testant la visibilité.
                () -> robot.lookup("#compteRenduChiffre")
                        .tryQuery()
                        .map(Node::isVisible)
                        .orElse(false),
                "l'import lancé pour faire paraître le suivi n'a jamais abouti : le banc laisserait"
                        + " alors du travail derrière lui",
                FIN_SECONDES * 1000L);
    }

    /// Déclenche l'item `item` du sous-menu `sousMenu`, sans passer par le pointeur.
    ///
    /// Un sous-menu s'ouvre au survol, et le survol d'un popup dans un popup est ce que le rendu sans
    /// écran tient le moins bien. L'action, elle, est celle que le produit a posée : la déclencher
    /// éprouve le même code que le clic, sans dépendre de la géométrie.
    private static void declencher(ContextMenu menu, String sousMenu, String item) {
        for (MenuItem entree : menu.getItems()) {
            if (entree instanceof Menu sous && sousMenu.equals(sous.getText())) {
                for (MenuItem feuille : sous.getItems()) {
                    if (item.equals(feuille.getText())) {
                        feuille.fire();
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Le menu ne porte pas « " + sousMenu + " ▸ " + item + " »");
    }

    /// Le nom de fichier que la première ligne du suivi affiche.
    private static String premiereLigne(FxRobot robot, TableView<?> table) {
        Object ligne = table.getItems().getFirst();
        return ligne instanceof LigneFichierImport fichier ? fichier.nomFichier() : String.valueOf(ligne);
    }

    /// Le presse-papier, lu **sur le fil JavaFX** : `Clipboard` le refuse ailleurs.
    private static String pressePapier(FxRobot robot) {
        StringBuilder lu = new StringBuilder();
        robot.interact(() -> {
            Clipboard presse = Clipboard.getSystemClipboard();
            lu.append(presse.hasString() ? presse.getString() : "");
        });
        return lu.toString();
    }

    /// Ouvre l'assistant, désigne la carte, rattache et lance l'import **sans attendre sa fin** : c'est
    /// pendant l'opération que le suivi des fichiers existe.
    private void lancerUnImportFreine(FxRobot robot) throws TimeoutException, IOException {
        Path carte = CarteDeRecette.materialiser("sd-nominale");
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carte));
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();
        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais conclu",
                APPARITION_SECONDES * 1000L);

        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.amenerDansLeCadre(robot, "#boutonImporter");
        GesteVisible.cliquer(robot, "#boutonImporter");
    }

    private ImportationController controleur() {
        Object courant =
                injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
        assertThat(courant)
                .as("l'écran affiché doit être l'assistant d'import")
                .isInstanceOf(ImportationController.class);
        return (ImportationController) courant;
    }

    private static SelecteurFichier repondant(Path carte) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                return Optional.empty();
            }
        };
    }

    private static String texte(FxRobot robot, String id) {
        Node noeud = robot.lookup(id).tryQuery().orElse(null);
        if (noeud instanceof Labeled libelle) {
            return libelle.getText() == null ? "" : libelle.getText();
        }
        if (noeud instanceof TextInputControl champ) {
            return champ.getText() == null ? "" : champ.getText();
        }
        return "";
    }
}
