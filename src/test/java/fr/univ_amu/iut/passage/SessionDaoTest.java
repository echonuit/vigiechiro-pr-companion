package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [SessionDao] + contraintes : relation 1:1 avec le passage (`passage_id` unique),
/// volumes dérivés nullables, FK et suppression en cascade depuis le passage.
class SessionDaoTest {

    @TempDir
    Path dossier;

    private PassageDao passageDao;
    private SessionDao dao;
    private Long idPassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        passageDao = new PassageDao(source);
        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("040962")
                .point("A1")
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.IMPORTE)
                .semerPassage()
                .idPassage();
        dao = new SessionDao(source);
    }

    @Test
    @DisplayName("insert rend la session relisible et préserve les volumes (renseignés ou null)")
    void inserer_rend_la_session_relisible() {
        SessionDEnregistrement insere =
                dao.insert(new SessionDEnregistrement(null, "Car040962-2026-Pass1-A1", 42_000L, null, idPassage));

        assertThat(insere.id()).isNotNull();
        SessionDEnregistrement relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.cheminRacine()).isEqualTo("Car040962-2026-Pass1-A1");
        assertThat(relu.volumeOriginauxOctets()).isEqualTo(42_000L);
        assertThat(relu.volumeSequencesOctets())
                .as("volume dérivé non calculé : reste null")
                .isNull();
        assertThat(dao.trouverParPassage(idPassage).orElseThrow().id()).isEqualTo(insere.id());
    }

    @Test
    @DisplayName("relation 1:1 : deux sessions pour le même passage sont interdites")
    void unicite_passage_id_est_garantie() {
        dao.insert(new SessionDEnregistrement(null, "racine-1", null, null, idPassage));

        assertThatThrownBy(() -> dao.insert(new SessionDEnregistrement(null, "racine-2", null, null, idPassage)))
                .as("passage_id UNIQUE impose une seule session par passage")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("FK active : un passage inconnu est rejeté")
    void clef_etrangere_active_un_passage_inconnu_est_rejete() {
        assertThatThrownBy(() -> dao.insert(new SessionDEnregistrement(null, "racine", null, null, 9999L)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("supprimer le passage supprime sa session en cascade")
    void supprimer_le_passage_supprime_la_session_en_cascade() {
        dao.insert(new SessionDEnregistrement(null, "racine", null, null, idPassage));
        assertThat(dao.trouverParPassage(idPassage)).isPresent();

        passageDao.delete(idPassage);

        assertThat(dao.trouverParPassage(idPassage))
                .as("ON DELETE CASCADE doit avoir supprimé la session")
                .isEmpty();
    }
}
