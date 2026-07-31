package fr.univ_amu.iut.passage.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.RattachementDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests d'intégration de [RattachementDao] sur une base SQLite jetable : les écritures
/// connection-aware (quadruplet + re-préfixage des chemins via `cheminApres`) réécrivent les sept
/// tables d'une session en une transaction ([UniteDeTravail]).
class RattachementDaoTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final String ANCIEN = "Car040962-2026-Pass1-A1";
    private static final String NOUVEAU = "Car040962-2026-Pass2-A1";

    @TempDir
    Path dossier;

    private RattachementDao dao;
    private UniteDeTravail uniteDeTravail;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private JournalDuCapteurDao journalDao;
    private ReleveClimatiqueDao releveDao;
    private ResultatsIdentificationDao resultatsDao;
    private Path racine;
    private long idPassage;
    private long idSession;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new RattachementDao();
        uniteDeTravail = new UniteDeTravail(source);
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        journalDao = new JournalDuCapteurDao(source);
        releveDao = new ReleveClimatiqueDao(source);
        resultatsDao = new ResultatsIdentificationDao(source);

        racine = dossier.resolve(ANCIEN);
        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .enregistreur(SERIE)
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage()
                .idPassage();
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racine.toString(), 100L, 50L, idPassage))
                .id();
        EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                null,
                ANCIEN + "-PaRec.wav",
                racine.resolve("bruts").resolve(ANCIEN + "-PaRec.wav").toString(),
                5.0,
                384000,
                null,
                idSession));
        sequenceDao.insert(new SequenceDEcoute(
                null,
                ANCIEN + "-PaRec_000.wav",
                original.id(),
                0,
                0.0,
                5.0,
                racine.resolve("transformes").resolve(ANCIEN + "-PaRec_000.wav").toString(),
                false,
                idSession));
        journalDao.insert(
                new JournalDuCapteur(null, racine.resolve("foo_LogPR.txt").toString(), "[]", "[]", idSession));
        releveDao.insert(
                new ReleveClimatique(null, racine.resolve("foo_THLog.csv").toString(), null, idSession));
    }

    /// Seede les résultats Tadarida du passage avec le `chemin` de CSV donné.
    private void seederResultats(String chemin) {
        resultatsDao.insert(new ResultatsIdentification(null, chemin, "Vu", "2026-06-23T08:00", idPassage));
    }

    @Test
    @DisplayName("Réécrit le quadruplet et re-préfixe les chemins des sept tables en une transaction")
    void modifie_quadruplet_et_reprefixe_les_chemins() {
        seederResultats(racine.resolve("transformes")
                .resolve(ANCIEN + "-observations.csv")
                .toString());

        uniteDeTravail.executer(cx -> {
            dao.majQuadruplet(cx, idPassage, 2026, 2);
            dao.reprefixerChemins(
                    cx, idPassage, idSession, racine, dossier.resolve(NOUVEAU), ANCIEN + "-", NOUVEAU + "-");
        });

        Passage passage = passageDao.findById(idPassage).orElseThrow();
        assertThat(passage.numeroPassage()).isEqualTo(2);

        SessionDEnregistrement session = sessionDao.trouverParPassage(idPassage).orElseThrow();
        assertThat(session.cheminRacine()).contains(NOUVEAU).doesNotContain(ANCIEN);

        EnregistrementOriginal original = originalDao.findBySession(idSession).get(0);
        assertThat(original.nomFichier()).isEqualTo(NOUVEAU + "-PaRec.wav");
        assertThat(original.cheminFichier()).contains(NOUVEAU).doesNotContain(ANCIEN);

        SequenceDEcoute sequence = sequenceDao.findBySession(idSession).get(0);
        assertThat(sequence.nomFichier()).isEqualTo(NOUVEAU + "-PaRec_000.wav");
        assertThat(sequence.cheminFichier()).contains(NOUVEAU).doesNotContain(ANCIEN);

        assertThat(journalDao.trouverParSession(idSession).orElseThrow().cheminFichier())
                .contains(NOUVEAU)
                .doesNotContain(ANCIEN);
        assertThat(releveDao.trouverParSession(idSession).orElseThrow().cheminFichier())
                .contains(NOUVEAU)
                .doesNotContain(ANCIEN);
        assertThat(resultatsDao.findByPassage(idPassage).orElseThrow().cheminFichier())
                .contains(NOUVEAU)
                .doesNotContain(ANCIEN);
    }

    @Test
    @DisplayName("Import sans copie : le nom logique d'un original hors session (carte SD) suit le nouveau préfixe")
    void reprefixe_le_nom_logique_d_un_original_hors_session() {
        // Original référencé sur une « carte SD » : chemin EXTERNE à la session (non déplaçable sur disque),
        // mais nom logique préfixé Pass1 en base. Régression du contrôle de préfixe M-Lot après renumérotation.
        String cheminSd = dossier.resolve("sd-72CA").resolve("PaRec_brut.wav").toString();
        long idExterne = originalDao
                .insert(new EnregistrementOriginal(
                        null, ANCIEN + "-PaRec_brut.wav", cheminSd, 5.0, 384000, null, idSession))
                .id();

        uniteDeTravail.executer(cx -> dao.reprefixerChemins(
                cx, idPassage, idSession, racine, dossier.resolve(NOUVEAU), ANCIEN + "-", NOUVEAU + "-"));

        EnregistrementOriginal relu = originalDao.findById(idExterne).orElseThrow();
        assertThat(relu.nomFichier()).isEqualTo(NOUVEAU + "-PaRec_brut.wav"); // nom logique re-préfixé Pass2
        assertThat(relu.cheminFichier()).isEqualTo(cheminSd); // chemin SD inchangé (aucune copie)
    }

    @Test
    @DisplayName("Ne réécrit pas le CSV Tadarida importé depuis un chemin externe à la session")
    void preserve_un_csv_tadarida_externe() {
        String externe = "/tmp/export/" + ANCIEN + "-resultats.csv"; // hors de la racine de session
        seederResultats(externe);

        uniteDeTravail.executer(cx -> dao.reprefixerChemins(
                cx, idPassage, idSession, racine, dossier.resolve(NOUVEAU), ANCIEN + "-", NOUVEAU + "-"));

        assertThat(resultatsDao.findByPassage(idPassage).orElseThrow().cheminFichier())
                .isEqualTo(externe);
    }
}
