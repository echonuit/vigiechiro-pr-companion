package fr.univ_amu.iut.commun.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/// Exécute un bloc de travail dans une **transaction atomique** (begin / commit / rollback).
///
/// Par défaut, chaque appel DAO s'auto-commit. Quand plusieurs écritures doivent réussir ou
/// échouer ensemble (ex. créer un passage et sa session d'enregistrement), on les regroupe dans une
/// unité de travail : si le bloc lève une exception, **tout est annulé** (rollback) et la base
/// reste cohérente (objectif qualité intégrité / résilience O7).
///
/// ```
/// uniteDeTravail.executer(connexion -> {
///   // plusieurs écritures sur la même connexion...
/// }); // commit si tout s'est bien passé, rollback sinon
/// ```
public class UniteDeTravail {

    private final SourceDeDonnees source;

    public UniteDeTravail(SourceDeDonnees source) {
        this.source = source;
    }

    /// Ouvre une connexion, désactive l'auto-commit, exécute `travail`, puis valide (commit). En
    /// cas d'erreur, annule (rollback) et propage une [DataAccessException].
    public void executer(TravailTransactionnel travail) {
        try (Connection connexion = source.getConnection()) {
            boolean autoCommitInitial = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                travail.executer(connexion);
                connexion.commit();
            } catch (SQLException | RuntimeException erreur) {
                connexion.rollback();
                throw qualifiee(erreur);
            } finally {
                connexion.setAutoCommit(autoCommitInitial);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec d'ouverture de la transaction", e);
        }
    }

    /// Situe une erreur nue, laisse passer une erreur déjà située.
    ///
    /// Un bloc qui sait ce qu'il faisait lève volontiers une [DataAccessException] qui **nomme** son
    /// échec et dit dans quel état la base se retrouve (ainsi de [MigrationSchema], qui donne le
    /// script et l'instruction). La réemballer enfouirait ce message sous un « Transaction annulée »
    /// générique, et c'est le générique qu'on lirait en premier. Les autres erreurs, elles, remontent
    /// nues du pilote JDBC et gagnent à être situées.
    private static DataAccessException qualifiee(Exception erreur) {
        return erreur instanceof DataAccessException deja
                ? deja
                : new DataAccessException("Transaction annulée (rollback)", erreur);
    }

    /// Bloc de travail s'exécutant sur la connexion transactionnelle fournie.
    @FunctionalInterface
    public interface TravailTransactionnel {
        void executer(Connection connexion) throws SQLException;
    }
}
