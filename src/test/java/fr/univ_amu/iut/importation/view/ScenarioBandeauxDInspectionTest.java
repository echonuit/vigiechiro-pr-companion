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

/// Les bandeaux **non bloquants** que l'inspection lève sur une carte discutable (#33, #147).
///
/// ## Ce qu'ils ont en commun, et que le clip doit rendre
///
/// Aucun n'empêche l'import. Ils disent ce que l'observateur ne peut pas voir en regardant sa carte -
/// deux enregistreurs mêlés, un journal qui ne correspond pas - et le laissent décider. Un clip qui
/// montrerait le bandeau sans le bouton d'import encore actif suggérerait un refus.
///
/// Chaque cas prend **sa** carte : c'est la carte qui fait la pathologie, et le générateur les
/// reconstruit à l'octet près depuis leur spec (`dev-docs/recette/fixtures.md`).
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioBandeauxDInspectionTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

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
    @CasDeRecette(value = "S2-40", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-40 · deux enregistreurs sur la même carte : le bandeau « mélange », non bloquant")
    void le_bandeau_du_melange(FxRobot robot) throws TimeoutException, IOException {
        String dit = inspecter(robot, "sd-melange");

        assertThat(dit)
                .as("le bandeau doit NOMMER ce qui est mêlé - deux séries d'enregistreur - et non se"
                        + " contenter de signaler une anomalie : c'est en lisant les séries que"
                        + " l'observateur reconnaît sa carte")
                .contains("mélange")
                .contains("plusieurs enregistreurs");

        assertThat(robot.lookup("#boutonImporter").tryQuery())
                .as("le bandeau INFORME : le bouton d'import reste là. Un avertissement qui retirerait"
                        + " le bouton serait un refus déguisé")
                .isPresent();
    }

    @Test
    @CasDeRecette(value = "S2-41", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-41 · journal et enregistrements en désaccord : le bandeau « incohérence », plus ferme")
    void le_bandeau_de_l_incoherence(FxRobot robot) throws TimeoutException, IOException {
        String dit = inspecter(robot, "sd-incoherente");

        assertThat(dit)
                .as("le journal ne correspond pas aux enregistrements : le bandeau doit dire QUOI peut"
                        + " être faux - la série ou la date du passage - car c'est cela que"
                        + " l'observateur devra corriger après l'import")
                .contains("journal")
                .containsAnyOf("ne correspond", "correspondait");

        assertThat(robot.lookup("#boutonImporter").tryQuery())
                .as("plus ferme que le mélange, mais toujours pas bloquant")
                .isPresent();
    }

    @Test
    @CasDeRecette(value = "S2-46", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-46 · des fichiers préfixés pour un autre rattachement : le bandeau de discordance")
    void le_bandeau_de_la_discordance_de_prefixe(FxRobot robot) throws TimeoutException, IOException {
        // `sd-prefixee` porte des noms déjà préfixés pour le carré 130711, point Z1. Le banc rattache
        // au carré 640380, point A1 : c'est la discordance que le cas demande.
        inspecter(robot, "sd-prefixee");

        ComboBox<?> points = robot.lookup("#comboPoints").queryAs(ComboBox.class);
        robot.interact(() -> points.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> !texte(robot, "#labelPrefixeDiscordant").isBlank(),
                "l'avertissement de discordance n'a pas paru alors que les fichiers portent le préfixe"
                        + " d'un autre carré : sans lui, leurs noms partiraient au dépôt sous le nom"
                        + " d'un autre",
                APPARITION_SECONDES * 1000L);

        assertThat(texte(robot, "#labelPrefixeDiscordant"))
                .as("l'avertissement doit NOMMER le préfixe attendu ici : sans lui, l'observateur sait"
                        + " qu'il y a discordance mais pas avec quoi comparer")
                .contains("préfixe attendu ici");

        // MESURÉ, et cela contredit deux écrits. Le bouton d'import est **désactivé** : ce
        // quatrième bandeau BLOQUE, là où le commentaire d'`ImportationController` le disait « non
        // bloquant » et où la session le range sous le geste des bandeaux *non bloquants* (#5138).
        //
        // Le comportement est le bon : importer des fichiers préfixés pour un autre carré les
        // enverrait au dépôt sous ce nom-là. Ce sont les deux écrits qui ont vieilli.
        assertThat(robot.lookup("#boutonImporter").queryAs(Node.class).isDisabled())
                .as("l'import doit être BLOQUÉ tant que la discordance dure : les noms partiraient"
                        + " tels quels au dépôt, sous le nom d'un autre carré")
                .isTrue();
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
