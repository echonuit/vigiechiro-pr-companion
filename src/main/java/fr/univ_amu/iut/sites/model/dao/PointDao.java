package fr.univ_amu.iut.sites.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.LotsDeParametres;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// DAO de l'entité [PointDEcoute] (table `listening_point`).
///
/// Illustre le mapping de colonnes **nullable** : les coordonnées GPS (`REAL`) peuvent être
/// absentes, on les lit donc via `rs.getObject(...)` (qui renvoie `null`) plutôt que
/// `rs.getDouble(...)` (qui renverrait 0.0).
public class PointDao extends DaoGenerique<PointDEcoute, Long> {

    private static final RowMapper<PointDEcoute> MAPPER = rs -> new PointDEcoute(
            rs.getLong("id"),
            rs.getString("code"),
            (Double) rs.getObject("gps_lat"),
            (Double) rs.getObject("gps_lon"),
            rs.getString("description"),
            rs.getLong("site_id"),
            rs.getInt("synchronise") != 0);

    public PointDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "listening_point";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<PointDEcoute> mapper() {
        return MAPPER;
    }

    /// Points d'écoute d'un site donné, triés par code.
    public List<PointDEcoute> findBySite(Long idSite) {
        return query("SELECT * FROM listening_point WHERE site_id = ? ORDER BY code", MAPPER, idSite);
    }

    /// Les points de **plusieurs sites à la fois**, groupés par site (#4251).
    ///
    /// ## Pourquoi cette lecture existe
    ///
    /// Quatre endroits appelaient [#findBySite(Long)] **dans une boucle** sur les sites, et l'un d'eux
    /// enchaînait une seconde boucle sur les passages de chaque point. Mesuré sur soixante carrés de deux
    /// points : cent quatre-vingts requêtes, et **487 ms** pour ouvrir « Mes sites » - là où l'écran
    /// « Espèces & observations » charge quatre fois plus de lignes en huit millisecondes, parce qu'il
    /// lit par ensembles.
    ///
    /// ⚠️ **Le lot est découpé.** SQLite refuse au-delà de quelques centaines de paramètres liés
    /// (`SQLITE_MAX_VARIABLE_NUMBER`), et un observateur qui suit trois cents carrés dépasserait la borne
    /// en silence - l'appel échouerait là où la boucle, elle, marchait. Le découpage rend la lecture
    /// sûre quel que soit l'inventaire, au prix d'une requête par tranche.
    ///
    /// @param idsSites les sites voulus ; un identifiant sans point rend une liste vide, pas d'entrée
    /// @return une entrée par site **ayant au moins un point**
    public Map<Long, List<PointDEcoute>> findParSites(Collection<Long> idsSites) {
        Map<Long, List<PointDEcoute>> parSite = new HashMap<>();
        for (List<Long> tranche : LotsDeParametres.decouper(idsSites)) {
            String trous = tranche.stream().map(id -> "?").collect(Collectors.joining(", "));
            query(
                            "SELECT * FROM listening_point WHERE site_id IN (" + trous + ") ORDER BY code",
                            MAPPER,
                            tranche.toArray())
                    .forEach(point -> parSite.computeIfAbsent(point.idSite(), cle -> new ArrayList<>())
                            .add(point));
        }
        return parSite;
    }

    @Override
    public PointDEcoute insert(PointDEcoute point) {
        long id = insererEtRecupererCle(
                "INSERT INTO listening_point (code, gps_lat, gps_lon, description, site_id, synchronise)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                point.code(),
                point.latitude(),
                point.longitude(),
                point.description(),
                point.idSite(),
                point.synchronise() ? 1 : 0);
        return new PointDEcoute(
                id,
                point.code(),
                point.latitude(),
                point.longitude(),
                point.description(),
                point.idSite(),
                point.synchronise());
    }

    @Override
    public void update(PointDEcoute point) {
        executerMaj(
                "UPDATE listening_point SET code = ?, gps_lat = ?, gps_lon = ?, description = ?, site_id = ?"
                        + " WHERE id = ?",
                point.code(),
                point.latitude(),
                point.longitude(),
                point.description(),
                point.idSite(),
                point.id());
    }
}
