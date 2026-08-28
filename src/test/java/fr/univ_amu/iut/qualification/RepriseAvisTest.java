package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.qualification.model.AvisRevenu;
import fr.univ_amu.iut.qualification.model.PlanDeReprise;
import fr.univ_amu.iut.qualification.model.RepriseAvis;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ce que la reprise d'un avis écrit, et surtout ce qu'elle **refuse** d'écrire (#4627).
///
/// Le patron est celui de [fr.univ_amu.iut.passage.model.EcrivainPaquet] : le plan commande, et un
/// plan qui refuse n'est pas exécuté.
class RepriseAvisTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private SelectionDao dao;
    private long idPassage;
    private long idSession;
    private long idOriginal;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("640380")
                .point("A1")
                .enregistreur("SM4-001")
                .nuit(1, 2026, "2026-05-01")
                .heures("20:00", "06:00")
                .statut(StatutWorkflow.IMPORTE)
                .semerPassage()
                .idPassage();
        idSession = inserer(
                "INSERT INTO recording_session (root_path, passage_id) VALUES ('/tmp/sess', " + idPassage + ")");
        idOriginal = inserer("INSERT INTO original_recording (file_name, file_path, session_id)"
                + " VALUES ('orig.wav', '/tmp/orig.wav', "
                + idSession
                + ")");
        dao = new SelectionDao(source);
    }

    @Test
    @DisplayName("Reprendre un avis pose le verdict du relecteur sans toucher à celui de l'expéditeur")
    void reprendre_pose_l_avis_sans_toucher_au_verdict_de_l_expediteur() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        long sequence = creerSequence("seq_1");
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), sequence, 0, false));
        dao.marquerVerdict(selection.id(), sequence, VerdictFichier.BON);

        AvisRevenu avis = new AvisRevenu("martin", Map.of(sequence, VerdictFichier.MAUVAIS));
        PlanDeReprise plan = PlanDeReprise.pour(dao.listerSequences(selection.id()), avis);

        int poses = RepriseAvis.appliquer(dao, selection.id(), plan, avis, false);

        assertThat(poses).as("un verdict posé").isEqualTo(1);
        assertThat(dao.listerSequences(selection.id())).singleElement().satisfies(rattachement -> {
            assertThat(rattachement.verdict())
                    .as("le verdict de l'expéditeur ne bouge pas")
                    .isEqualTo(VerdictFichier.BON);
            assertThat(rattachement.verdictRelecteur())
                    .as("celui du relecteur se range à côté")
                    .isEqualTo(VerdictFichier.MAUVAIS);
            assertThat(rattachement.pseudoRelecteur()).isEqualTo("martin");
        });
    }

    @Test
    @DisplayName("Un plan qui refuse n'écrit rien du tout, pas même sa part valide")
    void un_plan_qui_refuse_n_ecrit_rien() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        long sequence = creerSequence("seq_1");
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), sequence, 0, false));

        AvisRevenu avis =
                new AvisRevenu("martin", Map.of(sequence, VerdictFichier.MAUVAIS, 987_654L, VerdictFichier.BON));
        PlanDeReprise plan = PlanDeReprise.pour(dao.listerSequences(selection.id()), avis);

        assertThatThrownBy(() -> RepriseAvis.appliquer(dao, selection.id(), plan, avis, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("987654");

        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .as("rien n'a été écrit, pas même le verdict de la séquence connue")
                .satisfies(rattachement ->
                        assertThat(rattachement.porteUnAvisDeRelecteur()).isFalse());
    }

    @Test
    @DisplayName("Un second avis ne remplace le premier qu'une fois le remplacement confirmé")
    void un_second_avis_attend_la_confirmation() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        long sequence = creerSequence("seq_1");
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), sequence, 0, false));
        dao.marquerAvisDeRelecteur(selection.id(), sequence, VerdictFichier.BON, "claire");

        AvisRevenu avis = new AvisRevenu("martin", Map.of(sequence, VerdictFichier.MAUVAIS));
        List<SequenceSelectionnee> etat = dao.listerSequences(selection.id());
        PlanDeReprise plan = PlanDeReprise.pour(etat, avis);

        assertThatThrownBy(() -> RepriseAvis.appliquer(dao, selection.id(), plan, avis, false))
                .as("sans confirmation, l'avis de claire tient")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claire");
        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .satisfies(rattachement ->
                        assertThat(rattachement.pseudoRelecteur()).isEqualTo("claire"));

        RepriseAvis.appliquer(dao, selection.id(), plan, avis, true);

        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .as("confirmé, le remplacement a bien eu lieu")
                .satisfies(rattachement -> {
                    assertThat(rattachement.pseudoRelecteur()).isEqualTo("martin");
                    assertThat(rattachement.verdictRelecteur()).isEqualTo(VerdictFichier.MAUVAIS);
                });
    }

    private long inserer(String sql) {
        try (Connection c = source.getConnection();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet cles = ps.getGeneratedKeys()) {
                cles.next();
                return cles.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("seed parent : " + sql, e);
        }
    }

    private long creerSequence(String nom) {
        return inserer("INSERT INTO listening_sequence (file_name, original_recording_id, file_path, session_id)"
                + " VALUES ('"
                + nom
                + "', "
                + idOriginal
                + ", '/tmp/"
                + nom
                + "', "
                + idSession
                + ")");
    }
}
