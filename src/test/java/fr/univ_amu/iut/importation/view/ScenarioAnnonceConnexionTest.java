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

/// Ce que l'import **annonce**, selon qu'on est connecté ou non (#3424, #3448, #3473).
///
/// ## Ce que ces cas jugent, et qui n'est pas l'action
///
/// La session le dit : « dans les quatre cas, l'action était juste et le message mentait ». Ce sont
/// des cas de **véracité**, la même famille que les trois alertes inversées du retour de terrain
/// (#4980). Ils se jugent sur ce que l'écran DIT, confronté à ce qui s'est produit.
///
/// ## Ce que le banc ne peut pas atteindre
///
/// La seconde moitié de `S2-59` - « la participation existe réellement sur la plateforme » - exige de
/// regarder **ailleurs que dans l'application**, et la session l'annonce déjà. Le banc confronte
/// l'annonce à ce que l'application a fait localement ; il n'ouvre pas le portail. Cette moitié reste
/// manuelle, et la page du clip le dit.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioAnnonceConnexionTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final int APPARITION_SECONDES = 30;

    private static final int FIN_SECONDES = 180;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private static final String BOUTON_ASSISTANT = "#boutonImporterNuit";

    private static final String BOUTON_IMPORTER = "#boutonImporter";

    private static final String LABEL_ORIGINAUX = "#labelOriginaux";

    private static final String PARTICIPATION = "participation";

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
    @CasDeRecette(value = "S2-61", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-61 · déconnecté, le compte rendu ne prétend pas avoir créé de participation")
    void deconnecte_le_compte_rendu_ne_pretend_rien(FxRobot robot) throws TimeoutException, IOException {
        Path carte = CarteDeRecette.materialiser("sd-nominale");
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, BOUTON_ASSISTANT);
        WaitForAsyncUtils.waitForFxEvents();
        controleur().selecteur().definir(repondant(carte));
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();
        Attente.que(
                () -> !texte(robot, LABEL_ORIGINAUX).isBlank(),
                "l'inspection n'a jamais conclu",
                APPARITION_SECONDES * 1000L);
        importerAuPremierPoint(robot);

        String compteRendu = texteDe(robot, "#compteRenduChiffre");

        // Ne RIEN prétendre et annoncer un échec sont deux messages différents, et c'est le premier
        // que le cas demande : hors ligne, il n'y a pas eu de tentative, donc pas d'échec à raconter.
        assertThat(compteRendu)
                .as(
                        "hors ligne, le compte rendu ne doit pas parler de participation : ni créée, ni"
                                + " échouée. L'observateur déposera plus tard, et rien ne s'est passé qui"
                                + " mérite d'être annoncé.%nIl dit : %s",
                        compteRendu)
                .doesNotContainIgnoringCase(PARTICIPATION);
    }

    /// Rattache au premier point et lance l'import, jusqu'au compte rendu de fin.
    private void importerAuPremierPoint(FxRobot robot) throws TimeoutException {
        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.amenerDansLeCadre(robot, BOUTON_IMPORTER);
        GesteVisible.cliquer(robot, BOUTON_IMPORTER);
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
