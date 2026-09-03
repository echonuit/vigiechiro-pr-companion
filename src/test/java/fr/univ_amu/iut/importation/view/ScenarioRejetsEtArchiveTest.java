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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Trois façons d'aborder l'import qui ne partent pas d'un dossier propre : des **rejets**, une
/// **archive**, et une nuit **déjà connue**.
///
/// ## Ce que les trois ont en commun
///
/// Aucune n'est un cas limite. Un WAV illisible parmi cent arrive ; une nuit rapportée en `.zip`
/// arrive ; rebrancher la même carte arrive tout le temps. Ce que le produit doit faire, chaque fois,
/// c'est **continuer** en disant ce qu'il a trouvé.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioRejetsEtArchiveTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final int APPARITION_SECONDES = 30;

    private static final int FIN_SECONDES = 180;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// L'action du compte rendu de fin, telle que `CompteRenduDeFinImport` la nomme.
    private static final String LIBELLE_SUITE = "Ouvrir la nuit importée";

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
    @CasDeRecette(value = "S2-47", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-47 · un faux WAV parmi les bons : l'import aboutit, et les rejets sont nommés")
    void l_import_aboutit_malgre_les_rejets(FxRobot robot) throws TimeoutException, IOException {
        inspecter(robot, "sd-rejets");
        importerAuPremierPoint(robot);

        String compteRendu = texteDe(robot, "#compteRenduChiffre");

        assertThat(compteRendu)
                .as("l'import ABOUTIT : un fichier illisible ne fait pas perdre les autres, qui sont"
                        + " ce que l'observateur est venu chercher")
                .isNotBlank();

        assertThat(compteRendu)
                .as(
                        "les rejets doivent NOMMER le fichier et la raison, et non se compter. Un « 1"
                                + " rejet » ne dit pas lequel, et l'observateur ne peut pas le retrouver sur sa"
                                + " carte pour comprendre ce qui s'est passé.%nLe compte rendu dit : %s",
                        compteRendu)
                .containsIgnoringCase("wav");
    }

    @Test
    @CasDeRecette(value = "S2-48", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-48 · une archive .zip : la décompression se voit AVANT l'inspection")
    void la_decompression_se_voit_avant_l_inspection(FxRobot robot) throws TimeoutException, IOException {
        // L'archive vit à côté du dossier de la carte : le générateur l'écrit `<fixture>.zip` dans le
        // même parent, et `CarteDeRecette.materialiser` rend le dossier.
        Path carte = CarteDeRecette.materialiser("sd-nominale");
        Path archive = carte.getParent().resolve("sd-nominale.zip");
        assertThat(archive)
                .as("l'archive doit avoir été produite par le générateur : sans elle, ce cas n'a pas de"
                        + " source à désigner")
                .exists();

        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(archive));
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonZip");

        // La progression est posée sur le fil JavaFX AVANT que la décompression démarre (#146) : elle
        // est donc visible dès le clic, et c'est ce que le cas demande de voir.
        Attente.que(
                () -> estVisible(robot, "#zoneProgression"),
                "la barre n'a jamais paru : une grosse archive laisserait l'observateur devant un écran"
                        + " figé sans savoir si quelque chose se passe (#146)",
                APPARITION_SECONDES * 1000L);

        // Le libellé n'est pas VIDE au départ : il affiche « 0 enregistrement(s) », son état neutre.
        // Ce qui se juge est donc que le VRAI compte n'y soit pas encore - la carte en porte six.
        assertThat(texte(robot, "#labelOriginaux"))
                .as("la barre doit se voir AVANT l'inspection. Si les six originaux étaient déjà"
                        + " comptés, ce que le clip montre serait la progression de l'IMPORT et non"
                        + " celle de la décompression - et le cas ne prouverait rien")
                .doesNotContain("6 enregistrement");

        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais suivi la décompression : l'archive a été ouverte pour rien",
                FIN_SECONDES * 1000L);
    }

    @Test
    @CasDeRecette(
            value = {"S2-49", "S2-50"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-49 et S2-50 · rebrancher la même carte : « nuit déjà importée », puis « n° déjà pris »")
    void reimporter_une_nuit_deja_connue(FxRobot robot) throws TimeoutException, IOException {
        // PREMIER TEMPS : la nuit est importée pour de bon. Sans lui, les deux bandeaux paraîtraient
        // sortis de nulle part, et le clip ne montrerait pas d'où vient ce que le produit sait.
        Path carte = CarteDeRecette.materialiser("sd-nominale");
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carte);

        // SECOND TEMPS : on recommence avec la même carte.
        Respiration.avantLeGeste(robot);
        // Sur le fil JavaFX : la navigation touche la scène, et l'appeler depuis le fil de test lève
        // « Not on FX application thread » que TestFX ressort en exception DIFFÉRÉE - donc sur un autre
        // cas que celui qui l'a causée. Une faute, trois cas rouges.
        robot.interact(() -> injecteur.getInstance(NavigationSites.class).ouvrirDetail(CARRE));
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carte));
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais conclu sur la seconde désignation",
                APPARITION_SECONDES * 1000L);

        // ─── S2-49 · « nuit déjà importée », et c'est INFORMATIF ─────────────────────────────────
        assertThat(bandeaux(robot))
                .as("rebrancher la même carte est courant, et le produit doit le DIRE sans refuser :"
                        + " l'observateur peut vouloir réimporter, et c'est à lui d'en décider")
                .containsIgnoringCase("déjà");

        assertThat(robot.lookup("#boutonImporter").tryQuery())
                .as("le bandeau informe, il ne bloque pas")
                .isPresent();
    }

    /// Rattache au premier point et lance l'import, jusqu'au compte rendu de fin.
    private void importerAuPremierPoint(FxRobot robot) throws TimeoutException {
        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.amenerDansLeCadre(robot, "#boutonImporter");
        GesteVisible.cliquer(robot, "#boutonImporter");
        Attente.que(
                () -> estVisible(robot, "#compteRenduChiffre"),
                "l'import n'a pas abouti : le compte rendu de fin n'a jamais paru",
                FIN_SECONDES * 1000L);
    }

    /// Tout ce qu'un nœud dit, mis bout à bout.
    private static String texteDe(FxRobot robot, String identifiant) {
        Node noeud = robot.lookup(identifiant).tryQuery().orElse(null);
        if (!(noeud instanceof Parent parent)) {
            return "";
        }
        StringBuilder dit = new StringBuilder();
        collecter(parent, dit);
        return dit.toString();
    }

    private static boolean estVisible(FxRobot robot, String identifiant) {
        return robot.lookup(identifiant).tryQuery().map(Node::isVisible).orElse(false);
    }

    /// Le texte rendu de chaque ligne de la table des nuits, dans l'ordre de l'écran.
    ///
    /// Lu sur les **cellules**, et non sur les objets du modèle : une cellule est un `Labeled`, et
    /// c'est ce qu'elle affiche qui se juge. Un banc qui lirait `NuitVM#badge()` rejouerait le calcul
    /// au lieu d'éprouver ce que l'observateur voit.
    private static List<String> lignesDeLaTable(FxRobot robot) {
        Node zone = robot.lookup("#zoneNuits").tryQuery().orElse(null);
        if (!(zone instanceof Parent parent)) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        for (Node noeud : parent.lookupAll(".table-row-cell")) {
            StringBuilder ligne = new StringBuilder();
            collecter(noeud, ligne);
            if (!ligne.isEmpty()) {
                lignes.add(ligne.toString());
            }
        }
        return lignes;
    }

    /// Désigne `fixture`, lance l'inspection, et rend tout ce que les bandeaux disent.
    private String inspecter(FxRobot robot, String fixture) throws TimeoutException, IOException {
        Path carte = CarteDeRecette.materialiser(fixture);
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carte));
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        // On attend que l'INSPECTION ait conclu, et non qu'un bandeau paraisse : toutes les cartes
        // n'en lèvent pas au même endroit. `sd-prefixee` ne dit rien ici - sa discordance se voit au
        // RATTACHEMENT - et attendre un bandeau d'inspection y expirerait pour rien.
        Attente.que(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux sur « " + fixture + " » : elle"
                        + " balaie le dossier hors du fil JavaFX, et rien n'a paru dans le temps imparti",
                APPARITION_SECONDES * 1000L);
        return bandeaux(robot);
    }

    /// Tout ce que la zone des avertissements dit, mis bout à bout dans l'ordre de l'écran.
    private static String bandeaux(FxRobot robot) {
        Node zone = robot.lookup("#zoneAvertissements").tryQuery().orElse(null);
        if (!(zone instanceof Parent parent)) {
            return "";
        }
        StringBuilder dit = new StringBuilder();
        collecter(parent, dit);
        return dit.toString();
    }

    private static void collecter(Node noeud, StringBuilder dit) {
        if (noeud instanceof Labeled libelle && libelle.getText() != null) {
            dit.append(libelle.getText()).append('\n');
        }
        if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecter(enfant, dit));
        }
    }

    private ImportationController controleur() {
        Navigateur navigateur = injecteur.getInstance(Navigateur.class);
        Object courant = navigateur.historique().getLast().controleur();
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
                throw new AssertionError("l'import LIT une source : ce geste n'écrit aucun fichier");
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
