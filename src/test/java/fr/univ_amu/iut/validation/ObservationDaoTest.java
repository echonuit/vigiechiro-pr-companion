package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.EspeceObservee;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
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
                45,
                "Pippip",
                0.92,
                "Nyclei",
                "Pippip",
                0.99,
                "signal net",
                true,
                ModeValidation.MANUEL,
                idResultats,
                false);
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
        assertThat(relu.frequenceMedianeKHz()).isEqualTo(45);
        assertThat(relu.taxonTadarida()).isEqualTo("Pippip");
        assertThat(relu.taxonAutreTadarida()).isEqualTo("Nyclei");
        assertThat(relu.taxonObservateur()).isEqualTo("Pippip");
        assertThat(relu.probObservateur()).isEqualTo(0.99);
        assertThat(relu.reference()).isTrue();
        assertThat(relu.modeValidation()).isEqualTo(ModeValidation.MANUEL);
    }

    @Test
    @DisplayName("#160 : le drapeau douteux est persisté, relu et projeté dans la vue audio (is_doubtful)")
    void douteux_persiste_relu_et_projete() {
        Observation inseree = dao.insert(observationComplete()); // douteux = false par défaut
        assertThat(dao.findById(inseree.id()).orElseThrow().douteux())
                .as("non douteux à l'insertion")
                .isFalse();

        dao.update(inseree.avecDouteux(true));
        assertThat(dao.findById(inseree.id()).orElseThrow().douteux())
                .as("marqué douteux → persisté")
                .isTrue();
        // La projection audio (LigneObservationAudio) reflète aussi le drapeau (colonne is_doubtful).
        assertThat(dao.lignesAudioDuPassage(idPassage))
                .filteredOn(ligne -> inseree.id().equals(ligne.idObservation()))
                .singleElement()
                .satisfies(ligne -> assertThat(ligne.douteux()).isTrue());

        dao.update(inseree.avecDouteux(false));
        assertThat(dao.findById(inseree.id()).orElseThrow().douteux())
                .as("drapeau retiré → persisté")
                .isFalse();
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
                idResultats,
                false);

        Observation relu = dao.findById(dao.insert(minimale).id()).orElseThrow();

        assertThat(relu.taxonAutreTadarida()).isNull();
        assertThat(relu.taxonObservateur()).isNull();
        assertThat(relu.debutS()).isNull();
        assertThat(relu.finS()).isNull();
        assertThat(relu.frequenceMedianeKHz()).isNull();
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
                idResultats,
                false);

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
                idResultats,
                false);

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
                idResultats,
                false);

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
                idResultats,
                false);

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
            // Taxon parent (join taxonomic_group) : Pippip et Tadten sont des chiroptères, tous deux
            // rattachés à la catégorie « Chiroptères » depuis la normalisation V08 (fil rouge sous genre → catégorie).
            assertThat(espece.groupe()).isEqualTo("Chiroptères");
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
    @DisplayName(
            "#analyse : observationsAnalyse — une ligne enrichie par observation (espèce retenue, statut, contexte)")
    void observations_analyse_enrichies() {
        semerTroisStatuts();

        List<ObservationAnalyse> observations = dao.observationsAnalyse("u-1");

        // Espèce retenue = COALESCE(observateur, tadarida) ; statut dérivé ; contexte carré/point/passage/année.
        assertThat(observations)
                .extracting(ObservationAnalyse::taxonRetenu)
                .as("3 espèces retenues : Pippip (validé), Tadten (corrigé), Nyclei (proposition)")
                .containsExactlyInAnyOrder("Pippip", "Tadten", "Nyclei");
        assertThat(observations).allSatisfy(observation -> {
            assertThat(observation.numeroCarre()).isEqualTo("640380");
            assertThat(observation.annee()).isEqualTo(2026);
            assertThat(observation.idPassage()).isEqualTo(idPassage);
            assertThat(observation.groupe()).isEqualTo("Chiroptères"); // taxon parent renseigné
        });
        assertThat(statutDe(observations, "Pippip")).isEqualTo(StatutObservation.VALIDEE);
        assertThat(statutDe(observations, "Tadten")).isEqualTo(StatutObservation.CORRIGEE);
        assertThat(statutDe(observations, "Nyclei")).isEqualTo(StatutObservation.NON_TOUCHEE);
    }

    private static StatutObservation statutDe(List<ObservationAnalyse> observations, String taxon) {
        return observations.stream()
                .filter(observation -> observation.taxonRetenu().equals(taxon))
                .map(ObservationAnalyse::statut)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("#analyse : observationsAnalyse ne renvoie rien pour un autre utilisateur (périmètre)")
    void observations_analyse_autre_utilisateur_vide() {
        semerTroisStatuts();

        assertThat(dao.observationsAnalyse("autre")).isEmpty();
    }

    @Test
    @DisplayName("#audio : referencesDeLUtilisateur ne renvoie que les is_reference de l'utilisateur")
    void references_de_l_utilisateur() {
        dao.insert(observationComplete()); // is_reference = true
        dao.insert(observation("Nyclei", null)); // is_reference = false

        assertThat(dao.referencesDeLUtilisateur("u-1"))
                .singleElement()
                .satisfies(observation -> assertThat(observation.reference()).isTrue());
        assertThat(dao.referencesDeLUtilisateur("autre")).isEmpty();
    }

    @Test
    @DisplayName("#audio : lignesAudioDuPassage porte le contexte, les champs d'archivage et le statut")
    void lignes_audio_du_passage() {
        dao.insert(observationComplete()); // Pippip validé, is_reference, commentaire « signal net », 45 kHz
        dao.insert(observation("Nyclei", null)); // non touchée

        List<LigneObservationAudio> lignes = dao.lignesAudioDuPassage(idPassage);

        assertThat(lignes).hasSize(2).allSatisfy(ligne -> {
            assertThat(ligne.idPassage()).isEqualTo(idPassage);
            assertThat(ligne.numeroCarre()).isEqualTo("640380");
            assertThat(ligne.codePoint()).isEqualTo("A1");
            assertThat(ligne.nomFichier()).isEqualTo("a_000.wav");
            // Groupe taxon parent porté par la projection (LEFT JOIN taxonomic_group) : Pippip et Nyclei
            // sont rattachés à « Chiroptères » (V08). Alimente le filtre par groupe de la vue audio.
            assertThat(ligne.groupe()).isEqualTo("Chiroptères");
        });
        LigneObservationAudio reference = lignes.stream()
                .filter(LigneObservationAudio::reference)
                .findFirst()
                .orElseThrow();
        assertThat(reference.statut()).isEqualTo(StatutObservation.VALIDEE);
        assertThat(reference.commentaire()).isEqualTo("signal net");
        assertThat(reference.frequenceKHz()).isEqualTo(45);
        // Bornes du cri (timeline transformée) portées par la projection : servent à la durée et au repérage.
        assertThat(reference.debutS()).isEqualTo(0.5);
        assertThat(reference.finS()).isEqualTo(3.2);
        // Nom latin de la proposition Tadarida porté par la projection (#897, LEFT JOIN taxon tt) : sert de
        // clé à la source universelle (GBIF/Wikipédia) pour la fiche des taxons hors PNA.
        assertThat(reference.latinTadarida()).isEqualTo("Pipistrellus pipistrellus");
    }

    @Test
    @DisplayName("#479 : updateTout applique les modifications du lot en une transaction et compte les lignes")
    void update_tout_ecrit_le_lot() {
        long id1 = dao.insert(observationComplete()).id();
        long id2 = dao.insert(observationComplete()).id();
        Observation o1 = dao.findById(id1).orElseThrow().avecCommentaire("revu");
        Observation o2 = dao.findById(id2).orElseThrow().avecReference(false);

        int ecrites = dao.updateTout(List.of(o1, o2));

        assertThat(ecrites).isEqualTo(2);
        assertThat(dao.findById(id1).orElseThrow().commentaire()).isEqualTo("revu");
        assertThat(dao.findById(id2).orElseThrow().reference()).isFalse();
    }

    @Test
    @DisplayName("#audio/#530 : la projection porte l'heure de capture (instant complet) depuis recorded_at")
    void lignes_audio_portent_l_heure_de_capture() {
        dao.insert(observation("Nyclei", null));
        // La séquence est horodatée après minuit : la projection doit porter l'INSTANT complet (date + heure)
        // pour un tri chronologique correct, pas seulement 00:15.
        new SequenceDao(source).majHorodatage(idSequence, LocalDateTime.of(2026, 4, 23, 0, 15, 0));

        assertThat(dao.lignesAudioDuPassage(idPassage))
                .isNotEmpty()
                .allSatisfy(
                        ligne -> assertThat(ligne.heureCapture()).isEqualTo(LocalDateTime.of(2026, 4, 23, 0, 15, 0)));
    }

    @Test
    @DisplayName("#audio : lignesAudioReferences ne renvoie que les is_reference de l'utilisateur")
    void lignes_audio_references() {
        dao.insert(observationComplete()); // is_reference = true
        dao.insert(observation("Nyclei", null)); // is_reference = false

        assertThat(dao.lignesAudioReferences("u-1")).singleElement().satisfies(ligne -> {
            assertThat(ligne.reference()).isTrue();
            assertThat(ligne.taxonTadarida()).isEqualTo("Pippip");
        });
        assertThat(dao.lignesAudioReferences("autre")).isEmpty();
    }

    @Test
    @DisplayName("#audio : lignesAudioNonIdentifiees projette les séquences sans observation (à revoir, sans taxon)")
    void lignes_audio_non_identifiees_sequences_sans_observation() {
        // La séquence du fixture n'a aucune observation : c'est une séquence « non identifiée » à écouter.
        new SequenceDao(source).majHorodatage(idSequence, LocalDateTime.of(2026, 4, 22, 22, 30, 0));

        assertThat(dao.lignesAudioNonIdentifiees(idPassage)).singleElement().satisfies(ligne -> {
            assertThat(ligne.idObservation()).as("aucune observation").isNull();
            assertThat(ligne.idSequence()).isEqualTo(idSequence);
            assertThat(ligne.taxonTadarida()).isNull();
            assertThat(ligne.taxonObservateur()).isNull();
            assertThat(ligne.statut()).isEqualTo(StatutObservation.NON_TOUCHEE);
            assertThat(ligne.reference()).isFalse();
            assertThat(ligne.nomFichier()).isEqualTo("a_000.wav");
            assertThat(ligne.numeroCarre()).isEqualTo("640380");
            assertThat(ligne.codePoint()).isEqualTo("A1");
            assertThat(ligne.heureCapture()).isEqualTo(LocalDateTime.of(2026, 4, 22, 22, 30, 0));
        });
    }

    @Test
    @DisplayName("#audio : une séquence identifiée (avec observation Tadarida) sort des non identifiés")
    void lignes_audio_non_identifiees_exclut_les_sequences_observees() {
        dao.insert(observationComplete()); // identifie la séquence du fixture

        assertThat(dao.lignesAudioNonIdentifiees(idPassage)).isEmpty();
    }

    /// Observation **manuelle** de la séquence du fixture : taxon observateur `observateur`, **sans**
    /// proposition ni jeu de résultats Tadarida (autorisé depuis la migration V13).
    private Observation observationManuelle(String observateur) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                null,
                null,
                null,
                observateur,
                null,
                null,
                false,
                ModeValidation.MANUEL,
                null,
                false);
    }

    @Test
    @DisplayName("#V13 : une observation manuelle (sans taxon ni jeu de résultats Tadarida) est persistée")
    void observation_manuelle_sans_tadarida_ni_results() {
        Observation relu =
                dao.findById(dao.insert(observationManuelle("Pippip")).id()).orElseThrow();

        assertThat(relu.taxonTadarida()).isNull();
        assertThat(relu.idResultats()).isNull();
        assertThat(relu.taxonObservateur()).isEqualTo("Pippip");
        assertThat(relu.modeValidation()).isEqualTo(ModeValidation.MANUEL);
    }

    @Test
    @DisplayName("#audio : après validation manuelle, la séquence RESTE dans les non identifiés (corrigée + taxon)")
    void lignes_audio_non_identifiees_montre_l_observation_manuelle() {
        long idObs = dao.insert(observationManuelle("Pippip")).id();

        assertThat(dao.lignesAudioNonIdentifiees(idPassage)).singleElement().satisfies(ligne -> {
            assertThat(ligne.idSequence()).isEqualTo(idSequence);
            assertThat(ligne.idObservation()).isEqualTo(idObs);
            assertThat(ligne.taxonTadarida()).isNull();
            assertThat(ligne.taxonObservateur()).isEqualTo("Pippip");
            assertThat(ligne.statut()).isEqualTo(StatutObservation.CORRIGEE);
        });
    }

    @Test
    @DisplayName("#audio : observationManuelleDeLaSequence renvoie la manuelle, jamais une observation Tadarida")
    void observation_manuelle_de_la_sequence_ignore_la_tadarida() {
        dao.insert(observationComplete()); // observation Tadarida (results_id non nul)
        long idManuelle = dao.insert(observationManuelle("Pippip")).id();

        assertThat(dao.observationManuelleDeLaSequence(idSequence).orElseThrow().id())
                .isEqualTo(idManuelle);
    }

    @Test
    @DisplayName("#audio : lignesAudioDesPassages agrège les observations de plusieurs passages")
    void lignes_audio_des_passages() throws SQLException {
        dao.insert(observationValidee("Pippip")); // passage 1 (seedé)
        long[] second = creerSecondPassage();
        dao.insert(new Observation(
                null,
                second[1],
                null,
                null,
                null,
                "Nyclei",
                null,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                second[2],
                false)); // passage 2

        List<LigneObservationAudio> lignes = dao.lignesAudioDesPassages(List.of(idPassage, second[0]));

        assertThat(lignes)
                .hasSize(2)
                .extracting(LigneObservationAudio::idPassage)
                .containsExactlyInAnyOrder(idPassage, second[0]);
        assertThat(dao.lignesAudioDesPassages(List.of())).isEmpty();
    }

    @Test
    @DisplayName("#audio : lignesAudioDeLEspece filtre par espèce et par statut")
    void lignes_audio_de_l_espece() {
        dao.insert(observationValidee("Pippip"));
        dao.insert(observation("Nyclei", null));

        assertThat(dao.lignesAudioDeLEspece("u-1", "Pippip", null))
                .singleElement()
                .satisfies(ligne -> assertThat(ligne.taxonTadarida()).isEqualTo("Pippip"));
        assertThat(dao.lignesAudioDeLEspece("u-1", "Pippip", StatutObservation.NON_TOUCHEE))
                .isEmpty();
    }

    @Test
    @DisplayName("#audio : l'espèce retenue suit la correction observateur (COALESCE(observer, tadarida))")
    void lignes_audio_espece_retenue_par_correction() {
        // Proposée Nyclei par Tadarida, mais corrigée en Pippip par l'observateur (R16).
        dao.insert(observation("Nyclei", "Pippip"));

        assertThat(dao.lignesAudioDeLEspece("u-1", "Pippip", null))
                .as("retrouvée sous l'espèce corrigée, pas sous la proposition Tadarida")
                .singleElement()
                .satisfies(ligne -> {
                    assertThat(ligne.taxonObservateur()).isEqualTo("Pippip");
                    assertThat(ligne.taxonTadarida()).isEqualTo("Nyclei");
                    assertThat(ligne.statut()).isEqualTo(StatutObservation.CORRIGEE);
                });
        assertThat(dao.lignesAudioDeLEspece("u-1", "Nyclei", null))
                .as("plus rattachée à la proposition Tadarida d'origine")
                .isEmpty();
    }

    @Test
    @DisplayName("#audio : les pseudo-taxons (noise/piaf) sont inclus (contrairement aux espèces observées)")
    void lignes_audio_inclut_pseudo_taxons() {
        dao.insert(observation("noise", null)); // bruit : exclu des espèces, mais à réécouter
        dao.insert(observationValidee("Pippip"));

        assertThat(dao.lignesAudioDuPassage(idPassage))
                .as("la revue audio porte sur TOUT le passage, pseudo-taxons compris")
                .extracting(LigneObservationAudio::taxonTadarida)
                .containsExactlyInAnyOrder("noise", "Pippip");
        assertThat(dao.especesObserveesParUtilisateur("u-1"))
                .as("garde-fou : les espèces observées, elles, excluent les pseudo-taxons")
                .extracting(EspeceObservee::code)
                .doesNotContain("noise");
    }

    @Test
    @DisplayName("#audio : ordre de revue chronologique (date, point, id) verrouillé, toutes sources")
    void lignes_audio_ordre_de_revue() throws SQLException {
        // Passage 1 (date 2026-06-20) : deux observations -> départage par id croissant.
        dao.insert(observationValidee("Pippip"));
        dao.insert(observation("Nyclei", null));
        // Passage 2 (date 2026-07-15) : postérieur -> doit venir après celles de juin.
        long[] second = creerSecondPassage();
        dao.insert(new Observation(
                null,
                second[1],
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
                second[2],
                false));

        List<LigneObservationAudio> lignes = dao.lignesAudioDesPassages(List.of(idPassage, second[0]));

        assertThat(lignes)
                .as("date croissante (juin avant juillet), puis id croissant dans le passage de juin")
                .extracting(LigneObservationAudio::idPassage)
                .containsExactly(idPassage, idPassage, second[0]);
        assertThat(lignes.get(0).idObservation()).isLessThan(lignes.get(1).idObservation());
    }

    @Test
    @DisplayName("#analyse : observationsDeLEspece liste les observations d'une espèce à travers les passages")
    void observations_d_une_espece_a_travers_les_passages() throws SQLException {
        // Passage 1 (seedé) : Pippip validée + Nyclei non touchée (autre espèce, ne doit pas remonter).
        dao.insert(observationValidee("Pippip"));
        dao.insert(observation("Nyclei", null));
        // Passage 2 (autre nuit, même point) : Pippip à nouveau (validée).
        long[] second = creerSecondPassage();
        long idPassage2 = second[0];
        long idSequence2 = second[1];
        long idResultats2 = second[2];
        dao.insert(new Observation(
                null,
                idSequence2,
                null,
                null,
                null,
                "Pippip",
                null,
                null,
                "Pippip",
                0.9,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats2,
                false));

        List<ObservationEspece> detail = dao.observationsDeLEspece("u-1", "Pippip", null);

        assertThat(detail)
                .as("les deux observations de Pippip, une par passage")
                .hasSize(2)
                .extracting(ObservationEspece::idPassage)
                .containsExactlyInAnyOrder(idPassage, idPassage2);
        assertThat(detail).allSatisfy(observation -> {
            assertThat(observation.numeroCarre()).isEqualTo("640380");
            assertThat(observation.codePoint()).isEqualTo("A1");
            assertThat(observation.statut()).isEqualTo(StatutObservation.VALIDEE);
            assertThat(observation.idObservation()).isPositive();
            assertThat(observation.idSequence()).isPositive();
        });
        // Filtre statut : aucune Pippip non touchée ; périmètre par utilisateur respecté.
        assertThat(dao.observationsDeLEspece("u-1", "Pippip", StatutObservation.NON_TOUCHEE))
                .isEmpty();
        assertThat(dao.observationsDeLEspece("autre", "Pippip", null)).isEmpty();
    }

    /// Sème un **second passage** (autre nuit, même point A1) avec sa séquence et ses résultats.
    /// Retourne `{idPassage, idSequence, idResultats}`.
    private long[] creerSecondPassage() throws SQLException {
        try (Connection cx = source.getConnection()) {
            long idPoint;
            try (Statement st = cx.createStatement();
                    ResultSet rs = st.executeQuery("SELECT id FROM listening_point LIMIT 1")) {
                rs.next();
                idPoint = rs.getLong(1);
            }
            long idPassage2 = insererCle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (3, 2026, '2026-07-15', '21:00', '05:00', 'Importé', ?, 'SN-1')",
                    idPoint);
            long idSession2 = insererCle(
                    cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws2', ?)", idPassage2);
            long idOriginal2 = insererCle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('b.wav', '/ws2/bruts/b.wav', ?)",
                    idSession2);
            long idSequence2 = insererCle(
                    cx,
                    "INSERT INTO listening_sequence(file_name, original_recording_id, file_path,"
                            + " session_id) VALUES ('b_000.wav', ?, '/ws2/transformes/b_000.wav', ?)",
                    idOriginal2,
                    idSession2);
            long idResultats2 = insererCle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at,"
                            + " passage_id) VALUES ('/ws2/transformes/obs.csv', 'Vu', '2026-07-16', ?)",
                    idPassage2);
            return new long[] {idPassage2, idSequence2, idResultats2};
        }
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
                idResultats,
                false);
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
                idResultats,
                false);
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
                idResultats,
                false);
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
