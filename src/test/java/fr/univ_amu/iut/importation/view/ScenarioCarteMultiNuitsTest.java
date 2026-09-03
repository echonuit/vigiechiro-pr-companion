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

/// Une carte laissée tourner **plusieurs nuits** (#3332), et ce que la table en dit.
///
/// ## Le cas ordinaire du terrain
///
/// On pose l'enregistreur et on le laisse. Le journal, lui, est **circulaire** : il perd les plus
/// anciennes (R19). La carte porte donc trois nuits d'enregistrements sous un journal qui n'en couvre
/// qu'une, et c'est la situation la plus fréquente, pas un cas limite.
///
/// ## Ce que le clip de `S2-70` démontre, et que l'image fixe ne dit pas
///
/// Trois lots ont construit ce badge sans jamais le montrer en mouvement : #5071 pour la règle - une
/// nuit sans preuve est **inconnue**, jamais complète - #5030 pour sa colonne, #5135 pour le trajet
/// qui l'amène à la base. `apercu-import-multi-nuits.png` en donne une image (#5101), mais elle ne dit
/// pas d'où vient ce badge. Le clip part de la carte et montre l'inspection qui la lit.
///
/// Ce qui se juge est la **distinction** : « complète » sur la première nuit, « complétude inconnue »
/// sur les deux autres. Trois badges identiques ne diraient rien, et c'était l'état d'avant #5071.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioCarteMultiNuitsTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// Ce que `NuitVM#badge()` rend pour une nuit dont le journal atteste la fin.
    private static final String COMPLETE = "complète";

    /// Et pour une nuit dont le journal ne dit rien. Le libellé s'affiche tronqué dans une colonne de
    /// 130 px (#5111) : le banc cherche donc son début, qui suffit à la distinguer de « complète ».
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
    @CasDeRecette(
            value = {"S2-42", "S2-70"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S2-42 et S2-70 · la table des nuits paraît, et ses badges de complétude se distinguent")
    void la_table_des_nuits_et_ses_badges(FxRobot robot) throws TimeoutException, IOException {
        inspecter(robot, "sd-multi-nuits");

        Attente.que(
                () -> lignesDeLaTable(robot).size() >= 3,
                "la table des nuits n'a pas paru : trois nuits ont été détectées, et c'est elle qui"
                        + " permet d'en exclure une avant d'importer",
                APPARITION_SECONDES * 1000L);

        List<String> lignes = lignesDeLaTable(robot);

        // ─── S2-42 · trois lignes, une par nuit ──────────────────────────────────────────────────
        assertThat(lignes)
                .as("chaque nuit devient un passage distinct : la table en montre une par ligne, et"
                        + " c'est là que l'observateur décoche celles qu'il ne veut pas")
                .hasSize(3);

        // ─── S2-70 · et leurs badges SE DISTINGUENT ──────────────────────────────────────────────
        String tout = String.join(" | ", lignes);
        assertThat(tout)
                .as(
                        "le journal ne couvre que la PREMIÈRE nuit : elle seule porte un badge établi."
                                + " Les deux autres ne sont pas incomplètes - on n'en sait rien - et les"
                                + " confondre rendrait le badge menteur dans un sens comme dans l'autre"
                                + " (#4990, #5071).%nLa table dit : %s",
                        tout)
                .contains(COMPLETE)
                .contains(INCONNUE);

        assertThat(lignes.stream().filter(ligne -> ligne.contains(INCONNUE)).count())
                .as("DEUX nuits sur trois sont inconnues, et non une seule : le journal circulaire a"
                        + " perdu les deux plus anciennes")
                .isEqualTo(2);
    }

    @Test
    @CasDeRecette(value = "S2-43", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-43 · capteur reconfiguré entre deux nuits : chacune reçoit les paramètres de sa session")
    void deux_nuits_deux_configurations(FxRobot robot) throws TimeoutException, IOException {
        inspecter(robot, "sd-multi-configs");

        Attente.que(
                () -> lignesDeLaTable(robot).size() >= 2,
                "la table des nuits n'a pas paru sur une carte qui en porte deux",
                APPARITION_SECONDES * 1000L);

        assertThat(lignesDeLaTable(robot))
                .as("deux nuits, donc deux passages. Le capteur a été repris et reconfiguré entre les"
                        + " deux, et #3460 a corrigé le fait qu'une nuit repartait avec les réglages"
                        + " d'une AUTRE : c'est ce que ce cas garde")
                .hasSize(2);
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
