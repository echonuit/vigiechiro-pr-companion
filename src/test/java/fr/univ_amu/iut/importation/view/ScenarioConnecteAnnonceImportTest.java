package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// `S2-59` et `S2-60` : ce que l'import annonce **quand la participation part vraiment** (#3448).
///
/// ## Pourquoi ce scénario ne peut pas être bouchonné
///
/// L'objet de ces deux cas est **hors de l'application** : la participation vit sur la plateforme.
/// Contre un double, le clip affirmerait la création parce qu'on aurait fait dire au double qu'elle a
/// eu lieu - « convaincant et creux », ce que `clips-connectes.md` mesure.
///
/// **Il écrit** : il crée une participation sur le compte de tournage, jamais sur un compte portant de
/// vraies nuits. Et il ne prouve pas qu'elle soit correctement **remplie** : « Voir la participation »
/// l'ouvre sur le portail, et cette moitié se juge à l'oeil.
///
/// `@Tag("recette-connectee")`, exclu par `surefire.excludedGroups` : ne tourne que dans le flux
/// **tournage de recette** avec le drapeau `connecte`, qui exige `VIGIECHIRO_TOKEN_TOURNAGE`.
@Tag("recette-connectee")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioConnecteAnnonceImportTest {

    private static final String ID_USER = "u-recette";

    private static final int APPARITION_SECONDES = 30;

    private static final int FIN_SECONDES = 240;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private static final String PARTICIPATION = "participation";

    private Injector injecteur;

    /// Le site du compte sur lequel la nuit sera rattachée, ou vide si le compte n'en porte aucun.
    private SiteVigieChiro siteDuCompte;

    private String numeroCarre;

    @Start
    void start(Stage stage) throws IOException {
        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                // Connecté POUR DE BON : l'import est le sujet, pas la connexion. Le banc lie sa
                // propre source de jeton, et le flux le révoque en fin de run (#4305).
                .connecteALaPlateforme()
                .remplacer(new AbstractModule() {
                    @Provides
                    @Singleton
                    ExecuteurTache executeurFreine() {
                        return new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS);
                    }
                })
                .semer(this::poserUnSiteRelieDuCompte)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
                .montrer(stage);
    }

    /// Sème un carré local **relié** à un site réel du compte de tournage.
    ///
    /// Le lien est la condition de la création : `ServiceImport.creerParticipationSiPossible` ne part
    /// que si l'observateur est connecté **et** le site relié. Un carré local non relié donnerait un
    /// compte rendu muet, et le cas passerait pour un défaut alors qu'il manquerait une précondition.
    private void poserUnSiteRelieDuCompte(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));

        List<SiteVigieChiro> sites =
                inj.getInstance(ClientVigieChiro.class).mesSites().enOptionnel().orElse(List.of());
        if (sites.isEmpty()) {
            return;
        }
        siteDuCompte = sites.getFirst();
        numeroCarre = siteDuCompte.numeroCarre();

        ServiceSites service = inj.getInstance(ServiceSites.class);
        Site carre = service.creerSite(numeroCarre, siteDuCompte.titre(), Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Point du tournage");
        inj.getInstance(LienVigieChiroDao.class)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_SITE, String.valueOf(carre.id()), siteDuCompte.id(), false));
    }

    @Test
    @CasDeRecette(
            value = {"S2-59", "S2-60"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-59 et S2-60 · connecté, l'import annonce la participation créée et ce qu'il reste à faire")
    void connecte_l_import_annonce_la_participation(FxRobot robot) throws TimeoutException, IOException {
        // Imprimé AVANT la précondition : c'est quand le geste est abandonné qu'on veut savoir pourquoi.
        System.out.printf("  site du compte : %s%n", siteDuCompte == null ? "AUCUN" : numeroCarre);

        Assumptions.assumeTrue(
                siteDuCompte != null,
                "Le compte de tournage ne porte aucun site : il n'y a rien à quoi rattacher la nuit, et"
                        + " la participation ne peut pas être créée. Ce n'est PAS un défaut du produit."
                        + " Pour le rejouer, créer un site sur le compte de tournage.");

        Path carte = CarteDeRecette.materialiser("sd-nominale");

        Respiration.avantLeGeste(robot);
        injecteur.getInstance(NavigationSites.class);
        robot.interact(() -> injecteur.getInstance(NavigationSites.class).ouvrirDetail(numeroCarre));
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carte));
        Respiration.surLeMomentCle(robot);
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

        Attente.que(
                () -> robot.lookup("#compteRenduChiffre").tryQuery().isPresent(),
                "l'import n'a pas abouti sur le compte de tournage",
                FIN_SECONDES * 1000L);

        String compteRendu = texteDe(robot, "#compteRenduChiffre");
        System.out.printf("  compte rendu : %s%n", compteRendu.replace("\n", " / "));

        // ─── S2-59 · l'annonce dit la participation ──────────────────────────────────────────────
        assertThat(compteRendu)
                .as(
                        "connecté et le site relié, l'import crée la participation au plus tôt : le compte"
                                + " rendu doit le DIRE. Se taire laisserait l'observateur la créer une seconde"
                                + " fois sur le portail.%nIl dit : %s",
                        compteRendu)
                .containsIgnoringCase(PARTICIPATION);

        // ─── S2-60 · et ce qu'il RESTE à faire ───────────────────────────────────────────────────
        assertThat(compteRendu)
                .as("sans cette suite, une création se lit comme une fiche terminée (#3473) : la"
                        + " participation existe, mais la météo, le matériel et les commentaires"
                        + " restent à remplir sur le portail")
                .containsIgnoringCase("portail");
    }

    private ImportationController controleur() {
        Object courant = injecteur
                .getInstance(fr.univ_amu.iut.commun.view.Navigateur.class)
                .historique()
                .getLast()
                .controleur();
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

    private static String texteDe(FxRobot robot, String identifiant) {
        Node noeud = robot.lookup(identifiant).tryQuery().orElse(null);
        if (!(noeud instanceof Parent parent)) {
            return "";
        }
        StringBuilder dit = new StringBuilder();
        collecter(parent, dit);
        return dit.toString();
    }

    private static void collecter(Node noeud, StringBuilder dit) {
        if (noeud instanceof Labeled libelle
                && libelle.getText() != null
                && !libelle.getText().isBlank()) {
            dit.append(libelle.getText()).append('\n');
        }
        if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecter(enfant, dit));
        }
    }
}
