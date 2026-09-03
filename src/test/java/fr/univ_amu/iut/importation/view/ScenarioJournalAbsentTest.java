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
import javafx.scene.control.Control;
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

/// Ce que l'inspection dit quand le journal **manque** ou ne se lit pas (#107, #4990).
///
/// ## Une absence n'est pas une preuve
///
/// Sans journal, Companion ne sait rien de la fin de la nuit. Avant #4990, cette nuit recevait le
/// badge vert le plus rassurant : l'absence de preuve était lue comme une preuve. Elle porte
/// désormais « complétude inconnue », dont la pastille n'est ni verte ni ambre - rien ne permet de
/// rassurer, rien ne permet d'inquiéter.
///
/// ## Ce que ces clips montrent, et ce qu'ils ne montrent pas
///
/// `S2-69` demande de **survoler** la pastille. Un popup ne se rend pas en headless : le banc lit
/// l'infobulle **installée**, comme `ScenarioPassagePivotTest` le fait déjà. Il affirme donc ce
/// qu'elle dit - le fond du cas - sans montrer son ouverture, et le clip porte cette limite écrite.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioJournalAbsentTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private static final String INCONNUE = "complétude inco";

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
    @CasDeRecette(value = "S2-44", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-44 · aucun journal sur la carte : l'absence est signalée, l'import reste possible")
    void l_absence_de_journal_est_signalee(FxRobot robot) throws TimeoutException, IOException {
        inspecter(robot, "sd-sans-journal");

        assertThat(texte(robot, "#labelJournal"))
                .as("l'inspection doit DIRE que le journal manque. Se taire laisserait croire qu'elle"
                        + " ne l'a pas cherché, et l'observateur ne saurait pas que ce qui suit est"
                        + " établi sans lui (mode dégradé, #107)")
                .isNotBlank();

        assertThat(robot.lookup("#boutonImporter").tryQuery())
                .as("l'import reste POSSIBLE : un journal manquant n'empêche pas de récupérer les"
                        + " enregistrements, qui sont ce que l'observateur est venu chercher")
                .isPresent();
    }

    @Test
    @CasDeRecette(
            value = {"S2-68", "S2-69"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-68 et S2-69 · sans journal, la nuit est « inconnue », et l'infobulle dit pourquoi")
    void sans_journal_la_nuit_est_inconnue(FxRobot robot) throws TimeoutException, IOException {
        // `sd-sans-journal-multi` et non `sd-sans-journal` : la table des nuits n'est visible qu'à
        // partir de DEUX nuits, et le badge n'était donc jamais à l'écran sur la carte à une nuit
        // (#5145). C'est la même carte sans journal, avec une nuit de plus.
        inspecter(robot, "sd-sans-journal-multi");

        Attente.que(
                () -> lignesDeLaTable(robot).size() >= 2,
                "la table des nuits n'a pas paru : sans elle, le badge de complétude n'est pas à"
                        + " l'écran, et ces deux cas n'ont rien à lire",
                APPARITION_SECONDES * 1000L);

        // ─── S2-68 · inconnue, et non complète ───────────────────────────────────────────────────
        assertThat(lignesDeLaTable(robot))
                .as("aucune preuve, donc aucune des deux nuits n'est attestée. Avant #4990, elles"
                        + " recevaient le badge vert le plus rassurant : l'absence de preuve était lue"
                        + " comme une preuve")
                .allSatisfy(ligne -> assertThat(ligne).contains(INCONNUE));

        // ─── S2-69 · et l'infobulle dit POURQUOI ─────────────────────────────────────────────────
        // Lue sur l'infobulle INSTALLÉE : un popup ne se rend pas en headless. Le clip ne la montre
        // donc pas s'ouvrir, et sa page le dit.
        String infobulle = infobulleDuBadge(robot);
        assertThat(infobulle)
                .as("l'infobulle doit dire POURQUOI on ne sait pas, sans affirmer de cause : le journal"
                        + " ne couvre pas la nuit, ses entrées ont PU être effacées, et la nuit est"
                        + " peut-être entière. « Votre carte était pleine » serait un diagnostic que"
                        + " Companion n'a pas les moyens de poser")
                .contains("ont pu être effacées")
                .contains("peut-être entière")
                .contains("s'importent normalement");
    }

    @Test
    @CasDeRecette(value = "S2-45", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-45 · journal illisible : l'inspection échoue, et le message se lit")
    void un_journal_corrompu_fait_echouer_l_inspection(FxRobot robot) throws TimeoutException, IOException {
        Path carte = CarteDeRecette.materialiser("sd-journal-corrompu");
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        controleur().selecteur().definir(repondant(carte));
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");
        WaitForAsyncUtils.waitForFxEvents();

        // Le seul cas du geste qui attend un ÉCHEC. Un écran resté vide ne dirait pas si le refus a eu
        // lieu ou si rien ne s'est passé : c'est le MESSAGE qui fait la différence.
        Attente.que(
                () -> !messageDErreur(robot).isBlank(),
                "l'inspection n'a rien dit sur un journal illisible. Un écran muet laisse l'observateur"
                        + " devant une carte qu'il croit lue, et il importera en pensant que tout va bien",
                APPARITION_SECONDES * 1000L);

        assertThat(messageDErreur(robot))
                .as("le message doit NOMMER ce qui cloche - le journal - et non dire « une erreur est"
                        + " survenue » : l'observateur doit savoir quoi regarder sur sa carte")
                .containsIgnoringCase("journal");
    }

    /// L'infobulle installée sur la pastille de complétude, lue sans la faire paraître.
    ///
    /// Une cellule de table **est** un `Control` : son infobulle se relit directement, là où
    /// `ScenarioPassagePivotTest` doit passer par `InfobulleDeBlocage` pour une enveloppe `StackPane`.
    private static String infobulleDuBadge(FxRobot robot) {
        Node zone = robot.lookup("#zoneNuits").tryQuery().orElse(null);
        if (!(zone instanceof Parent parent)) {
            return "";
        }
        for (Node noeud : parent.lookupAll(".badge")) {
            if (noeud instanceof Control controle && controle.getTooltip() != null) {
                return controle.getTooltip().getText();
            }
        }
        return "";
    }

    /// Ce que l'écran dit d'un refus d'inspection.
    ///
    /// Sur `#labelMessage`, auquel le contrôleur lie `messageErreurProperty`. Mon premier relevé lisait
    /// la zone des avertissements et le retour de la barre de statut : il rendait vide alors que
    /// l'écran disait bien « Journal LogPR inexploitable », et j'ai failli en conclure que le produit
    /// se taisait (#5145).
    private static String messageDErreur(FxRobot robot) {
        return texte(robot, "#labelMessage");
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
