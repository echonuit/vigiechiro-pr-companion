package fr.univ_amu.iut.sites.model.dao;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.CommuneDuPoint;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/// DAO de la commune d'un point d'écoute (table latérale `point_commune`, #2791).
///
/// Table latérale de **valeurs** : une ligne porte la [Commune] résolue du point, l'**absence** de
/// ligne dit « commune non résolue ». Même isolement hors du record
/// [fr.univ_amu.iut.sites.model.PointDEcoute] que [SiteTiersDao] pour `site_tiers` (EPIC arité
/// #2483) : le point ne change pas, sa commune est un fait dérivé et recalculable.
public class PointCommuneDao extends DaoGenerique<CommuneDuPoint, Long> {

    private static final RowMapper<CommuneDuPoint> MAPPER = rs -> new CommuneDuPoint(
            rs.getLong("point_id"), new Commune(rs.getString("commune_name"), rs.getString("commune_insee")));

    public PointCommuneDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "point_commune";
    }

    @Override
    protected String colonneCle() {
        return "point_id";
    }

    @Override
    protected RowMapper<CommuneDuPoint> mapper() {
        return MAPPER;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : pose ou remplace la commune (idempotent).
    /// À préférer via [#definir].
    @Override
    public CommuneDuPoint insert(CommuneDuPoint ligne) {
        definir(ligne.idPoint(), ligne.commune());
        return ligne;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : même upsert que [#insert].
    @Override
    public void update(CommuneDuPoint ligne) {
        definir(ligne.idPoint(), ligne.commune());
    }

    /// La commune résolue du point, ou vide si aucune ligne (non résolue).
    public Optional<Commune> pour(long idPoint) {
        return findById(idPoint).map(CommuneDuPoint::commune);
    }

    /// Pose ou remplace la commune du point (upsert idempotent).
    public void definir(long idPoint, Commune commune) {
        executerMaj(
                "INSERT INTO point_commune (point_id, commune_name, commune_insee) VALUES (?, ?, ?)"
                        + " ON CONFLICT(point_id) DO UPDATE"
                        + " SET commune_name = excluded.commune_name, commune_insee = excluded.commune_insee",
                idPoint,
                commune.nom(),
                commune.codeInsee());
    }

    /// Efface la commune du point (idempotent) : elle redevient « non résolue ».
    public void effacer(long idPoint) {
        delete(idPoint);
    }

    /// Identifiants de **tous** les points dont la commune est résolue (lecture groupée : le
    /// rattrapage écarte en un seul accès ceux qui n'ont rien à faire).
    public Set<Long> idsResolus() {
        Set<Long> ids = new HashSet<>();
        for (CommuneDuPoint ligne : findAll()) {
            ids.add(ligne.idPoint());
        }
        return ids;
    }
}
