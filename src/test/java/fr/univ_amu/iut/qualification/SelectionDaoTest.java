package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [SelectionDao] et gestion de la jonction N..N `selection_sequence`, sur une base
/// SQLite jetable (@TempDir) initialisée par [MigrationSchema].
///
/// La chaîne de parents requise par les clés étrangères (user → site → point → recorder →
/// passage → session → original) est seedée en SQL brut : la feature `qualification` ne
/// dépend d'aucun DAO d'une autre feature (passage, importation), encore à venir.
class SelectionDaoTest {

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
        inserer("INSERT INTO user (local_id, display_name) VALUES ('u-1', 'Testeur')");
        long idSite = inserer("INSERT INTO monitoring_site (square_number, protocol, created_at, user_id)"
                + " VALUES ('640380', 'PointFixeStandard', '2026-01-01', 'u-1')");
        long idPoint = inserer("INSERT INTO listening_point (code, site_id) VALUES ('A1', " + idSite + ")");
        inserer("INSERT INTO recorder (serial_number) VALUES ('SM4-001')");
        idPassage = inserer("INSERT INTO passage"
                + " (passage_number, year, recording_date, start_time, end_time, workflow_status,"
                + " point_id, recorder_id)"
                + " VALUES (1, 2026, '2026-05-01', '20:00', '06:00', 'Importé', "
                + idPoint
                + ", 'SM4-001')");
        idSession = inserer(
                "INSERT INTO recording_session (root_path, passage_id) VALUES ('/tmp/sess', " + idPassage + ")");
        idOriginal = inserer("INSERT INTO original_recording (file_name, file_path, session_id)"
                + " VALUES ('orig.wav', '/tmp/orig.wav', "
                + idSession
                + ")");
        dao = new SelectionDao(source);
    }

    /// Insère une ligne de parent et renvoie sa clé (rowid ou auto-incrément).
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

    /// Crée une séquence d'écoute rattachable, sous la session/original seedés.
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

    @Test
    @DisplayName("Insérer attribue un id et rend la sélection relisible (findById + findByPassage)")
    void inserer_attribue_un_id_et_rend_la_selection_relisible() {
        SelectionDEcoute insere =
                dao.insert(new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 20, idPassage));

        assertThat(insere.id()).as("la clé auto-incrémentée est renseignée").isNotNull();
        SelectionDEcoute relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.methode()).isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
        assertThat(relu.taille()).isEqualTo(20);
        assertThat(relu.idPassage()).isEqualTo(idPassage);
        assertThat(dao.findByPassage(idPassage)).map(SelectionDEcoute::id).contains(insere.id());
    }

    @Test
    @DisplayName("Mettre à jour modifie la méthode et la taille")
    void mettre_a_jour_modifie_la_methode_et_la_taille() {
        SelectionDEcoute insere =
                dao.insert(new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 20, idPassage));

        dao.update(new SelectionDEcoute(insere.id(), MethodeSelection.ALEATOIRE, 30, idPassage));

        SelectionDEcoute relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.methode()).isEqualTo(MethodeSelection.ALEATOIRE);
        assertThat(relu.taille()).isEqualTo(30);
    }

    @Test
    @DisplayName("Supprimer retire la sélection")
    void supprimer_retire_la_selection() {
        SelectionDEcoute insere = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 12, idPassage));
        assertThat(dao.findById(insere.id())).isPresent();

        dao.delete(insere.id());

        assertThat(dao.findById(insere.id())).isEmpty();
    }

    @Test
    @DisplayName("findByPassage renvoie vide quand aucune sélection n'est rattachée")
    void findByPassage_sans_selection_renvoie_vide() {
        assertThat(dao.findByPassage(idPassage)).isEmpty();
    }

    @Test
    @DisplayName("FK active : un passage inconnu est rejeté")
    void clef_etrangere_active_un_passage_inconnu_est_rejete() {
        SelectionDEcoute orpheline = new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 20, 9999L);

        assertThatThrownBy(() -> dao.insert(orpheline))
                .as("PRAGMA foreign_keys=ON doit refuser une FK vers un passage absent")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Unicité du passage : une seule sélection par passage (0:1)")
    void unicite_du_passage_une_seule_selection_par_passage() {
        dao.insert(new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 20, idPassage));

        assertThatThrownBy(() -> dao.insert(new SelectionDEcoute(null, MethodeSelection.ALEATOIRE, 15, idPassage)))
                .as("UNIQUE(passage_id) interdit deux sélections pour le même passage")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Rattacher N séquences puis les relire ordonnées par position")
    void rattacher_des_sequences_et_les_relire_ordonnees_par_position() {
        SelectionDEcoute selection =
                dao.insert(new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 3, idPassage));
        long seqA = creerSequence("seq_a");
        long seqB = creerSequence("seq_b");
        long seqC = creerSequence("seq_c");

        // Rattachement dans le désordre : la relecture doit ré-ordonner par position.
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), seqA, 2, false));
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), seqB, 0, false));
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), seqC, 1, false));

        assertThat(dao.listerSequences(selection.id()))
                .extracting(SequenceSelectionnee::idSequence)
                .as("séquences triées par position croissante")
                .containsExactly(seqB, seqC, seqA);
    }

    @Test
    @DisplayName("Marquer une séquence écoutée bascule son flag listened")
    void marquer_une_sequence_ecoutee_bascule_le_flag() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        long sequence = creerSequence("seq_unique");
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), sequence, 0, false));
        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .extracting(SequenceSelectionnee::ecoutee)
                .isEqualTo(false);

        dao.marquerEcoutee(selection.id(), sequence);

        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .extracting(SequenceSelectionnee::ecoutee)
                .as("le flag listened doit être passé à true")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("#1524 : verdict par fichier, défaut NON_JUGE (NULL), marquage et remise à NULL")
    void verdict_par_fichier_defaut_marquage_et_remise_a_zero() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        long sequence = creerSequence("seq_verdict");
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), sequence, 0, false));

        // Par défaut : colonne NULL → NON_JUGE.
        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .extracting(SequenceSelectionnee::verdict)
                .isEqualTo(VerdictFichier.NON_JUGE);

        dao.marquerVerdict(selection.id(), sequence, VerdictFichier.INEXPLOITABLE);
        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .extracting(SequenceSelectionnee::verdict)
                .isEqualTo(VerdictFichier.INEXPLOITABLE);

        // Remise à NON_JUGE : la colonne repasse à NULL (relue en NON_JUGE).
        dao.marquerVerdict(selection.id(), sequence, VerdictFichier.NON_JUGE);
        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .extracting(SequenceSelectionnee::verdict)
                .isEqualTo(VerdictFichier.NON_JUGE);
    }

    @Test
    @DisplayName("#1524 : le verdict fourni à l'attachement est persisté et relu")
    void verdict_fourni_a_l_attachement_est_persiste() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        long sequence = creerSequence("seq_bon");
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), sequence, 0, true, VerdictFichier.BON));

        assertThat(dao.listerSequences(selection.id()))
                .singleElement()
                .extracting(SequenceSelectionnee::verdict)
                .isEqualTo(VerdictFichier.BON);
    }

    @Test
    @DisplayName("FK jonction : rattacher à une sélection inconnue est rejeté")
    void rattacher_a_une_selection_inconnue_est_rejete() {
        long sequence = creerSequence("seq_orpheline");

        assertThatThrownBy(() -> dao.attacherSequence(new SequenceSelectionnee(9999L, sequence, 0, false)))
                .as("FK selection_sequence.selection_id doit refuser une sélection absente")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("FK jonction : rattacher une séquence inconnue est rejeté")
    void rattacher_une_sequence_inconnue_est_rejete() {
        SelectionDEcoute selection = dao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));

        assertThatThrownBy(() -> dao.attacherSequence(new SequenceSelectionnee(selection.id(), 9999L, 0, false)))
                .as("FK selection_sequence.sequence_id doit refuser une séquence absente")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Supprimer la sélection supprime ses rattachements en cascade")
    void supprimer_la_selection_supprime_les_rattachements_en_cascade() {
        SelectionDEcoute selection =
                dao.insert(new SelectionDEcoute(null, MethodeSelection.REPARTITION_TEMPORELLE, 2, idPassage));
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), creerSequence("s0"), 0, false));
        dao.attacherSequence(new SequenceSelectionnee(selection.id(), creerSequence("s1"), 1, false));
        assertThat(dao.listerSequences(selection.id())).hasSize(2);

        dao.delete(selection.id());

        assertThat(dao.listerSequences(selection.id()))
                .as("ON DELETE CASCADE doit avoir supprimé les rattachements")
                .isEmpty();
    }
}
