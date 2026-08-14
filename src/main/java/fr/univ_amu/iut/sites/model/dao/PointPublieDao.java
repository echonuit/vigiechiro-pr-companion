package fr.univ_amu.iut.sites.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.util.List;

/// Points d'écoute **publiés sur Vigie-Chiro** depuis Companion (#3458).
///
/// Table de **présence** (`point_publie`), même patron que `SiteTiersDao` : la ligne existe, donc le
/// point est en ligne. Le cas courant - un point rapatrié de la plateforme, donc jamais publié par
/// nous - ne coûte aucune ligne.
///
/// ⚠️ **À ne pas confondre avec `PointDEcoute#synchronise`**, qui dit « rapatrié **de** la plateforme »
/// et sert à masquer les points inutilisés (#1738). Un point publié va dans l'autre sens : c'est nous
/// qui l'avons poussé, et il doit rester visible.
public class PointPublieDao extends DaoGenerique<Long, Long> {

    private static final RowMapper<Long> MAPPER = rs -> rs.getLong("point_id");

    public PointPublieDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "point_publie";
    }

    @Override
    protected String colonneCle() {
        return "point_id";
    }

    @Override
    protected RowMapper<Long> mapper() {
        return MAPPER;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : table de présence (aucune colonne mutable).
    @Override
    public Long insert(Long idPoint) {
        marquer(idPoint);
        return idPoint;
    }

    /// Contrat [fr.univ_amu.iut.commun.persistence.Dao] : sans objet sur une ligne de présence.
    @Override
    public void update(Long idPoint) {
        marquer(idPoint);
    }

    /// Retient que ce point est en ligne. **Idempotent** : republier n'est pas une erreur.
    public void marquer(long idPoint) {
        executerMaj("INSERT OR IGNORE INTO point_publie(point_id) VALUES (?)", idPoint);
    }

    /// Ce point a-t-il été publié depuis Companion ?
    public boolean estPublie(long idPoint) {
        return findById(idPoint).isPresent();
    }

    /// Les points publiés d'un site, pour que la fiche n'interroge pas la base point par point.
    public List<Long> parSite(long idSite) {
        return query(
                "SELECT p.point_id FROM point_publie p JOIN listening_point l ON l.id = p.point_id"
                        + " WHERE l.site_id = ?",
                MAPPER,
                idSite);
    }
}
