package fr.univ_amu.iut.lot.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.plateforme.PlateformeDeTest;
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
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
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
import java.sql.SQLException;
import java.util.List;
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

/// `S4-98` : « Actualiser » importe les observations d'une analyse terminée (#5784).
///
/// ## Pourquoi ce scénario n'existe que sur la plateforme de test
///
/// Le geste part d'un relevé qui rend « terminée », puis lit les observations que le serveur a
/// produites. Contre un double, on lirait l'état qu'on lui a fait rendre. La plateforme de test de
/// l'ADR 5641 joue le vrai code serveur, et son état de départ porte `nuit-traitee`, dont le
/// traitement est déclaré fini avec ses deux `donnees`.
///
/// Il la vise **explicitement**, et porte `plateforme-de-test-seule`, que le tournage national exclut
/// (#5795).
///
/// ## Ce qu'il sème, et ce qu'il laisse dehors
///
/// Un passage local déjà déposé, relié à `nuit-traitee`, avec deux séquences nommées comme ses
/// `donnees`, et **aucune observation en base** : c'est le relevé qui doit les apporter. La moitié
/// « ligne de commande » de S4-98 (`etat-traitement-vigiechiro --importer`) n'est pas jouée ici.
@Tag("recette-connectee")
@Tag("plateforme-de-test")
@Tag("plateforme-de-test-seule")
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioConnecteActualisationTest {

    private static final String ID_USER = "u-recette";
    private static final String CARRE = "130711";
    private static final String POINT = "Z1";
    private static final String NOM_SITE = "Carré 130711";
    private static final int NUMERO_PASSAGE = 1;
    private static final int ANNEE = 2026;
    private static final Prefixe PREFIXE = new Prefixe(CARRE, ANNEE, NUMERO_PASSAGE, POINT);

    /// Les titres des `donnees` de `nuit-traitee` dans l'état de départ : l'import rapproche par eux
    /// une ligne du CSV d'une séquence locale.
    private static final List<String> SEQUENCES = List.of(
            "Car130711-2026-Pass1-Z1-PaRec_20260615_220000_000", "Car130711-2026-Pass1-Z1-PaRec_20260615_221500_000");

    private static final String ACTUALISER = "#btnActualiserTraitement";
    private static final String ETAT = "#lblEtatTraitement";
    private static final String IMPORT = "#lblImportTraitement";

    private static final String IMPORTEES = "Observations importées depuis Vigie-Chiro : 2 observation(s)";
    private static final String DEJA_IMPORTEES = "déjà importées";

    private static final long REPONSE_DU_SERVEUR_MS = 60_000L;

    private Injector injecteur;
    private ContextePassage contexte;

    @Start
    void start(Stage stage) throws IOException {
        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .surLaPlateformeDeTest("observatrice")
                .semer(this::semerLaNuitTraiteeSansObservations)
                .ouvrir(inj -> inj.getInstance(NavigationLot.class).ouvrir(contexte))
                .montrer(stage);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    private void semerLaNuitTraiteeSansObservations(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        Path workspace = inj.getInstance(Workspace.class).racine();
        try {
            Long idPassage = JeuDeDonneesPassage.dans(source)
                    .utilisateur(ID_USER)
                    .carre(CARRE)
                    .nomSite(NOM_SITE)
                    .point(POINT)
                    .nuit(NUMERO_PASSAGE, ANNEE, "2026-06-15")
                    .statut(StatutWorkflow.DEPOSE)
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
                            null,
                            "PaRec_20260615_220000.wav",
                            "bruts/PaRec_20260615_220000.wav",
                            12.0,
                            384000,
                            null,
                            idSession))
                    .id();
            SequenceDao sequences = new SequenceDao(source);
            for (int rang = 0; rang < SEQUENCES.size(); rang++) {
                String nom = SEQUENCES.get(rang) + ".wav";
                Files.writeString(racine.resolve("transformes").resolve(nom), "sequence");
                sequences.insert(new SequenceDEcoute(
                        null, nom, idOriginal, rang, rang * 5.0, 5.0, "transformes/" + nom, true, idSession));
            }
            // Une archive déjà déposée : la nuit est sur la plateforme, et l'écran garde la forme d'un
            // dépôt mené à son terme.
            new DepotUniteDao(source)
                    .insert(new DepotUnite(
                            null,
                            idPassage,
                            PREFIXE.nomDossierSession() + "-0.zip",
                            TypeDepotUnite.ZIP,
                            StatutDepotUnite.DEPOSE,
                            null,
                            null,
                            "2026-06-16"));

            inj.getInstance(LienVigieChiroDao.class)
                    .upsert(new LienVigieChiro(
                            LienVigieChiro.ENTITE_PASSAGE,
                            String.valueOf(idPassage),
                            PlateformeDeTest.acces().id("participations:nuit-traitee"),
                            false));

            contexte = new ContextePassage(idPassage, NUMERO_PASSAGE, new ContexteSite(CARRE, POINT, NOM_SITE));
        } catch (IOException e) {
            throw new IllegalStateException("le semis de la nuit traitée a échoué", e);
        }
    }

    @Test
    @CasDeRecette(value = "S4-98", portee = Portee.A_L_ECRAN)
    @DisplayName("S4-98 · « Actualiser » sur une analyse terminée importe les observations, une seule fois")
    void actualiser_importe_les_observations_d_une_analyse_terminee(FxRobot robot) {
        Attente.queSurLeFil(
                () -> robot.lookup(ACTUALISER).tryQuery().isPresent(),
                "la carte « Traitement Vigie-Chiro » ne paraît pas : le passage n'est pas relié",
                REPONSE_DU_SERVEUR_MS);

        // AVANT le geste : rien en base, et la carte ne dit rien d'un import. Sans ce constat, des
        // observations déjà là passeraient pour l'effet du clic.
        assertThat(observationsEnBase())
                .as("avant le relevé, la nuit n'a aucune observation en base")
                .isZero();
        assertThat(texte(robot, IMPORT))
                .as("avant le relevé, la carte n'annonce aucun import")
                .doesNotContain("importées");

        // ─── le relevé qui apporte les observations ──────────────────────────────────────────────
        GesteVisible.amenerDansLeCadre(robot, ACTUALISER);
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, ACTUALISER);
        Attente.queSurLeFil(
                () -> texte(robot, ETAT).contains("Analyse terminée"),
                "la carte ne dit pas « Analyse terminée » : la plateforme de test n'a pas rendu cet état",
                REPONSE_DU_SERVEUR_MS);
        Attente.queSurLeFil(
                () -> texte(robot, IMPORT).contains(IMPORTEES),
                () -> "la carte n'annonce pas l'import des deux observations. Elle dit : « " + texte(robot, IMPORT)
                        + " »",
                REPONSE_DU_SERVEUR_MS);
        assertThat(observationsEnBase())
                .as("les observations sont en base, sans être passé par « Sons & validation »")
                .isEqualTo(SEQUENCES.size());
        GesteVisible.allerAuBasDeLaPage(robot, IMPORT);
        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);

        // ─── le second relevé ne réimporte rien ──────────────────────────────────────────────────
        GesteVisible.cliquer(robot, ACTUALISER);
        Attente.queSurLeFil(
                () -> texte(robot, IMPORT).contains(DEJA_IMPORTEES),
                () -> "la carte ne dit pas que les observations sont déjà importées. Elle dit : « "
                        + texte(robot, IMPORT) + " »",
                REPONSE_DU_SERVEUR_MS);
        assertThat(observationsEnBase())
                .as("le second relevé n'a rien réimporté")
                .isEqualTo(SEQUENCES.size());
        // La phrase du second relevé est plus longue que celle du premier : la carte peut avoir
        // grandi, et la dernière image se cale de nouveau sur le bas de la page (#5870).
        GesteVisible.allerAuBasDeLaPage(robot, IMPORT);
        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);
    }

    private long observationsEnBase() {
        try (var connexion = injecteur.getInstance(SourceDeDonnees.class).getConnection();
                var ordre = connexion.createStatement();
                var lignes = ordre.executeQuery("SELECT COUNT(*) FROM observation")) {
            lignes.next();
            return lignes.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("compte des observations impossible", e);
        }
    }

    private static String texte(FxRobot robot, String identifiant) {
        Node noeud = robot.lookup(identifiant).tryQuery().orElse(null);
        if (noeud instanceof Labeled libelle && libelle.getText() != null) {
            return libelle.getText();
        }
        return "";
    }
}
