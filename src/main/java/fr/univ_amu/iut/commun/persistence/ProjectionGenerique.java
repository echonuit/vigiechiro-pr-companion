package fr.univ_amu.iut.commun.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/// Base technique des **lectures SQL** : ouverture de connexion, liaison des paramètres
/// [PreparedStatement] et itération du [ResultSet] vers un [RowMapper].
///
/// Deux familles de classes s'appuient dessus :
///
/// - les **DAO d'entité** via [DaoGenerique] (qui y ajoute le contrat CRUD [Dao] et les helpers
///   d'écriture) ;
/// - les **DAO de projection** en lecture seule (#1193), qui étendent directement cette base : une
///   projection transverse ne porte ni table propre ni écriture, lui imposer `insert`/`update`
///   n'aurait pas de sens.
///
/// **Aucun SQL métier ici** : le SQL vit dans les DAO de chaque feature.
public abstract class ProjectionGenerique {

    protected final SourceDeDonnees source;

    protected ProjectionGenerique(SourceDeDonnees source) {
        this.source = source;
    }

    /// Exécute un `SELECT` et mappe chaque ligne vers un type de **projection** arbitraire `R` (lecture
    /// transverse ne correspondant pas à une entité, p. ex. une agrégation jointe à d'autres tables).
    protected <R> List<R> projeter(String sql, RowMapper<R> mapper, Object... parametres) {
        List<R> resultats = new ArrayList<>();
        try (Connection connexion = source.getConnection();
                PreparedStatement ps = connexion.prepareStatement(sql)) {
            lier(ps, parametres);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultats.add(mapper.mapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la requête : " + sql, e);
        }
        return resultats;
    }

    /// Lie les paramètres positionnels (1-based), en gérant explicitement les valeurs nulles.
    protected static void lier(PreparedStatement ps, Object... parametres) throws SQLException {
        for (int i = 0; i < parametres.length; i++) {
            Object valeur = parametres[i];
            if (valeur == null) {
                ps.setString(i + 1, null);
            } else {
                ps.setObject(i + 1, valeur);
            }
        }
    }
}
