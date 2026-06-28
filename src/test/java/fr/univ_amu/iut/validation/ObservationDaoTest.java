package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [ObservationDao] + contraintes. Vérifie en particulier les **trois** FK distinctes
/// vers `taxon` (`taxon_tadarida` obligatoire, `taxon_other_tadarida` et `taxon_observer`
/// optionnels), l'insertion en lot transactionnelle et la cascade depuis les résultats parents.
/// Les taxons fil rouge et pseudo-taxons sont déjà semés par `V02`.
///
/// La chaîne de FK (passage → session → original → séquence, et passage → résultats) n'a pas
/// encore de DAO dédié (autres features) : on la sème ici directement en SQL pour rester
/// autonome.
class ObservationDaoTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ObservationDao dao;
    private long idSequence;
    private long idResultats;
    private long idPassage;

    @BeforeEach
    void preparer() throws SQLException {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        try (Connection cx = source.getConnection()) {
            executer(cx, "INSERT INTO user(local_id, display_name) VALUES ('u-1', 'Testeur')");
            long idSite = insererCle(
                    cx,
                    "INSERT INTO monitoring_site(square_number, protocol, created_at, user_id)"
                            + " VALUES ('640380', 'Point fixe standard', '2026-05-01', 'u-1')");
            long idPoint = insererCle(cx, "INSERT INTO listening_point(code, site_id) VALUES ('A1', ?)", idSite);
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            idPassage = insererCle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (1, 2026, '2026-06-20', '21:00', '05:00', 'Importé', ?, 'SN-1')",
                    idPoint);
            long idSession =
                    insererCle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws', ?)", idPassage);
            long idOriginal = insererCle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('a.wav', '/ws/bruts/a.wav', ?)",
                    idSession);
            idSequence = insererCle(
                    cx,
                    "INSERT INTO listening_sequence(file_name, original_recording_id, file_path,"
                            + " session_id) VALUES ('a_000.wav', ?, '/ws/transformes/a_000.wav', ?)",
                    idOriginal,
                    idSession);
            idResultats = insererCle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at,"
                            + " passage_id) VALUES ('/ws/transformes/obs.csv', 'Vu', '2026-06-21', ?)",
                    idPassage);
        }
        dao = new ObservationDao(source);
    }

    private Observation observationComplete() {
        return new Observation(
                null,
                idSequence,
                0.5,
                3.2,
                45000,
                "Pippip",
                0.92,
                "Nyclei",
                "Pippip",
                0.99,
                "signal net",
                true,
                ModeValidation.MANUEL,
                idResultats);
    }

    @Test
    @DisplayName("Insérer attribue un id et rend l'observation relisible (3 taxons, mode, référence)")
    void inserer_attribue_un_id_et_rend_l_observation_relisible() {
        Observation insere = dao.insert(observationComplete());

        assertThat(insere.id()).as("la clé auto-incrémentée est renseignée").isNotNull();
        Observation relu = dao.findById(insere.id()).orElseThrow();
        assertThat(relu.idSequence()).isEqualTo(idSequence);
        assertThat(relu.debutS()).isEqualTo(0.5);
        assertThat(relu.finS()).isEqualTo(3.2);
        assertThat(relu.frequenceMedianeHz()).isEqualTo(45000);
        assertThat(relu.taxonTadarida()).isEqualTo("Pippip");
        assertThat(relu.taxonAutreTadarida()).isEqualTo("Nyclei");
        assertThat(relu.taxonObservateur()).isEqualTo("Pippip");
        assertThat(relu.probObservateur()).isEqualTo(0.99);
        assertThat(relu.reference()).isTrue();
        assertThat(relu.modeValidation()).isEqualTo(ModeValidation.MANUEL);
    }

    @Test
    @DisplayName("Les taxons optionnels et les métriques absentes sont persistés comme null")
    void taxons_optionnels_et_metriques_absentes_sont_nulles() {
        Observation minimale = new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                "Pippip",
                null,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);

        Observation relu = dao.findById(dao.insert(minimale).id()).orElseThrow();

        assertThat(relu.taxonAutreTadarida()).isNull();
        assertThat(relu.taxonObservateur()).isNull();
        assertThat(relu.debutS()).isNull();
        assertThat(relu.finS()).isNull();
        assertThat(relu.frequenceMedianeHz()).isNull();
        assertThat(relu.probTadarida()).isNull();
        assertThat(relu.reference()).isFalse();
        assertThat(relu.modeValidation())
                .as("validation_mode NULL est relu en NON_VALIDE")
                .isEqualTo(ModeValidation.NON_VALIDE);
    }

    @Test
    @DisplayName("Un taxon Tadarida (obligatoire) inconnu est rejeté")
    void clef_etrangere_taxon_tadarida_inconnu_est_rejete() {
        Observation orphelin = new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                "ZZZZZZ",
                null,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);

        assertThatThrownBy(() -> dao.insert(orphelin))
                .as("FK taxon_tadarida doit refuser un code absent")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Un second taxon Tadarida inconnu est rejeté")
    void clef_etrangere_taxon_other_tadarida_inconnu_est_rejete() {
        Observation orphelin = new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                "Pippip",
                null,
                "ZZZZZZ",
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);

        assertThatThrownBy(() -> dao.insert(orphelin))
                .as("FK taxon_other_tadarida doit refuser un code absent")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Un taxon observateur inconnu est rejeté")
    void clef_etrangere_taxon_observer_inconnu_est_rejete() {
        Observation orphelin = new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                "Pippip",
                null,
                null,
                "ZZZZZZ",
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);

        assertThatThrownBy(() -> dao.insert(orphelin))
                .as("FK taxon_observer doit refuser un code absent")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Une séquence inconnue est rejetée (FK active)")
    void clef_etrangere_sequence_inconnue_est_rejetee() {
        Observation orphelin = new Observation(
                null,
                9999L,
                null,
                null,
                null,
                "Pippip",
                null,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);

        assertThatThrownBy(() -> dao.insert(orphelin))
                .as("FK sequence_id doit refuser une séquence absente")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Insérer en lot crée toutes les observations et findByResults les retrouve")
    void inserer_en_lot_cree_toutes_les_observations() {
        int inserees = dao.insererTout(List.of(avecTaxon("Pippip"), avecTaxon("Nyclei"), avecTaxon("Tadten")));

        assertThat(inserees).isEqualTo(3);
        assertThat(dao.findByResults(idResultats))
                .extracting(Observation::taxonTadarida)
                .containsExactlyInAnyOrder("Pippip", "Nyclei", "Tadten");
    }

    @Test
    @DisplayName("Un lot contenant une observation invalide est entièrement annulé (rollback)")
    void inserer_en_lot_est_atomique() {
        List<Observation> lot = List.of(avecTaxon("Pippip"), avecTaxon("ZZZZZZ"));

        assertThatThrownBy(() -> dao.insererTout(lot)).isInstanceOf(DataAccessException.class);
        assertThat(dao.findByResults(idResultats))
                .as("aucune ligne du lot ne doit subsister après rollback")
                .isEmpty();
    }

    @Test
    @DisplayName("findBySequence retrouve les observations d'une séquence")
    void find_by_sequence() {
        dao.insert(observationComplete());

        assertThat(dao.findBySequence(idSequence)).hasSize(1);
        assertThat(dao.findBySequence(9999L)).isEmpty();
    }

    @Test
    @DisplayName("Supprimer les résultats supprime leurs observations en cascade")
    void supprimer_les_resultats_supprime_les_observations_en_cascade() throws SQLException {
        dao.insert(observationComplete());
        assertThat(dao.findByResults(idResultats)).hasSize(1);

        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement("DELETE FROM identification_results WHERE id = ?")) {
            ps.setLong(1, idResultats);
            ps.executeUpdate();
        }

        assertThat(dao.findByResults(idResultats))
                .as("ON DELETE CASCADE doit avoir supprimé les observations")
                .isEmpty();
    }

    @Test
    @DisplayName("#323 : especesObserveesParUtilisateur agrège par espèce×passage, exclut bruit/oiseau")
    void especes_observees_par_utilisateur() {
        dao.insererTout(List.of(
                observation("Nyclei", "Pippip"), // validé Pippip (le tadarida Nyclei est masqué)
                observation("Tadten", null), // non validé → proposition Tadarida Tadten
                observation("Pippip", null), // Pippip à nouveau, MÊME passage → dédupliqué (DISTINCT)
                observation("noise", null))); // pseudo-taxon bruit → exclu

        List<EspeceObservee> especes = dao.especesObserveesParUtilisateur("u-1");

        assertThat(especes)
                .extracting(EspeceObservee::code)
                .as("Pippip (validé + dédupliqué) et Tadten ; ni Nyclei (masqué) ni noise (exclu)")
                .containsExactlyInAnyOrder("Pippip", "Tadten");
        assertThat(especes).allSatisfy(espece -> {
            assertThat(espece.idPassage()).isEqualTo(idPassage);
            assertThat(espece.numeroCarre()).isEqualTo("640380");
            assertThat(espece.codePoint()).isEqualTo("A1");
            assertThat(espece.annee()).isEqualTo(2026);
            assertThat(espece.numeroPassage()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("#323 : especesObserveesParUtilisateur ne renvoie rien pour un autre utilisateur")
    void especes_observees_autre_utilisateur_vide() {
        dao.insert(observationComplete());

        assertThat(dao.especesObserveesParUtilisateur("autre")).isEmpty();
    }

    // --- Inventaire transverse (#analyse) ---

    /// Sème 3 observations dans l'unique passage seedé, une par statut dérivé :
    /// Pippip VALIDÉE, Tadten CORRIGÉE (observateur ≠ Tadarida), Nyclei NON_TOUCHÉE.
    private void semerTroisStatuts() {
        dao.insert(observationValidee("Pippip")); // observateur = tadarida + prob → VALIDEE, espèce Pippip
        dao.insert(observation("Nyclei", "Tadten")); // observateur ≠ tadarida → CORRIGEE, espèce Tadten
        dao.insert(observation("Nyclei", null)); // pas d'observateur → NON_TOUCHEE, espèce Nyclei
    }

    @Test
    @DisplayName("#analyse : inventaire par espèce — compteurs et choix d'espèce (validé sinon Tadarida)")
    void inventaire_par_espece_compteurs() {
        semerTroisStatuts();

        List<EspeceAgregee> inventaire = dao.inventaireParEspece("u-1", null);

        assertThat(inventaire)
                .extracting(EspeceAgregee::code)
                .as("3 espèces : Pippip (validé), Tadten (corrigé), Nyclei (proposition)")
                .containsExactlyInAnyOrder("Pippip", "Tadten", "Nyclei");
        assertThat(inventaire).allSatisfy(espece -> {
            assertThat(espece.nbObservations()).isEqualTo(1);
            assertThat(espece.nbPassages()).isEqualTo(1);
            assertThat(espece.nbCarres()).isEqualTo(1);
            assertThat(espece.nbPoints()).isEqualTo(1);
            assertThat(espece.anneeMin()).isEqualTo(2026);
            assertThat(espece.anneeMax()).isEqualTo(2026);
        });
    }

    @Test
    @DisplayName("#analyse : le filtre de statut restreint l'inventaire par espèce")
    void inventaire_par_espece_filtre_statut() {
        semerTroisStatuts();

        assertThat(dao.inventaireParEspece("u-1", StatutObservation.VALIDEE))
                .extracting(EspeceAgregee::code)
                .containsExactly("Pippip");
        assertThat(dao.inventaireParEspece("u-1", StatutObservation.CORRIGEE))
                .extracting(EspeceAgregee::code)
                .containsExactly("Tadten");
        assertThat(dao.inventaireParEspece("u-1", StatutObservation.NON_TOUCHEE))
                .extracting(EspeceAgregee::code)
                .containsExactly("Nyclei");
    }

    @Test
    @DisplayName("#analyse : inventaire par carré — richesse = nb d'espèces distinctes, total de détections")
    void inventaire_par_carre_richesse() {
        semerTroisStatuts();

        assertThat(dao.inventaireParCarre("u-1", null)).singleElement().satisfies(carre -> {
            assertThat(carre.numeroCarre()).isEqualTo("640380");
            assertThat(carre.richesse()).as("3 espèces distinctes").isEqualTo(3);
            assertThat(carre.nbObservations()).isEqualTo(3);
        });
        // Filtré sur les validées : une seule espèce (Pippip), une détection.
        assertThat(dao.inventaireParCarre("u-1", StatutObservation.VALIDEE))
                .singleElement()
                .satisfies(carre -> {
                    assertThat(carre.richesse()).isEqualTo(1);
                    assertThat(carre.nbObservations()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("#analyse : l'inventaire ne renvoie rien pour un autre utilisateur (périmètre)")
    void inventaire_autre_utilisateur_vide() {
        semerTroisStatuts();

        assertThat(dao.inventaireParEspece("autre", null)).isEmpty();
        assertThat(dao.inventaireParCarre("autre", null)).isEmpty();
    }

    private Observation observationValidee(String code) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                code,
                null,
                null,
                code,
                0.9,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats);
    }

    private Observation observation(String codeTadarida, String codeObservateur) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                codeTadarida,
                null,
                null,
                codeObservateur,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);
    }

    private Observation avecTaxon(String codeTadarida) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                codeTadarida,
                null,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats);
    }

    private static long insererCle(Connection cx, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
            try (ResultSet cles = ps.getGeneratedKeys()) {
                cles.next();
                return cles.getLong(1);
            }
        }
    }

    private static void executer(Connection cx, String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }
}
