package fr.univ_amu.iut.qualification.model.dao;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// DAO de l'entité [SelectionDEcoute] (table `listening_selection`). Il gère en plus la table
/// de jonction N..N `selection_sequence` qui rattache des [SequenceSelectionnee] à une
/// sélection.
///
/// `findAll` / `findById` / `delete` sont hérités de [DaoGenerique]. Suppression d'une
/// sélection : la jonction part en cascade (`ON DELETE CASCADE`).
///
/// La [MethodeSelection] est sérialisée via son `libelle()` dans la colonne
/// `selection_method`, et relue via [MethodeSelection#parLibelle(String)] (patron de mapping
/// d'un énum). Le flag `listened` est stocké comme entier 0/1 (booléen SQLite).
public class SelectionDao extends DaoGenerique<SelectionDEcoute, Long> {

    private static final RowMapper<SelectionDEcoute> MAPPER = rs -> new SelectionDEcoute(
            rs.getLong("id"),
            MethodeSelection.parLibelle(rs.getString("selection_method")),
            rs.getInt("size"),
            rs.getLong("passage_id"));

    private static final RowMapper<SequenceSelectionnee> MAPPER_JONCTION = rs -> new SequenceSelectionnee(
            rs.getLong("selection_id"),
            rs.getLong("sequence_id"),
            rs.getInt("position"),
            rs.getInt("listened") != 0,
            VerdictFichier.parLibelleOuNonJuge(rs.getString("verdict")),
            VerdictFichier.parLibelleOuNonJuge(rs.getString("verdict_relecteur")),
            rs.getString("relecteur_pseudo"));

    public SelectionDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "listening_selection";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<SelectionDEcoute> mapper() {
        return MAPPER;
    }

    /// Sélection rattachée à un passage donné (relation 0:1 : au plus une sélection par passage,
    /// garanti par `UNIQUE(passage_id)`).
    public Optional<SelectionDEcoute> findByPassage(Long idPassage) {
        return queryUnique("SELECT * FROM listening_selection WHERE passage_id = ?", MAPPER, idPassage);
    }

    @Override
    public SelectionDEcoute insert(SelectionDEcoute selection) {
        long id = insererEtRecupererCle(
                "INSERT INTO listening_selection (selection_method, size, passage_id)" + " VALUES (?, ?, ?)",
                selection.methode().libelle(),
                selection.taille(),
                selection.idPassage());
        return new SelectionDEcoute(id, selection.methode(), selection.taille(), selection.idPassage());
    }

    @Override
    public void update(SelectionDEcoute selection) {
        executerMaj(
                "UPDATE listening_selection SET selection_method = ?, size = ?, passage_id = ? WHERE id = ?",
                selection.methode().libelle(),
                selection.taille(),
                selection.idPassage(),
                selection.id());
    }

    // ---------------------------------------------------------------------------
    // Jonction N..N selection_sequence
    // ---------------------------------------------------------------------------

    /// Rattache une séquence d'écoute à une sélection (insère une ligne dans
    /// `selection_sequence`). Les deux FK doivent exister, sinon `DataAccessException`
    /// (`foreign_keys=ON`).
    public void attacherSequence(SequenceSelectionnee rattachement) {
        executerMaj(
                "INSERT INTO selection_sequence (selection_id, sequence_id, position, listened, verdict)"
                        + " VALUES (?, ?, ?, ?, ?)",
                rattachement.idSelection(),
                rattachement.idSequence(),
                rattachement.position(),
                rattachement.ecoutee() ? 1 : 0,
                libelleOuNull(rattachement.verdict()));
    }

    /// Libellé d'un verdict par fichier pour le stockage, ou `null` si non jugé (colonne nullable :
    /// `NULL` ⇔ [VerdictFichier#NON_JUGE], cf. mapper).
    private static String libelleOuNull(VerdictFichier verdict) {
        return verdict == null || verdict == VerdictFichier.NON_JUGE ? null : verdict.libelle();
    }

