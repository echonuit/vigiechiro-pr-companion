package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
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

/// CRUD du [JournalDuCapteurDao] + contraintes : relation 1:1 avec la session (`session_id`
/// unique), champs JSON, FK et suppression en cascade depuis la session.
class JournalDuCapteurDaoTest {

    @TempDir
    Path dossier;

    private SessionDao sessionDao;
    private JournalDuCapteurDao dao;
    private Long idSession;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", null, null));
        Long idPassage = new PassageDao(source)
                .insert(new Passage(
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
                        "1925492",
                        null))
                .id();
        sessionDao = new SessionDao(source);
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, "racine", null, null, idPassage))
                .id();
        dao = new JournalDuCapteurDao(source);
    }

    @Test
    @DisplayName("insert rend le journal relisible (chemin + JSON)")
    void inserer_rend_le_journal_relisible() {
        JournalDuCapteur insere = dao.insert(
                new JournalDuCapteur(null, "LogPR1925492.txt", "[{\"evt\":\"start\"}]", "[\"reboot\"]", idSession));

        assertThat(insere.id()).isNotNull();
        JournalDuCapteur relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.cheminFichier()).isEqualTo("LogPR1925492.txt");
        assertThat(relu.evenementsParses()).contains("start");
        assertThat(relu.anomaliesDetectees()).contains("reboot");
        assertThat(dao.trouverParSession(idSession).orElseThrow().id()).isEqualTo(insere.id());
    }

    @Test
    @DisplayName("relation 1:1 : deux journaux pour la même session sont interdits")
    void unicite_session_id_est_garantie() {
        dao.insert(new JournalDuCapteur(null, "LogPR1.txt", null, null, idSession));

        assertThatThrownBy(() -> dao.insert(new JournalDuCapteur(null, "LogPR2.txt", null, null, idSession)))
                .as("session_id UNIQUE impose un seul journal par session")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("FK active : une session inconnue est rejetée")
    void clef_etrangere_active_une_session_inconnue_est_rejetee() {
        assertThatThrownBy(() -> dao.insert(new JournalDuCapteur(null, "x.txt", null, null, 9999L)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("supprimer la session supprime son journal en cascade")
    void supprimer_la_session_supprime_le_journal_en_cascade() {
        dao.insert(new JournalDuCapteur(null, "LogPR1925492.txt", null, null, idSession));
        assertThat(dao.trouverParSession(idSession)).isPresent();

        sessionDao.delete(idSession);

        assertThat(dao.trouverParSession(idSession))
                .as("ON DELETE CASCADE doit avoir supprimé le journal")
                .isEmpty();
    }
}
