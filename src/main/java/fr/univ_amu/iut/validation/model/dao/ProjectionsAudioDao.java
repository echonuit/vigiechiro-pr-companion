package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.persistence.ProjectionGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Collections;
import java.util.List;

/// Projections **audio** de la table `observation` (#1193) : la [LigneObservationAudio] unifiée de la
/// vue « Sons & validation », déclinée par source (passage, lot de passages, espèce, références,
/// séquences non identifiées). Extraites d'[ObservationDao] (qui garde le CRUD et les écritures en
/// lot) pour séparer les familles de requêtes par client. **Lecture seule** : aucune écriture ici.
///
/// Observations + contexte passage + champs d'archivage (référence/commentaire/fréquence). Pas
/// d'exclusion de pseudo-taxons : la validation revoit toutes les séquences. Une CTE commune, quatre
/// sélections selon la source. Les fragments SQL (jointures de contexte, statut dérivé, alias) sont
/// partagés avec les autres DAO de la table via [FragmentsSqlObservation].
public class ProjectionsAudioDao extends ProjectionGenerique {

    /// CTE commune : une ligne par observation avec son contexte passage/carré/point, ses deux taxons +
    /// probabilités, son statut dérivé, et ses champs d'archivage.
    private static final String CTE_AUDIO = FragmentsSqlObservation.DEBUT_CTE
            + " SELECT o.id AS id, o.sequence_id AS seq,"
            + " COALESCE(o.taxon_observer, o.taxon_tadarida) AS taxon_code,"
            + FragmentsSqlObservation.CASE_STATUT
            + FragmentsSqlObservation.ALIAS_STATUT
            + " p.id AS passage_id, p.passage_number AS num_passage, p.recording_date AS date_enr,"
            + " ms.square_number AS carre, ms.friendly_name AS nom_site, lp.code AS point_code,"
            + " ms.user_id AS user_id, o.taxon_tadarida AS tadarida, o.prob_tadarida AS prob_tadarida,"
            + " o.taxon_observer AS observer, o.prob_observer AS prob_observer,"
            + " o.is_reference AS is_reference, o.is_doubtful AS is_doubtful,"
            + " o.observer_certainty AS certitude,"
            + " o.user_comment AS commentaire, o.median_freq_khz AS frequence,"
            + " te.vernacular_name_fr AS nom_espece, tt.vernacular_name_fr AS nom_tadarida,"
            + " tt.latin_name AS latin_tadarida,"
            + " g.name AS groupe,"
            + " ls.file_name AS nom_fichier, ls.recorded_at AS recorded_at,"
            + " o.start_time_s AS debut_s, o.end_time_s AS fin_s"
            + FragmentsSqlObservation.DE_OBSERVATION_AU_SITE
            + " LEFT JOIN taxon te ON te.code = COALESCE(o.taxon_observer, o.taxon_tadarida)"
            + " LEFT JOIN taxon tt ON tt.code = o.taxon_tadarida"
            + " LEFT JOIN taxonomic_group g ON g.id = te.group_id"
            + ")";

    private static final String SELECT_AUDIO = CTE_AUDIO + " SELECT * FROM obs WHERE obs.";

    /// Ordre **de revue** commun à toutes les sources audio : **chronologique** (date d'enregistrement
    /// du passage croissante = on revoit la nuit la plus ancienne d'abord), puis par **point d'écoute**
    /// et enfin par **id** (tri stable et déterministe à l'intérieur d'un passage). Choisi pour qu'une
    /// liste de travail se parcoure dans l'ordre naturel du terrain, identique quelle que soit la source
    /// (un passage, un lot multisite, une espèce ou les références) : pas de « plus récent d'abord » ni
    /// d'ordre propre au lot multisite. Verrouillé par `ObservationDaoTest#lignes_audio_ordre_de_revue`.
    private static final String ORDRE_AUDIO = " ORDER BY obs.date_enr, obs.point_code, obs.id";

