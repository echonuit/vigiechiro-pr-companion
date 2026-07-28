package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Passage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/// DAO de l'entité centrale [Passage] (table `passage`).
///
/// Met en œuvre deux patrons de mapping d'énum : [StatutWorkflow] est **obligatoire** (relu
/// directement par `parLibelle`), tandis que [Verdict] est **nullable** (un passage pas encore
/// vérifié n'a pas de verdict) : on lit la colonne en `String` d'abord et on ne convertit que si
/// elle est non nulle (cf. [#lireVerdict(ResultSet)]). On stocke toujours le `libelle()` de l'énum
/// (jamais son `name()`).
///
/// La contrainte d'unicité R5 (`UNIQUE(point_id, year, passage_number)`) est portée par le schéma
/// : une seconde insertion du même quadruplet lève une `DataAccessException`.
public class PassageDao extends DaoGenerique<Passage, Long> {

    private static final RowMapper<Passage> MAPPER = rs -> new Passage(
            rs.getLong("id"),
            rs.getInt("passage_number"),
            rs.getInt("year"),
            rs.getString("recording_date"),
            rs.getString("start_time"),
            rs.getString("end_time"),
            rs.getString("acquisition_params"),
            StatutWorkflow.parLibelle(rs.getString("workflow_status")),
            lireVerdict(rs),
            rs.getString("comment"),
            rs.getString("weather_data"),
            rs.getString("deposited_at"),
            rs.getLong("point_id"),
            rs.getString("recorder_id"),
            lireLongNullable(rs, "campaign_id"));

    public PassageDao(SourceDeDonnees source) {
        super(source);
    }

    /// Lit le verdict nullable : `null` en base reste `null` (passage non vérifié).
    private static Verdict lireVerdict(ResultSet rs) throws SQLException {
        String libelle = rs.getString("verification_verdict");
        return libelle == null ? null : Verdict.parLibelle(libelle);
    }

    /// Lit une clé étrangère `Long` nullable : `null` en base reste `null` (ici, passage non rattaché
    /// à une campagne).
    private static Long lireLongNullable(ResultSet rs, String colonne) throws SQLException {
        long valeur = rs.getLong(colonne);
        return rs.wasNull() ? null : valeur;
    }

    @Override
    protected String table() {
        return "passage";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<Passage> mapper() {
        return MAPPER;
    }

    /// Passages d'un point d'écoute, triés par année puis n° de passage.
    public List<Passage> findByPoint(Long idPoint) {
        return query("SELECT * FROM passage WHERE point_id = ? ORDER BY year, passage_number", MAPPER, idPoint);
    }

    /// Nombre de passages **rattachés** à une campagne (#2630).
    ///
    /// Compté et non listé : le seul appelant est la confirmation de suppression d'une campagne, qui
    /// annonce combien de nuits seront **détachées**. Charger les passages pour n'en garder que le
    /// nombre coûterait cher là où l'observateur en a des centaines.
    public long compterParCampagne(Long idCampagne) {
        return compterSi("campaign_id = ?", idCampagne);
    }

    /// Passages produits par un enregistreur donné.
    public List<Passage> findByEnregistreur(String idEnregistreur) {
        return query(
                "SELECT * FROM passage WHERE recorder_id = ? ORDER BY year, passage_number", MAPPER, idEnregistreur);
    }

    /// Passages dans un statut de workflow donné (ex. tous les « Prêt à déposer »).
    public List<Passage> findByStatut(StatutWorkflow statut) {
        return query(
                "SELECT * FROM passage WHERE workflow_status = ? ORDER BY year, passage_number",
                MAPPER,
                statut.libelle());
    }

    /// Recherche par quadruplet R5 (le point déterminant le site) : au plus un passage.
    public Optional<Passage> trouverParPointAnneePassage(Long idPoint, int annee, int numeroPassage) {
        return queryUnique(
                "SELECT * FROM passage WHERE point_id = ? AND year = ? AND passage_number = ?",
                MAPPER,
                idPoint,
                annee,
                numeroPassage);
    }

    @Override
    public Passage insert(Passage passage) {
        long id = insererEtRecupererCle(
                "INSERT INTO passage"
                        + " (passage_number, year, recording_date, start_time, end_time,"
                        + " acquisition_params, workflow_status, verification_verdict, comment,"
                        + " weather_data, deposited_at, point_id, recorder_id, campaign_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                passage.statutWorkflow().libelle(),
                passage.verdictVerification() == null
                        ? null
                        : passage.verdictVerification().libelle(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne());
        return new Passage(
                id,
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                passage.statutWorkflow(),
                passage.verdictVerification(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne());
    }

    @Override
    public void update(Passage passage) {
        executerMaj(
                "UPDATE passage SET"
                        + " passage_number = ?, year = ?, recording_date = ?, start_time = ?, end_time = ?,"
                        + " acquisition_params = ?, workflow_status = ?, verification_verdict = ?, comment = ?,"
                        + " weather_data = ?, deposited_at = ?, point_id = ?, recorder_id = ?, campaign_id = ?"
                        + " WHERE id = ?",
                passage.numeroPassage(),
                passage.annee(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin(),
                passage.parametresAcquisition(),
                passage.statutWorkflow().libelle(),
                passage.verdictVerification() == null
                        ? null
                        : passage.verdictVerification().libelle(),
                passage.commentaire(),
                passage.donneesMeteo(),
                passage.deposeLe(),
                passage.idPoint(),
                passage.idEnregistreur(),
                passage.idCampagne(),
                passage.id());
    }
}
