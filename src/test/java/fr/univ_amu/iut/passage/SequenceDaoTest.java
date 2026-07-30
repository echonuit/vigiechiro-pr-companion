package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.EmpreinteContenu;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [SequenceDao] + contraintes : booléen `in_selection`, colonnes nullables, FK vers
/// l'original source et la session, suppression en cascade depuis l'original.
class SequenceDaoTest {

    @TempDir
    Path dossier;

    private EnregistrementOriginalDao originalDao;
    private SequenceDao dao;
    private Long idSession;
    private Long idOriginal;

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
        originalDao = new EnregistrementOriginalDao(source);
        idOriginal = originalDao
                .insert(new EnregistrementOriginal(null, "orig.wav", "bruts/orig.wav", 12.0, 384000, null, idSession))
                .id();
        dao = new SequenceDao(source);
    }

    private SequenceDEcoute sequence(String nom, int index, boolean dansSelection) {
        return new SequenceDEcoute(
                null, nom, idOriginal, index, index * 5.0, 5.0, "transformes/" + nom, dansSelection, idSession);
    }

    @Test
    @DisplayName("insert attribue un id ; le drapeau in_selection est relisible")
    void inserer_rend_la_sequence_relisible() {
        SequenceDEcoute insere = dao.insert(sequence("orig_000.wav", 0, true));

        assertThat(insere.id()).isNotNull();
        SequenceDEcoute relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.nomFichier()).isEqualTo("orig_000.wav");
        assertThat(relu.dansSelection()).isTrue();
        assertThat(relu.indexSource()).isEqualTo(0);
        assertThat(dao.findBySession(idSession)).hasSize(1);
    }

    @Test
    @DisplayName("colonnes nullables et drapeau false persistés correctement")
    void colonnes_nullables_et_drapeau_false() {
        SequenceDEcoute minimale = new SequenceDEcoute(
                null, "min.wav", idOriginal, null, null, null, "transformes/min.wav", false, idSession);

        SequenceDEcoute relu = dao.findById(dao.insert(minimale).id()).orElseThrow();

        assertThat(relu.dansSelection()).isFalse();
        assertThat(relu.indexSource()).isNull();
        assertThat(relu.offsetSourceSecondes()).isNull();
        assertThat(relu.dureeSecondes()).isNull();
    }

    @Test
    @DisplayName("on retrouve les séquences issues d'un même original, ordonnées par index")
    void retrouver_les_sequences_par_original() {
        dao.insert(sequence("orig_001.wav", 1, false));
        dao.insert(sequence("orig_000.wav", 0, true));

        assertThat(dao.findByOriginal(idOriginal))
                .extracting(SequenceDEcoute::indexSource)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("#530 : l'horodatage de capture est persisté et relu (recorded_at)")
    void horodatage_capture_persiste() {
        LocalDateTime heure = LocalDateTime.of(2026, 4, 22, 22, 58, 59);
        SequenceDEcoute avecHeure = new SequenceDEcoute(
                null, "seq_000.wav", idOriginal, 0, 0.0, 5.0, "transformes/seq_000.wav", false, idSession, heure);

        SequenceDEcoute relu = dao.findById(dao.insert(avecHeure).id()).orElseThrow();

        assertThat(relu.horodatageCapture()).isEqualTo(heure);
    }

    @Test
    @DisplayName("#530 : sansHorodatage liste les recorded_at NULL ; majHorodatage le renseigne (backfill)")
    void backfill_sans_horodatage_puis_maj() {
        long id = dao.insert(sequence("orig_000.wav", 0, true)).id(); // constructeur compat → recorded_at NULL
        assertThat(dao.sansHorodatage()).extracting(SequenceDEcoute::id).contains(id);

        LocalDateTime heure = LocalDateTime.of(2026, 4, 22, 23, 5, 0);
        dao.majHorodatage(id, heure);

        assertThat(dao.findById(id).orElseThrow().horodatageCapture()).isEqualTo(heure);
        assertThat(dao.sansHorodatage()).extracting(SequenceDEcoute::id).doesNotContain(id);
    }

    @Test
    @DisplayName("#1299 : taille et empreinte persistées et relues (size_bytes, content_fingerprint)")
    void taille_et_empreinte_persistees() {
        SequenceDEcoute avecEmpreinte = new SequenceDEcoute(
                null,
                "seq_000.wav",
                idOriginal,
                0,
                0.0,
                5.0,
                "transformes/seq_000.wav",
                false,
                idSession,
                null,
                new EmpreinteContenu(1_474_604L, "abc123"));

        SequenceDEcoute relu = dao.findById(dao.insert(avecEmpreinte).id()).orElseThrow();

        assertThat(relu.tailleOctets()).isEqualTo(1_474_604L);
        assertThat(relu.empreinte()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("#1299 : sansEmpreinte liste les fingerprint NULL ; majEmpreinte les renseigne (backfill)")
    void backfill_sans_empreinte_puis_maj() {
        long id = dao.insert(sequence("orig_000.wav", 0, true)).id(); // constructeur compat → empreinte NULL
        assertThat(dao.sansEmpreinte()).extracting(SequenceDEcoute::id).contains(id);
        assertThat(dao.sansEmpreinteDeSession(idSession))
                .extracting(SequenceDEcoute::id)
                .contains(id);

        dao.majEmpreinte(id, 42L, "def456");

        SequenceDEcoute relu = dao.findById(id).orElseThrow();
        assertThat(relu.tailleOctets()).isEqualTo(42L);
        assertThat(relu.empreinte()).isEqualTo("def456");
        assertThat(dao.sansEmpreinte()).extracting(SequenceDEcoute::id).doesNotContain(id);
    }

    @Test
    @DisplayName("FK active : un original source inconnu est rejeté")
    void clef_etrangere_active_un_original_inconnu_est_rejete() {
        SequenceDEcoute orphelin =
                new SequenceDEcoute(null, "x.wav", 9999L, 0, 0.0, 5.0, "transformes/x.wav", false, idSession);

        assertThatThrownBy(() -> dao.insert(orphelin)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("supprimer l'original supprime ses séquences dérivées en cascade")
    void supprimer_l_original_supprime_les_sequences_en_cascade() {
        dao.insert(sequence("orig_000.wav", 0, true));
        dao.insert(sequence("orig_001.wav", 1, false));
        assertThat(dao.findByOriginal(idOriginal)).hasSize(2);

        originalDao.delete(idOriginal);

        assertThat(dao.findByOriginal(idOriginal))
                .as("ON DELETE CASCADE doit avoir supprimé les séquences de l'original")
                .isEmpty();
    }
}
