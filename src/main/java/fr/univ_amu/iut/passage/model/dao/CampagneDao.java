package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Campagne;
import java.util.List;

/// DAO de l'entité [Campagne] (table `campagne`). `findAll` / `findById` / `delete` sont fournis par
/// [DaoGenerique] ; seules les écritures (`insert` / `update`) et le tri de listage sont ici.
public class CampagneDao extends DaoGenerique<Campagne, Long> {

    private static final RowMapper<Campagne> MAPPER =
            rs -> new Campagne(rs.getLong("id"), rs.getString("name"), rs.getInt("year"), rs.getString("comment"));

    public CampagneDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "campagne";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<Campagne> mapper() {
        return MAPPER;
    }

    /// Toutes les campagnes, de la plus récente à la plus ancienne puis par nom (ordre déterministe).
    public List<Campagne> toutes() {
        return query("SELECT * FROM campagne ORDER BY year DESC, name", MAPPER);
    }

    @Override
    public Campagne insert(Campagne campagne) {
        long id = insererEtRecupererCle(
                "INSERT INTO campagne (name, year, comment) VALUES (?, ?, ?)",
                campagne.nom(),
                campagne.annee(),
                campagne.commentaire());
        return new Campagne(id, campagne.nom(), campagne.annee(), campagne.commentaire());
    }

    @Override
    public void update(Campagne campagne) {
        executerMaj(
                "UPDATE campagne SET name = ?, year = ?, comment = ? WHERE id = ?",
                campagne.nom(),
                campagne.annee(),
                campagne.commentaire(),
                campagne.id());
    }
}
