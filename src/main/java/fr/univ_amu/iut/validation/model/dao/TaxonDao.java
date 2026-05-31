package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Taxon;
import java.util.List;

/// DAO de l'entité [Taxon] (table `taxon`).
///
/// Illustre une **clé naturelle** (`code`, TEXT) : comme `UtilisateurDao`, l'insertion n'utilise
/// pas `insererEtRecupererCle` (aucune clé générée) mais [#executerMaj(String, Object...)], et
/// renvoie l'entité telle quelle. Les colonnes nom latin / nom vernaculaire sont nullable (lues
/// directement via `rs.getString`, qui renvoie `null`).
public class TaxonDao extends DaoGenerique<Taxon, String> {

  private static final RowMapper<Taxon> MAPPER =
      rs ->
          new Taxon(
              rs.getString("code"),
              rs.getString("latin_name"),
              rs.getString("vernacular_name_fr"),
              rs.getLong("group_id"));

  public TaxonDao(SourceDeDonnees source) {
    super(source);
  }

  @Override
  protected String table() {
    return "taxon";
  }

  @Override
  protected String colonneCle() {
    return "code";
  }

  @Override
  protected RowMapper<Taxon> mapper() {
    return MAPPER;
  }

  /// Taxons rattachés à un groupe taxonomique donné, triés par code.
  public List<Taxon> findByGroupe(Long idGroupe) {
    return query("SELECT * FROM taxon WHERE group_id = ? ORDER BY code", MAPPER, idGroupe);
  }

  @Override
  public Taxon insert(Taxon taxon) {
    executerMaj(
        "INSERT INTO taxon (code, latin_name, vernacular_name_fr, group_id) VALUES (?, ?, ?, ?)",
        taxon.code(),
        taxon.nomLatin(),
        taxon.nomVernaculaireFr(),
        taxon.idGroupe());
    return taxon;
  }

  @Override
  public void update(Taxon taxon) {
    executerMaj(
        "UPDATE taxon SET latin_name = ?, vernacular_name_fr = ?, group_id = ? WHERE code = ?",
        taxon.nomLatin(),
        taxon.nomVernaculaireFr(),
        taxon.idGroupe(),
        taxon.code());
  }
}
