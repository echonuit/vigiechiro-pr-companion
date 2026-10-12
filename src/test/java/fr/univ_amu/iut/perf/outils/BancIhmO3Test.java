package fr.univ_amu.iut.perf.outils;

import static fr.univ_amu.iut.perf.outils.EcranDuBanc.RACINE;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.heapApresGc;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.supprimer;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.surLeFil;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.importation.view.DesignationDuBanc;
import fr.univ_amu.iut.importation.view.InspectionConclue;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Gel;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Releve;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Banc de l'**IHM** pour l'objectif O3 (#105) : plusieurs nuits importées par l'assistant, dans le même
/// processus. La sonde des images est posée pendant chaque import puis pendant une navigation entre les
/// écrans, et le heap se relève après un GC complet à la fin de chaque nuit. Étiqueté `banc-ihm`, comme
/// [BancIhmO5Test], et lancé de la même façon.
///
/// Chaque nuit est une carte synthétique de `perf.ihm.wav` WAV de 5 s à 384 kHz (défaut 300), écrite par
/// le générateur de [BancImport], puis retirée une fois importée, comme une carte qu'on ressort.
@Tag("banc-ihm")
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class BancIhmO3Test {

    private static final long DELAI_IMPORT_MS = 30 * 60_000L;
    private static final int NUITS = Integer.getInteger("perf.ihm.nuits", 4);
    private static final int WAV_PAR_NUIT = Integer.getInteger("perf.ihm.wav", 300);
    private static final String CARRE = "640380";

    /// Chaque nuit a sa date : la même date ferait demander « nuit déjà importée » (#147) dès la seconde.
    private static final LocalDate PREMIERE_NUIT = LocalDate.of(2026, 4, 22);
    private static final String UTILISATEUR = "bench-ihm";

    private EcranDuBanc ecran;

    @Start
    void start(Stage stage) {
        ecran = new EcranDuBanc(stage);
    }

    @AfterEach
    void ranger() throws Exception {
        ecran.arreterLaSonde();
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("O3 : plusieurs nuits importées dans le même processus, sonde posée, heap après GC")
    void o3_nuits_successives() throws Exception {
        ecran.monter(EcranDuBanc.espace("o3-nuits"), BancIhmO3Test::semerUnCarre);
        Releve releve = new Releve(RACINE, "O3 nuits successives (" + WAV_PAR_NUIT + " WAV de 5 s par nuit)");
        releve.noterHeap("avant la première nuit", heapApresGc());

        for (int nuit = 1; nuit <= NUITS; nuit++) {
            Path carte = RACINE.resolve("carte-" + nuit);
            supprimer(carte);
            BancImport.genererNuit(carte, WAV_PAR_NUIT, 384_000, 5.0, PREMIERE_NUIT.plusDays(nuit - 1L));
            Button importer = preparerLImport(carte, nuit);

            ecran.reinitialiserLaSonde();
            long debut = System.nanoTime();
            surLeFil(() -> {
                importer.fire();
                return null;
            });
            Attente.queSurLeFil(
                    () -> ecran.visible("#compteRenduChiffre") || ecran.intrus() != null,
                    "l'import de la nuit " + nuit + " n'a pas fini",
                    DELAI_IMPORT_MS);
            assertThat(surLeFil(ecran::intrus))
                    .as("l'import de la nuit %d ne doit rien demander à personne", nuit)
                    .isNull();
            double dureeS = (System.nanoTime() - debut) / 1e9;
            Gel pendant = ecran.sonde().relever();
            supprimer(carte);
            releve.noterImport(nuit, dureeS, pendant);

            ecran.reinitialiserLaSonde();
            naviguer(nuit);
            releve.noterNavigation(nuit, ecran.sonde().relever());
            releve.noterHeap("après la nuit " + nuit, heapApresGc());
        }
        releve.ecrire();
    }

    /// L'assistant ouvert depuis la fiche du carré, la carte désignée et inspectée, la nuit rattachée au
    /// point avec le numéro de passage `nuit` : il ne reste qu'à cliquer « Importer ».
    private Button preparerLImport(Path carte, int nuit) throws Exception {
        surLeFil(() -> {
            ecran.injecteur().getInstance(NavigationSites.class).ouvrirDetail(CARRE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        surLeFil(() -> {
            ecran.noeud("#boutonImporterNuit", Button.class).fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        DesignationDuBanc.designer(surLeFil(ecran::controleurAffiche), carte, ecran.questions());
        surLeFil(() -> {
            ecran.noeud("#boutonParcourir", Button.class).fire();
            return null;
        });
        InspectionConclue.attendre(new FxRobot(), "l'inspection de la carte " + nuit + " n'a pas conclu", 120_000);
        surLeFil(() -> {
            ecran.noeud("#comboPoints", ComboBox.class).getSelectionModel().select(0);
            ecran.noeud("#champPassage", TextField.class).setText(String.valueOf(nuit));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Button importer = ecran.noeud("#boutonImporter", Button.class);
        assertThat(surLeFil(importer::isDisabled))
                .as("« Importer » doit être actif pour la nuit %d", nuit)
                .isFalse();
        return importer;
    }

    /// Trois tours de Mes sites, la vue multisite et l'écran des sons de la nuit importée.
    private void naviguer(int nuitsImportees) throws Exception {
        Passage importe = new PassageDao(ecran.injecteur().getInstance(SourceDeDonnees.class))
                .findAll().stream().max(Comparator.comparing(Passage::id)).orElseThrow();
        SourceObservations sons = new SourceObservations.ParPassage(
                new ContextePassage(importe.id(), importe.numeroPassage(), new ContexteSite(CARRE, "A1", null)));
        for (int tour = 0; tour < 3; tour++) {
            ecran.ouvrirEtAttendre(ecran::ouvrirMesSites, () -> ecran.cartesSites() == 1);
            ecran.ouvrirEtAttendre(ecran::ouvrirMultisite, () -> ecran.lignes("#tableLignes") == nuitsImportees);
            ecran.ouvrirEtAttendre(() -> ecran.ouvrirSons(sons), () -> ecran.lignes("#tableObservations") >= 0);
        }
    }

    /// Un observateur, un carré, un point : la base qu'un import attend.
    private static void semerUnCarre(Injector inj) {
        new UtilisateurDao(inj.getInstance(SourceDeDonnees.class))
                .insert(new Utilisateur(UTILISATEUR, "Banc de l'IHM"));
        ServiceSites sites = inj.getInstance(ServiceSites.class);
        Site site = sites.creerSite(CARRE, "Carré du banc de l'IHM", Protocole.STANDARD, null, UTILISATEUR);
        sites.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, null);
    }
}