    /// Séquences rattachées à une sélection, ordonnées par position d'affichage.
    ///
    /// Le helper `query` hérité de [DaoGenerique] est typé sur l'entité du DAO
    /// ([SelectionDEcoute]) ; la jonction renvoyant un autre type ([SequenceSelectionnee]), on
    /// exécute ici la requête directement (même patron technique : [PreparedStatement] +
    /// [RowMapper], `SQLException` enveloppée en [DataAccessException]).
    public List<SequenceSelectionnee> listerSequences(Long idSelection) {
        String sql = "SELECT * FROM selection_sequence WHERE selection_id = ? ORDER BY position";
        List<SequenceSelectionnee> resultats = new ArrayList<>();
        try (Connection connexion = source.getConnection();
                PreparedStatement ps = connexion.prepareStatement(sql)) {
            ps.setObject(1, idSelection);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultats.add(MAPPER_JONCTION.mapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la requête : " + sql, e);
        }
        return resultats;
    }

    /// Marque une séquence rattachée comme écoutée (flag `listened = 1`). Sans effet si le
    /// couple (sélection, séquence) n'est pas rattaché.
    public void marquerEcoutee(Long idSelection, Long idSequence) {
        executerMaj(
                "UPDATE selection_sequence SET listened = 1 WHERE selection_id = ? AND sequence_id = ?",
                idSelection,
                idSequence);
    }

    /// Enregistre le **verdict par fichier** d'une séquence rattachée (#1524, lot 5). Le verdict
    /// [VerdictFichier#NON_JUGE] repose la colonne à `NULL`. Sans effet si le couple (sélection,
    /// séquence) n'est pas rattaché.
    /// Enregistre l'avis d'un **relecteur** sur une séquence : son verdict et son pseudo, rangés à
    /// côté du nôtre sans le toucher (ADR 4517).
    ///
    /// Les deux colonnes s'écrivent **ensemble** : un verdict sans pseudo n'est signé par personne,
    /// un pseudo sans verdict ne juge rien.
    ///
    /// @param idSelection sélection portant la séquence
    /// @param idSequence séquence jugée
    /// @param verdict verdict du relecteur ; [VerdictFichier#NON_JUGE] efface l'avis, pseudo compris
    /// @param pseudo pseudo du relecteur, ignoré quand le verdict efface l'avis
    public void marquerAvisDeRelecteur(Long idSelection, Long idSequence, VerdictFichier verdict, String pseudo) {
        boolean efface = verdict == null || verdict == VerdictFichier.NON_JUGE;
        executerMaj(
                "UPDATE selection_sequence SET verdict_relecteur = ?, relecteur_pseudo = ?"
                        + " WHERE selection_id = ? AND sequence_id = ?",
                libelleOuNull(verdict),
                efface ? null : pseudo,
                idSelection,
                idSequence);
    }

    public void marquerVerdict(Long idSelection, Long idSequence, VerdictFichier verdict) {
        executerMaj(
                "UPDATE selection_sequence SET verdict = ? WHERE selection_id = ? AND sequence_id = ?",
                libelleOuNull(verdict),
                idSelection,
                idSequence);
    }

    // ---------------------------------------------------------------------------
    // Surcharges « connection-aware » pour les écritures atomiques multi-tables
    // ---------------------------------------------------------------------------
    //
    // Constituer une sélection = insérer la ligne listening_selection PUIS N lignes
    // selection_sequence : c'est une opération multi-tables qui doit être « tout ou rien ».
    // Conformément à SERVICE-CONVENTIONS §2.5, on ne peut PAS envelopper les méthodes DAO
    // standard (chacune ouvre sa propre connexion auto-commit) dans une UniteDeTravail sans
    // créer une fausse transaction (et risquer un « database is locked » sous SQLite). On
    // expose donc des surcharges qui réutilisent la connexion transactionnelle fournie par
    // UniteDeTravail.executer(cx -> ...). Le SQL reste dans le DAO (convention IMPL §3).

    /// Insère une sélection sur la connexion transactionnelle fournie et renvoie sa clé générée.
    /// À appeler dans un bloc `UniteDeTravail.executer`.
    public long insererDansTransaction(Connection connexion, MethodeSelection methode, int taille, Long idPassage)
            throws SQLException {
        try (PreparedStatement ps = connexion.prepareStatement(
                "INSERT INTO listening_selection (selection_method, size, passage_id) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, methode.libelle());
            ps.setInt(2, taille);
            ps.setObject(3, idPassage);
            ps.executeUpdate();
            try (ResultSet cles = ps.getGeneratedKeys()) {
                if (cles.next()) {
                    return cles.getLong(1);
                }
                throw new SQLException("Aucune clé générée pour l'insertion de la sélection.");
            }
        }
    }

    /// Rattache une séquence à une sélection sur la connexion transactionnelle fournie. À
    /// appeler dans un bloc `UniteDeTravail.executer`.
    public void attacherDansTransaction(
            Connection connexion, long idSelection, long idSequence, int position, boolean ecoutee)
            throws SQLException {
        try (PreparedStatement ps = connexion.prepareStatement(
                "INSERT INTO selection_sequence (selection_id, sequence_id, position, listened)"
                        + " VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, idSelection);
            ps.setObject(2, idSequence);
            ps.setInt(3, position);
            ps.setInt(4, ecoutee ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /// Supprime une sélection (et, par cascade, ses rattachements) sur la connexion
    /// transactionnelle fournie. Sert à reconstituer atomiquement une sélection lors d'un
    /// changement de méthode/taille.
    public void supprimerDansTransaction(Connection connexion, long idSelection) throws SQLException {
        try (PreparedStatement ps = connexion.prepareStatement("DELETE FROM listening_selection WHERE id = ?")) {
            ps.setObject(1, idSelection);
            ps.executeUpdate();
        }
    }
}
