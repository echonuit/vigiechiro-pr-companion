package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [ReleveClimatiqueDao] + contraintes : relation 0:1 avec la session (`session_id`
/// unique), mesures JSON optionnelles, FK et suppression en cascade depuis la session.
class ReleveClimatiqueDaoTest {

    @TempDir
    Path dossier;

    private SessionDao sessionDao;
    private ReleveClimatiqueDao dao;
    private Long idSession;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        // `semerSquelette` : pas d'enregistrement original, ces tests posent le leur.
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("040962")
                .point("A1")
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.IMPORTE)
                .cheminSession("racine")
                .semerSquelette();
        idSession = jeu.idSession();
        sessionDao = new SessionDao(source);
        dao = new ReleveClimatiqueDao(source);
    }

    @Test
    @DisplayName("insert rend le relevé relisible (mesures JSON)")
    void inserer_rend_le_releve_relisible() {
        ReleveClimatique insere = dao.insert(
                new ReleveClimatique(null, "PaRecPR1925492_THLog.csv", "[{\"t\":18.5,\"h\":80}]", idSession));

        assertThat(insere.id()).isNotNull();
        ReleveClimatique relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.cheminFichier()).isEqualTo("PaRecPR1925492_THLog.csv");
        assertThat(relu.mesures()).contains("18.5");
        assertThat(dao.trouverParSession(idSession).orElseThrow().id()).isEqualTo(insere.id());
    }

    @Test
    @DisplayName("mesures optionnelles persistées comme null")
    void mesures_nulles_restent_nulles() {
        ReleveClimatique sansMesures = new ReleveClimatique(null, "PaRecPR1925492_THLog.csv", null, idSession);

        ReleveClimatique relu = dao.findById(dao.insert(sansMesures).id()).orElseThrow();

        assertThat(relu.mesures()).isNull();
    }

    @Test
    @DisplayName("relation 0:1 : deux relevés pour la même session sont interdits")
    void unicite_session_id_est_garantie() {
        dao.insert(new ReleveClimatique(null, "a.csv", null, idSession));

        assertThatThrownBy(() -> dao.insert(new ReleveClimatique(null, "b.csv", null, idSession)))
                .as("session_id UNIQUE impose au plus un relevé par session")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("FK active : une session inconnue est rejetée")
    void clef_etrangere_active_une_session_inconnue_est_rejetee() {
        assertThatThrownBy(() -> dao.insert(new ReleveClimatique(null, "x.csv", null, 9999L)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("supprimer la session supprime son relevé en cascade")
    void supprimer_la_session_supprime_le_releve_en_cascade() {
        dao.insert(new ReleveClimatique(null, "PaRecPR1925492_THLog.csv", null, idSession));
        assertThat(dao.trouverParSession(idSession)).isPresent();

        sessionDao.delete(idSession);

        assertThat(dao.trouverParSession(idSession))
                .as("ON DELETE CASCADE doit avoir supprimé le relevé")
                .isEmpty();
    }
}
