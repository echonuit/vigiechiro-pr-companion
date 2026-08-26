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
    /// enchaînait une seconde boucle sur les passages de chaque point : cent quatre-vingts requêtes pour
    /// soixante carrés de deux points.
    ///
    /// Mesuré avec préchauffage, trois essais : « Mes sites » passait de **165-241 ms à cent cinquante
    /// carrés** à 6-8 ms. Le gain n'est pas un facteur, c'est une **pente qui disparaît** - le coût
    /// d'avant doublait de soixante à cent cinquante carrés, celui d'après ne bouge pas.
    ///
    /// Ce commentaire a d'abord annoncé « 487 ms ». C'était la **première mesure de son processus**,
    /// et le démarrage d'une JVM coûte ~300 ms quelle que soit la taille des données.
    ///
    /// Le lot est découpé ([LotsDeParametres]), ce qui borne la **taille de la requête** - et non, comme
    /// ce commentaire l'a aussi prétendu, pour contourner un refus de SQLite qui n'existe pas ici.
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