    private static final RowMapper<LigneObservationAudio> MAPPER_LIGNE_AUDIO = rs -> new LigneObservationAudio(
            rs.getLong("id"),
            rs.getLong(FragmentsSqlObservation.COL_SEQ),
            rs.getLong(FragmentsSqlObservation.COL_PASSAGE_ID),
            rs.getInt(FragmentsSqlObservation.COL_NUM_PASSAGE),
            rs.getString(FragmentsSqlObservation.COL_DATE_ENR),
            rs.getString(FragmentsSqlObservation.COL_CARRE),
            rs.getString(FragmentsSqlObservation.COL_POINT_CODE),
            rs.getString(FragmentsSqlObservation.COL_NOM_SITE),
            rs.getString("tadarida"),
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_TADARIDA),
            rs.getString(FragmentsSqlObservation.COL_OBSERVER),
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString(FragmentsSqlObservation.COL_STATUT)),
            rs.getInt(FragmentsSqlObservation.COL_IS_REFERENCE) != 0,
            rs.getString("commentaire"),
            FragmentsSqlObservation.entierNullable(rs, "frequence"),
            rs.getString("nom_espece"),
            rs.getString("nom_tadarida"),
            rs.getString("latin_tadarida"),
            rs.getString(FragmentsSqlObservation.COL_GROUPE),
            rs.getString("nom_fichier"),
            (Double) rs.getObject("debut_s"),
            (Double) rs.getObject("fin_s"),
            FragmentsSqlObservation.heureCaptureDe(rs.getString("recorded_at")),
            rs.getInt(FragmentsSqlObservation.COL_IS_DOUBTFUL) != 0,
            CertitudeObservateur.depuisTexte(rs.getString("certitude")));

    public ProjectionsAudioDao(SourceDeDonnees source) {
        super(source);
    }

    /// Source **Passage** : toutes les observations d'un passage (pseudo-taxons compris : on revoit tout).
    public List<LigneObservationAudio> lignesAudioDuPassage(Long idPassage) {
        return projeter(SELECT_AUDIO + "passage_id = ?" + ORDRE_AUDIO, MAPPER_LIGNE_AUDIO, idPassage);
    }

    /// Source **Lot de passages** : observations à travers plusieurs passages (lot filtré du multisite).
    public List<LigneObservationAudio> lignesAudioDesPassages(List<Long> idPassages) {
        if (idPassages.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(idPassages.size(), "?"));
        return projeter(
                SELECT_AUDIO + "passage_id IN (" + placeholders + ")" + ORDRE_AUDIO,
                MAPPER_LIGNE_AUDIO,
                idPassages.toArray());
    }

    /// Source **Espèce** : observations d'une espèce à travers les passages de l'utilisateur, filtrées par
    /// `statut` (`null` = tous).
    public List<LigneObservationAudio> lignesAudioDeLEspece(
            String idUtilisateur, String codeEspece, StatutObservation statut) {
        String filtre = statut == null ? null : statut.name();
        return projeter(
                SELECT_AUDIO + "user_id = ? AND obs.taxon_code = ? AND (? IS NULL OR obs.statut = ?)" + ORDRE_AUDIO,
                MAPPER_LIGNE_AUDIO,
                idUtilisateur,
                codeEspece,
                filtre,
                filtre);
    }

    /// Source **Références** : observations marquées `is_reference` de l'utilisateur (corpus de référence).
    public List<LigneObservationAudio> lignesAudioReferences(String idUtilisateur) {
        return projeter(
                SELECT_AUDIO + "user_id = ? AND obs.is_reference = 1" + ORDRE_AUDIO, MAPPER_LIGNE_AUDIO, idUtilisateur);
    }

    /// Séquences d'un passage **sans observation Tadarida** (présentes sur disque, absentes du CSV),
    /// projetées comme des lignes audio « à revoir » : écoutables (via `idSequence`). Une éventuelle
    /// **observation manuelle** (`results_id IS NULL`, créée en validant à la main) est rattachée par un
    /// `LEFT JOIN` — la séquence **reste** dans la liste après validation, marquée « corrigée » avec le
    /// taxon saisi. Le `NOT EXISTS` n'écarte que les séquences **identifiées par Tadarida**
    /// (`results_id IS NOT NULL`). Ordre chronologique (horodatage puis nom de fichier).
    private static final String SQL_LIGNES_NON_IDENTIFIEES = "SELECT ls.id AS seq, om.id AS id,"
            + " om.taxon_observer AS observer, om.prob_observer AS prob_observer,"
            + " om.user_comment AS commentaire, om.is_reference AS is_reference, om.is_doubtful AS is_doubtful,"
            + " om.observer_certainty AS certitude,"
            + " CASE WHEN om.taxon_observer IS NULL THEN 'NON_TOUCHEE' ELSE 'CORRIGEE' END AS statut,"
            + " te.vernacular_name_fr AS nom_espece,"
            + " p.id AS passage_id, p.passage_number AS num_passage, p.recording_date AS date_enr,"
            + " ms.square_number AS carre, ms.friendly_name AS nom_site, lp.code AS point_code,"
            + " ls.file_name AS nom_fichier, ls.recorded_at AS recorded_at"
            + FragmentsSqlObservation.DE_SEQUENCE_AU_SITE
            + " LEFT JOIN observation om ON om.sequence_id = ls.id AND om.results_id IS NULL"
            + " LEFT JOIN taxon te ON te.code = om.taxon_observer"
            + " WHERE rs.passage_id = ?"
            + "   AND NOT EXISTS (SELECT 1 FROM observation o WHERE o.sequence_id = ls.id"
            + "                   AND o.results_id IS NOT NULL)"
            + " ORDER BY ls.recorded_at, ls.file_name";

    /// Projette une séquence non identifiée en [LigneObservationAudio]. Sans validation manuelle :
    /// `idObservation` nul, aucun taxon, statut **à revoir**. Après validation manuelle : l'`id` et le
    /// `taxon_observer` de l'**observation manuelle** (jointe) sont portés, statut **corrigée**. Aucun champ
    /// Tadarida (proposition/probabilité/fréquence) : ces séquences n'en ont pas.
    private static final RowMapper<LigneObservationAudio> MAPPER_LIGNE_NON_IDENTIFIEE = rs -> new LigneObservationAudio(
            FragmentsSqlObservation.longNullable(rs, "id"), // id de l'observation manuelle, sinon null
            rs.getLong(FragmentsSqlObservation.COL_SEQ),
            rs.getLong(FragmentsSqlObservation.COL_PASSAGE_ID),
            rs.getInt(FragmentsSqlObservation.COL_NUM_PASSAGE),
            rs.getString(FragmentsSqlObservation.COL_DATE_ENR),
            rs.getString(FragmentsSqlObservation.COL_CARRE),
            rs.getString(FragmentsSqlObservation.COL_POINT_CODE),
            rs.getString(FragmentsSqlObservation.COL_NOM_SITE),
            null, // taxonTadarida (aucune proposition Tadarida)
            null, // probTadarida
            rs.getString(FragmentsSqlObservation.COL_OBSERVER), // taxonObservateur (validation manuelle)
            (Double) rs.getObject(FragmentsSqlObservation.COL_PROB_OBSERVER),
            StatutObservation.valueOf(rs.getString(FragmentsSqlObservation.COL_STATUT)),
            rs.getInt(FragmentsSqlObservation.COL_IS_REFERENCE) != 0,
            rs.getString("commentaire"),
            null, // frequenceKHz
            rs.getString("nom_espece"), // vernaculaire du taxon retenu (observateur)
            null, // nomTadarida
            null, // latinTadarida
            null, // groupe
            rs.getString("nom_fichier"),
            null, // debutS
            null, // finS
            FragmentsSqlObservation.heureCaptureDe(rs.getString("recorded_at")),
            rs.getInt(FragmentsSqlObservation.COL_IS_DOUBTFUL) != 0, // om NULL → getInt = 0 → pas douteux
            CertitudeObservateur.depuisTexte(rs.getString("certitude")));

    /// Source **Non identifiés** : les séquences d'un passage **sans observation Tadarida** (à écouter et
    /// valider à la main). L'écoute ne dépend pas d'une observation, seulement de la séquence ; une
    /// validation manuelle y rattache une observation (visible ici après coup).
    public List<LigneObservationAudio> lignesAudioNonIdentifiees(Long idPassage) {
        return projeter(SQL_LIGNES_NON_IDENTIFIEES, MAPPER_LIGNE_NON_IDENTIFIEE, idPassage);
    }
}
