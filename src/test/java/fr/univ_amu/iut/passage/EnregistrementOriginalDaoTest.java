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
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [EnregistrementOriginalDao] + contraintes : colonnes numériques nullables
/// (`duration_s`, `sample_rate_hz`), FK vers la session et suppression en cascade.
class EnregistrementOriginalDaoTest {

    @TempDir
    Path dossier;

    private SessionDao sessionDao;
    private EnregistrementOriginalDao dao;
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
        dao = new EnregistrementOriginalDao(source);
    }

    private EnregistrementOriginal original(String nom) {
        return new EnregistrementOriginal(null, nom, "bruts/" + nom, 12.5, 384000, "abc123", idSession);
    }

    @Test
    @DisplayName("insert attribue un id et rend l'original relisible")
    void inserer_rend_l_original_relisible() {
        EnregistrementOriginal insere = dao.insert(original("orig.wav"));

        assertThat(insere.id()).isNotNull();
        EnregistrementOriginal relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.nomFichier()).isEqualTo("orig.wav");
        assertThat(relu.dureeSecondes()).isEqualTo(12.5);
        assertThat(relu.frequenceEchantillonnageHz()).isEqualTo(384000);
        assertThat(dao.findBySession(idSession))
                .extracting(EnregistrementOriginal::nomFichier)
                .containsExactly("orig.wav");
    }

    @Test
    @DisplayName("colonnes numériques nullables persistées comme null (et non 0)")
    void colonnes_numeriques_nulles_restent_nulles() {
        EnregistrementOriginal sansMetriques =
                new EnregistrementOriginal(null, "vide.wav", "bruts/vide.wav", null, null, null, idSession);

        EnregistrementOriginal relu =
                dao.findById(dao.insert(sansMetriques).id()).orElseThrow();

        assertThat(relu.dureeSecondes()).isNull();
        assertThat(relu.frequenceEchantillonnageHz()).isNull();
        assertThat(relu.sha256()).isNull();
    }

    @Test
    @DisplayName("#1299 : la taille est persistée et relue (size_bytes)")
    void taille_persistee() {
        EnregistrementOriginal avecTaille = new EnregistrementOriginal(
                null,
                "orig.wav",
                "bruts/orig.wav",
                12.5,
                384000,
                idSession,
                new EmpreinteContenu(14_746_040L, "abc123"));

        EnregistrementOriginal relu = dao.findById(dao.insert(avecTaille).id()).orElseThrow();

        assertThat(relu.tailleOctets()).isEqualTo(14_746_040L);
    }

    @Test
    @DisplayName("#1299 : sansTaille liste les size_bytes NULL ; majTaille les renseigne (backfill)")
    void backfill_sans_taille_puis_maj() {
        long id = dao.insert(original("orig.wav")).id(); // constructeur compat → size_bytes NULL
        assertThat(dao.sansTaille()).extracting(EnregistrementOriginal::id).contains(id);

        dao.majTaille(id, 42L);

        assertThat(dao.findById(id).orElseThrow().tailleOctets()).isEqualTo(42L);
        assertThat(dao.sansTaille()).extracting(EnregistrementOriginal::id).doesNotContain(id);
    }

    @Test
    @DisplayName("FK active : une session inconnue est rejetée")
    void clef_etrangere_active_une_session_inconnue_est_rejetee() {
        EnregistrementOriginal orphelin =
                new EnregistrementOriginal(null, "x.wav", "bruts/x.wav", null, null, null, 9999L);

        assertThatThrownBy(() -> dao.insert(orphelin)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("supprimer la session supprime ses originaux en cascade")
    void supprimer_la_session_supprime_les_originaux_en_cascade() {
        dao.insert(original("a.wav"));
        dao.insert(original("b.wav"));
        assertThat(dao.findBySession(idSession)).hasSize(2);

        sessionDao.delete(idSession);

        assertThat(dao.findBySession(idSession))
                .as("ON DELETE CASCADE doit avoir supprimé les originaux de la session")
                .isEmpty();
    }
}
