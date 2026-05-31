package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.GroupeTaxonomique;
import java.util.List;

/// DAO de l'entité [GroupeTaxonomique] (table `taxonomic_group`, clé auto-incrémentée).
///
/// `findAll` / `findById` / `delete` sont hérités de [DaoGenerique]. Les groupes de référence
/// sont déjà semés par `V02__seed_taxons.sql` : ce DAO sert surtout à les lire et à filtrer par
/// niveau hiérarchique.
public class GroupeTaxonomiqueDao extends DaoGenerique<GroupeTaxonomique, Long> {

  private static final RowMapper<GroupeTaxonomique> MAPPER =
      rs -> new GroupeTaxonomique(rs.getLong("id"), rs.getString("level"), rs.getString("name"));

  public GroupeTaxonomiqueDao(SourceDeDonnees source) {
    super(source);
  }

  @Override
  protected String table() {
    return "taxonomic_group";
  }

  @Override
  protected String colonneCle() {
    return "id";
  }

  @Override
  protected RowMapper<GroupeTaxonomique> mapper() {
    return MAPPER;
  }

  /// Groupes d'un niveau hiérarchique donné (ex. `"Genre"`), triés par nom.
  public List<GroupeTaxonomique> findByNiveau(String niveau) {
    return query("SELECT * FROM taxonomic_group WHERE level = ? ORDER BY name", MAPPER, niveau);
  }

  @Override
  public GroupeTaxonomique insert(GroupeTaxonomique groupe) {
    long id =
        insererEtRecupererCle(
            "INSERT INTO taxonomic_group (level, name) VALUES (?, ?)",
            groupe.niveau(),
            groupe.nom());
    return new GroupeTaxonomique(id, groupe.niveau(), groupe.nom());
  }

  @Override
  public void update(GroupeTaxonomique groupe) {
    executerMaj(
        "UPDATE taxonomic_group SET level = ?, name = ? WHERE id = ?",
        groupe.niveau(),
        groupe.nom(),
        groupe.id());
  }
}
