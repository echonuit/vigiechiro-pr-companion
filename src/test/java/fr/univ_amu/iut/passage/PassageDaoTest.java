package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [PassageDao] (entité centrale) + contraintes : FK vers le point et l'enregistreur,
/// mapping d'énum nullable (`verification_verdict`), unicité R5 `(point_id, year, passage_number)`
/// et **cascade profonde** (supprimer un passage doit supprimer sa session, ses originaux, ses
/// séquences et ses journaux).
class PassageDaoTest {

    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private PassageDao dao;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        // Chaîne de parents requise par les FK : user -> site -> point, et l'enregistreur.
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", "u-1"));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        dao = new PassageDao(source);
    }

    private Passage nouveauPassage(int numero, int annee) {
        return new Passage(
                null,
                numero,
                annee,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                "{\"Fe\":384000,\"gain\":40}",
                StatutWorkflow.IMPORTE,
                null,
                "RAS",
                "{\"tempDebut\":18.5}",
                null,
                idPoint,
                SERIE,
                null);
    }

    @Test
    @DisplayName("insert attribue un id ; statut, JSON et verdict null sont relisibles")
    void inserer_rend_le_passage_relisible() {
        Passage insere = dao.insert(nouveauPassage(1, 2026));

        assertThat(insere.id()).isNotNull();
        Passage relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.statutWorkflow()).isEqualTo(StatutWorkflow.IMPORTE);
        assertThat(relu.verdictVerification()).as("non vérifié : verdict null").isNull();
        assertThat(relu.parametresAcquisition()).contains("384000");
        assertThat(relu.donneesMeteo()).contains("tempDebut");
        assertThat(dao.findByPoint(idPoint)).extracting(Passage::numeroPassage).containsExactly(1);
    }

    @Test
    @DisplayName("update modifie le statut et renseigne un verdict de vérification")
    void mettre_a_jour_le_statut_et_le_verdict() {
        Passage insere = dao.insert(nouveauPassage(1, 2026));

        dao.update(new Passage(
                insere.id(),
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.VERIFIE,
                Verdict.OK,
                "vérifié à l'oreille",
                null,
                null,
                idPoint,
                SERIE,
                null));

        Passage relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.statutWorkflow()).isEqualTo(StatutWorkflow.VERIFIE);
        assertThat(relu.verdictVerification()).isEqualTo(Verdict.OK);
        assertThat(dao.findByStatut(StatutWorkflow.VERIFIE)).hasSize(1);
    }

    @Test
    @DisplayName("FK active : un point ou un enregistreur inconnu est rejeté")
    void clefs_etrangeres_inconnues_sont_rejetees() {
        Passage pointInconnu = new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                9999L,
                SERIE,
                null);
        Passage recorderInconnu = new Passage(
                null,
                1,
                2026,
                "2026-06-20",
                "21:30:00",
                "05:15:00",
                null,
                StatutWorkflow.IMPORTE,
                null,
                null,
                null,
                null,
                idPoint,
                "inconnu",
                null);

        assertThatThrownBy(() -> dao.insert(pointInconnu)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> dao.insert(recorderInconnu)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("R5 : le quadruplet (point, année, n° de passage) est unique")
    void unicite_du_quadruplet_r5_est_garantie() {
        dao.insert(nouveauPassage(1, 2026));

        assertThatThrownBy(() -> dao.insert(nouveauPassage(1, 2026)))
                .as("UNIQUE(point_id, year, passage_number) interdit deux fois le même quadruplet")
                .isInstanceOf(DataAccessException.class);

        // Un autre n° de passage (ou une autre année) sur le même point reste autorisé.
        assertThat(dao.insert(nouveauPassage(2, 2026)).id()).isNotNull();
        assertThat(dao.trouverParPointAnneePassage(idPoint, 2026, 2)).isPresent();
    }

    @Test
    @DisplayName("supprimer un passage efface en cascade session, originaux, séquences et journaux")
    void supprimer_un_passage_cascade_jusqu_aux_fichiers() {
        Passage passage = dao.insert(nouveauPassage(1, 2026));
        SessionDao sessionDao = new SessionDao(source);
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        JournalDuCapteurDao journalDao = new JournalDuCapteurDao(source);
        ReleveClimatiqueDao releveDao = new ReleveClimatiqueDao(source);

        SessionDEnregistrement session = sessionDao.insert(
                new SessionDEnregistrement(null, "Car040962-2026-Pass1-A1", null, null, passage.id()));
        EnregistrementOriginal original = originalDao.insert(
                new EnregistrementOriginal(null, "orig.wav", "bruts/orig.wav", 12.0, 384000, null, session.id()));
        sequenceDao.insert(new SequenceDEcoute(
                null, "orig_000.wav", original.id(), 0, 0.0, 5.0, "transformes/orig_000.wav", false, session.id()));
        journalDao.insert(new JournalDuCapteur(null, "LogPR1925492.txt", "[]", "[]", session.id()));
        releveDao.insert(new ReleveClimatique(null, "PaRecPR1925492_THLog.csv", "[]", session.id()));

        dao.delete(passage.id());

        assertThat(sessionDao.trouverParPassage(passage.id()))
                .as("session supprimée")
                .isEmpty();
        assertThat(originalDao.findBySession(session.id()))
                .as("originaux supprimés")
                .isEmpty();
        assertThat(sequenceDao.findBySession(session.id()))
                .as("séquences supprimées")
                .isEmpty();
        assertThat(journalDao.trouverParSession(session.id()))
                .as("journal supprimé")
                .isEmpty();
        assertThat(releveDao.trouverParSession(session.id()))
                .as("relevé supprimé")
                .isEmpty();
    }
}
