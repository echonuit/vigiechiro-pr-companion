package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.plateforme.PlateformeDeTest;
import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// `S4-47` : « Lancer la participation », et la carte « Traitement Vigie-Chiro » qui en rend l'état.
///
/// ## Pourquoi ce scénario n'existe que sur la plateforme de test
///
/// « Analyse planifiée » est la réponse du **serveur**. Contre un double, le clip montrerait ce qu'on
/// a fait dire au double. Contre la plateforme nationale, lancer une analyse écrirait pour de bon sur
/// un compte réel. La plateforme de test de l'ADR 5641 joue le vrai code serveur sur un état qu'on
/// jette, et sans worker l'analyse y reste planifiée : c'est exactement l'état que le cas décrit.
///
/// Il vise donc la plateforme de test **explicitement**, sans lire la cible déclarée : sélectionné par
/// erreur dans un tournage national, il monterait la plateforme de test plutôt que d'écrire ailleurs.
/// Le tag `plateforme-de-test-seule` le tient hors de ce tournage et de son oracle.
///
/// ## Ce qu'il sème
///
/// Un passage local déjà téléversé, relié à la participation `nuit-a-lancer` de l'état de départ. Elle
/// est réservée à ce geste : `nuit-vierge` sert de rebut à la sonde du contrat, et un second lancement
/// dans la même JVM y recevrait « Already » (#5794).
@Tag("recette-connectee")
@Tag("plateforme-de-test")
@Tag("plateforme-de-test-seule")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioConnecteLancementTest {

    private static final String ID_USER = "u-recette";
    private static final String CARRE = "130711";
    private static final String POINT = "Z1";
    private static final String NOM_SITE = "Carré 130711";
    private static final String SERIE = "1925492";
    private static final int NUMERO_PASSAGE = 1;
    private static final int ANNEE = 2026;
    private static final Prefixe PREFIXE = new Prefixe(CARRE, ANNEE, NUMERO_PASSAGE, POINT);
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260616_213000.wav");

    private static final String PLANIFIEE = "Analyse planifiée";

    private static final long REPONSE_DU_SERVEUR_MS = 30_000L;

    private ContextePassage contexte;

    @Start
    void start(Stage stage) throws IOException {
        BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .surLaPlateformeDeTest("observatrice")
                .semer(this::semerUneNuitDeposeeEtReliee)
                .ouvrir(inj -> inj.getInstance(NavigationLot.class).ouvrir(contexte))
                .montrer(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Une nuit dont le téléversement est fait, et dont la participation existe déjà sur la plateforme.
    ///
    /// C'est l'état où l'étape 4 offre « Lancer la participation ». Le lien est ce qui fait partir le
    /// lancement sur `nuit-a-lancer` au lieu de créer une participation neuve.
    private void semerUneNuitDeposeeEtReliee(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        Path workspace = inj.getInstance(Workspace.class).racine();
        try {
            Long idPassage = JeuDeDonneesPassage.dans(source)
                    .utilisateur(ID_USER)
                    .carre(CARRE)
                    .nomSite(NOM_SITE)
                    .point(POINT)
                    .enregistreur(SERIE)
                    .nuit(NUMERO_PASSAGE, ANNEE, "2026-06-16")
                    .statut(StatutWorkflow.DEPOT_EN_COURS)
                    .verdict(Verdict.OK)
                    .semerPassage()
                    .idPassage();

            Path racine = workspace.resolve(PREFIXE.nomDossierSession());
            Files.createDirectories(racine.resolve("transformes"));
            Long idSession = new SessionDao(source)
                    .insert(new SessionDEnregistrement(null, racine.toString(), null, 4096L, idPassage))
                    .id();
            Long idOriginal = new EnregistrementOriginalDao(source)
                    .insert(new EnregistrementOriginal(
                            null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, idSession))
                    .id();
            String sequence = PREFIXE.nommerSequence(NOM_ORIGINAL, 0);
            Files.writeString(racine.resolve("transformes").resolve(sequence), "sequence");
            new SequenceDao(source)
                    .insert(new SequenceDEcoute(
                            null, sequence, idOriginal, 0, 0.0, 5.0, "transformes/" + sequence, true, idSession));
            new JournalDuCapteurDao(source)
                    .insert(new JournalDuCapteur(
                            null, "LogPR" + SERIE + ".txt", null, null, Completude.INCONNUE, idSession));

            new DepotUniteDao(source)
                    .insert(new DepotUnite(
                            null,
                            idPassage,
                            PREFIXE.nomDossierSession() + "-0.zip",
                            TypeDepotUnite.ZIP,
                            StatutDepotUnite.DEPOSE,
                            null,
                            null,
                            "2026-06-17"));

            inj.getInstance(LienVigieChiroDao.class)
                    .upsert(new LienVigieChiro(
                            LienVigieChiro.ENTITE_PASSAGE,
                            String.valueOf(idPassage),
                            PlateformeDeTest.acces().id("participations:nuit-a-lancer"),
                            false));

            contexte = new ContextePassage(idPassage, NUMERO_PASSAGE, new ContexteSite(CARRE, POINT, NOM_SITE));
        } catch (IOException e) {
            throw new IllegalStateException("le semis de la nuit à lancer a échoué", e);
        }
    }

    @Test
    @CasDeRecette(value = "S4-47", portee = Portee.A_L_ECRAN)
    @DisplayName("S4-47 · « Lancer la participation » : la carte « Traitement Vigie-Chiro » dit « Analyse planifiée »")
    void lancer_la_participation_rend_l_analyse_planifiee(FxRobot robot) {
        Attente.queSurLeFil(
                () -> "Lancer la participation".equals(texte(robot, "#btnDeposer")),
                "l'étape 4 n'offre pas « Lancer la participation » : le passage n'est pas relié",
                REPONSE_DU_SERVEUR_MS);

        // AVANT le geste : la participation de l'état de départ n'a aucun traitement. Sans ce constat,
        // un « planifiée » déjà là à l'ouverture passerait pour l'effet du clic.
        assertThat(texte(robot, "#lblEtatTraitement"))
                .as("avant le lancement, rien n'est planifié sur la plateforme de test")
                .doesNotContain(PLANIFIEE);

        Respiration.avantLeGeste(robot);
        GesteVisible.amenerDansLeCadre(robot, "#btnDeposer");
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#btnDeposer");

        Attente.queSurLeFil(
                () -> texte(robot, "#lblRetourLancement").contains("Analyse demandée à Vigie-Chiro"),
                "l'étape 4 ne dit pas que l'analyse est demandée",
                REPONSE_DU_SERVEUR_MS);

        GesteVisible.amenerDansLeCadre(robot, "#zoneTraitement");
        Attente.queSurLeFil(
                () -> texte(robot, "#lblEtatTraitement").contains(PLANIFIEE),
                "la carte « Traitement Vigie-Chiro » ne dit pas « Analyse planifiée »",
                REPONSE_DU_SERVEUR_MS);
        // La carte a été amenée dans le cadre AVANT d'avoir son état, pour qu'on la voie changer. Elle
        // a grandi depuis, et la page est restée là où elle était : deux tournages du même commit
        // finissaient à douze pixels l'un de l'autre, soit 20 % d'écart (#5870). Le bas de la page,
        // lui, ne dépend pas de l'instant où la carte a grandi.
        GesteVisible.allerAuBasDeLaPage(robot, "#zoneTraitement");

        assertThat(visible(robot, "#zoneTraitement"))
                .as("la carte du traitement est à l'écran, pas seulement renseignée")
                .isTrue();
        Respiration.surLeMomentCle(robot);
    }

    private static String texte(FxRobot robot, String identifiant) {
        Node noeud = robot.lookup(identifiant).tryQuery().orElse(null);
        if (noeud instanceof Labeled libelle && libelle.getText() != null) {
            return libelle.getText();
        }
        return "";
    }

    private static boolean visible(FxRobot robot, String identifiant) {
        Node noeud = robot.lookup(identifiant).tryQuery().orElse(null);
        while (noeud != null) {
            if (!noeud.isVisible()) {
                return false;
            }
            noeud = noeud.getParent();
        }
        return robot.lookup(identifiant).tryQuery().isPresent();
    }
}
