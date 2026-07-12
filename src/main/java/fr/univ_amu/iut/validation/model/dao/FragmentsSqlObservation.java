package fr.univ_amu.iut.validation.model.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/// Fragments SQL, alias de colonnes et lectures de [ResultSet] partagés par les DAO de la table
/// `observation` : [ObservationDao] (CRUD, écritures en lot) et les DAO de **projection** transverse
/// (#1193). Factorisés ici pour qu'une même jointure de contexte, un même statut dérivé ou un même
/// alias ne soit défini qu'une fois, quel que soit le DAO qui l'assemble.
final class FragmentsSqlObservation {

    /// Noms de colonnes projetées, partagés par les mappers des projections analyse et audio.
    static final String COL_CARRE = "carre";

    static final String COL_PROB_TADARIDA = "prob_tadarida";
    static final String COL_PROB_OBSERVER = "prob_observer";
    static final String COL_PASSAGE_ID = "passage_id";
    static final String COL_DATE_ENR = "date_enr";
    static final String COL_NOM_SITE = "nom_site";
    static final String COL_GROUPE = "groupe";
    static final String COL_ANNEE = "annee";
    static final String COL_STATUT = "statut";
    static final String DEBUT_CTE = "WITH obs AS (";
    static final String ALIAS_STATUT = " AS statut,";

    /// Alias de colonnes projetés par plusieurs mappers audio (séquence, n° de passage, code du point).
    static final String COL_SEQ = "seq";

    static final String COL_NUM_PASSAGE = "num_passage";
    static final String COL_POINT_CODE = "point_code";
    static final String COL_OBSERVER = "observer";
    static final String COL_IS_REFERENCE = "is_reference";
    static final String COL_IS_DOUBTFUL = "is_doubtful";

    /// Jointures communes `listening_sequence (ls) → recording_session → passage → point → site` : le
    /// **contexte d'un relevé**, partagé par toutes les projections transverses (observations, espèces,
    /// séquences non identifiées). L'alias `ls` doit être introduit par le fragment appelant.
    static final String JOIN_SESSION_AU_SITE = " JOIN recording_session rs ON ls.session_id = rs.id"
            + " JOIN passage p ON rs.passage_id = p.id"
            + " JOIN listening_point lp ON p.point_id = lp.id"
            + " JOIN monitoring_site ms ON lp.site_id = ms.id";

    /// Chaîne de jointures `observation → … → monitoring_site` (le contexte d'un relevé), partagée par
    /// toutes les projections transverses.
    static final String DE_OBSERVATION_AU_SITE =
            " FROM observation o" + " JOIN listening_sequence ls ON o.sequence_id = ls.id" + JOIN_SESSION_AU_SITE;

    /// Jointures `listening_sequence → … → monitoring_site` : le **contexte** d'une séquence (passage /
    /// carré / point) **sans passer par une observation**. Pivot des séquences non identifiées, dont la
    /// liste part des enregistrements (et non des observations, absentes par définition).
    static final String DE_SEQUENCE_AU_SITE = " FROM listening_sequence ls" + JOIN_SESSION_AU_SITE;

    /// Statut de revue **dérivé en SQL** (CASE), fidèle à `ServiceValidation#statut` : pas d'observateur →
    /// non touchée ; observateur = Tadarida → validée ; observateur ≠ Tadarida → corrigée. La décision
    /// tient à la **présence du `taxon_observer`**, pas à sa probabilité (un _Vu réel peut porter une
    /// confiance textuelle « SUR » lue comme prob inconnue). Facteur commun aux projections (#analyse).
    static final String CASE_STATUT = "CASE"
            + "   WHEN o.taxon_observer IS NULL THEN 'NON_TOUCHEE'"
            + "   WHEN o.taxon_observer = o.taxon_tadarida THEN 'VALIDEE'"
            + "   ELSE 'CORRIGEE'"
            + " END";

    /// Périmètre commun : observations **de l'utilisateur** (`?`), pseudo-taxons bruit/oiseau exclus.
    static final String FILTRE_UTILISATEUR_HORS_PSEUDO =
            " WHERE ms.user_id = ? AND COALESCE(o.taxon_observer, o.taxon_tadarida) NOT IN ('noise', 'piaf')";

    private FragmentsSqlObservation() {
        // Porte-constantes : jamais instanciée.
    }

    /// Lit un `INTEGER` nullable : `null` si la colonne vaut SQL NULL, sinon sa valeur.
    static Integer entierNullable(ResultSet rs, String colonne) throws SQLException {
        int valeur = rs.getInt(colonne);
        return rs.wasNull() ? null : valeur;
    }

    /// Lit une colonne `INTEGER` **nullable** en `Long` : `getLong` + [ResultSet#wasNull()], robuste quel
    /// que soit le type boxé rendu par le pilote (SQLite renvoie parfois `Integer` via `getObject`, d'où
    /// l'échec d'un cast direct `(Long)`).
    static Long longNullable(ResultSet rs, String colonne) throws SQLException {
        long valeur = rs.getLong(colonne);
        return rs.wasNull() ? null : valeur;
    }

    /// Instant de capture depuis l'horodatage persisté `recorded_at` (image ISO-8601 d'un [LocalDateTime],
    /// #530) : `null` si la séquence n'est pas horodatée. On projette l'**instant complet** (date + heure)
    /// pour un tri chronologique correct à cheval sur minuit (cf. `LigneObservationAudio#heureCapture`).
    static LocalDateTime heureCaptureDe(String recordedAt) {
        return recordedAt == null ? null : LocalDateTime.parse(recordedAt);
    }
}
