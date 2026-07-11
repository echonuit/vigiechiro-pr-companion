package fr.univ_amu.iut.commun.model.dao;

import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/// DAO de la **disposition des colonnes par écran** (table `column_layout`, migration V19, #994) : lecture
/// et **upsert** d'une disposition (JSON opaque) par `(feature, table_key)`.
///
/// Contrairement aux DAO d'entités du dépôt, il **n'étend pas** [fr.univ_amu.iut.commun.persistence.DaoGenerique]
/// (CRUD par clé simple, avec `insert`/`update`/`delete` d'entité) : la table a une **clé composite** et une
/// seule opération d'écriture utile (l'upsert). Il implémente donc directement le port
/// [DepotDispositionColonnes] en JDBC.
public class DispositionColonnesDao implements DepotDispositionColonnes {

    private final SourceDeDonnees source;

    public DispositionColonnesDao(SourceDeDonnees source) {
        this.source = source;
    }

    @Override
    public Optional<String> charger(String feature, String cle) {
        String sql = "SELECT layout_json FROM column_layout WHERE feature = ? AND table_key = ?";
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, feature);
            ps.setString(2, cle);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("layout_json")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec du chargement de la disposition des colonnes", e);
        }
    }

    @Override
    public void enregistrer(String feature, String cle, String layoutJson) {
        String sql = "INSERT INTO column_layout (feature, table_key, layout_json) VALUES (?, ?, ?)"
                + " ON CONFLICT (feature, table_key) DO UPDATE SET layout_json = excluded.layout_json";
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, feature);
            ps.setString(2, cle);
            ps.setString(3, layoutJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Échec de l'enregistrement de la disposition des colonnes", e);
        }
    }
}
