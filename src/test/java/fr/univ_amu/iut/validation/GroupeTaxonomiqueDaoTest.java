package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.GroupeTaxonomique;
import fr.univ_amu.iut.validation.model.dao.GroupeTaxonomiqueDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [GroupeTaxonomiqueDao] sur une base SQLite jetable (@TempDir), initialisée par
/// [MigrationSchema]. `V02__seed_taxons.sql` sème déjà 5 groupes de référence : les tests
/// s'appuient dessus.
class GroupeTaxonomiqueDaoTest {

  @TempDir Path dossier;
  private GroupeTaxonomiqueDao dao;

  @BeforeEach
  void preparer() {
    SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
    new MigrationSchema(source).migrer();
    dao = new GroupeTaxonomiqueDao(source);
  }

  @Test
  @DisplayName("Les 5 groupes de référence sont semés par la migration V02")
  void les_groupes_de_reference_sont_semes() {
    assertThat(dao.findAll())
        .extracting(GroupeTaxonomique::nom)
        .contains("Pipistrellus", "Nyctalus", "Tadarida", "Rhinolophus", "Pseudo-taxons");
  }

  @Test
  @DisplayName("Insérer attribue un id et rend le groupe relisible")
  void inserer_attribue_un_id_et_rend_le_groupe_relisible() {
    GroupeTaxonomique insere =
        dao.insert(new GroupeTaxonomique(null, "Famille", "Vespertilionidae"));

    assertThat(insere.id()).as("la clé auto-incrémentée est renseignée").isNotNull();
    GroupeTaxonomique relu = dao.findById(insere.id()).orElseThrow();
    assertThat(relu.niveau()).isEqualTo("Famille");
    assertThat(relu.nom()).isEqualTo("Vespertilionidae");
  }

  @Test
  @DisplayName("Filtrer par niveau ne retient que les groupes du niveau demandé")
  void filtrer_par_niveau_ne_retient_que_le_niveau_demande() {
    dao.insert(new GroupeTaxonomique(null, "Famille", "Vespertilionidae"));

    assertThat(dao.findByNiveau("Genre"))
        .extracting(GroupeTaxonomique::nom)
        .contains("Pipistrellus", "Nyctalus")
        .doesNotContain("Vespertilionidae");
  }

  @Test
  @DisplayName("Mettre à jour modifie le niveau et le nom")
  void mettre_a_jour_modifie_les_champs() {
    GroupeTaxonomique insere = dao.insert(new GroupeTaxonomique(null, "Genre", "Myotis"));

    dao.update(new GroupeTaxonomique(insere.id(), "Famille", "Vespertilionidae"));

    GroupeTaxonomique relu = dao.findById(insere.id()).orElseThrow();
    assertThat(relu.niveau()).isEqualTo("Famille");
    assertThat(relu.nom()).isEqualTo("Vespertilionidae");
  }

  @Test
  @DisplayName("Supprimer retire le groupe")
  void supprimer_retire_le_groupe() {
    GroupeTaxonomique insere = dao.insert(new GroupeTaxonomique(null, "Genre", "Myotis"));
    assertThat(dao.findById(insere.id())).isPresent();

    dao.delete(insere.id());

    assertThat(dao.findById(insere.id())).isEmpty();
  }
}
