package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.GroupeTaxonomique;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.dao.GroupeTaxonomiqueDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [TaxonDao] (clé naturelle `code`) + contraintes : FK vers le groupe taxonomique,
/// unicité de la clé naturelle, et refus de supprimer un groupe encore référencé. Les taxons fil
/// rouge et pseudo-taxons sont déjà semés par `V02__seed_taxons.sql`.
class TaxonDaoTest {

  @TempDir Path dossier;
  private TaxonDao dao;
  private Long idGenrePipistrellus;

  @BeforeEach
  void preparer() {
    SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
    new MigrationSchema(source).migrer();
    idGenrePipistrellus =
        new GroupeTaxonomiqueDao(source)
            .findByNiveau("Genre").stream()
                .filter(groupe -> "Pipistrellus".equals(groupe.nom()))
                .map(GroupeTaxonomique::id)
                .findFirst()
                .orElseThrow();
    dao = new TaxonDao(source);
  }

  @Test
  @DisplayName("Les taxons fil rouge sont semés et relisibles par leur code")
  void les_taxons_fil_rouge_sont_semes() {
    Taxon pippip = dao.findById("Pippip").orElseThrow();

    assertThat(pippip.nomLatin()).isEqualTo("Pipistrellus pipistrellus");
    assertThat(pippip.nomVernaculaireFr()).isEqualTo("Pipistrelle commune");
    assertThat(dao.findById("Nyclei")).isPresent();
    assertThat(dao.findById("Tadten")).isPresent();
    assertThat(dao.findById("Rhihip")).isPresent();
  }

  @Test
  @DisplayName("Le pseudo-taxon noise a un nom latin nul")
  void pseudo_taxon_a_un_nom_latin_nul() {
    Taxon noise = dao.findById("noise").orElseThrow();

    assertThat(noise.nomLatin()).isNull();
    assertThat(noise.nomVernaculaireFr()).isEqualTo("Bruit");
    assertThat(dao.findById("piaf")).isPresent();
  }

  @Test
  @DisplayName("Insérer rend la clé naturelle telle quelle et le taxon relisible")
  void inserer_rend_la_cle_naturelle_et_le_taxon_relisible() {
    Taxon insere =
        dao.insert(new Taxon("Myomyo", "Myotis myotis", "Grand murin", idGenrePipistrellus));

    assertThat(insere.code()).isEqualTo("Myomyo");
    Taxon relu = dao.findById("Myomyo").orElseThrow();
    assertThat(relu.nomVernaculaireFr()).isEqualTo("Grand murin");
    assertThat(relu.idGroupe()).isEqualTo(idGenrePipistrellus);
  }

  @Test
  @DisplayName("Filtrer par groupe ne retient que les taxons de ce groupe")
  void filtrer_par_groupe() {
    dao.insert(
        new Taxon(
            "Pipnat", "Pipistrellus nathusii", "Pipistrelle de Nathusius", idGenrePipistrellus));

    assertThat(dao.findByGroupe(idGenrePipistrellus))
        .extracting(Taxon::code)
        .contains("Pippip", "Pipnat");
  }

  @Test
  @DisplayName("Un code déjà présent est rejeté (clé naturelle unique)")
  void unicite_de_la_cle_naturelle() {
    dao.insert(new Taxon("Myomyo", "Myotis myotis", "Grand murin", idGenrePipistrellus));

    assertThatThrownBy(
            () -> dao.insert(new Taxon("Myomyo", "Autre", "Doublon", idGenrePipistrellus)))
        .as("la PK naturelle interdit deux taxons de même code")
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("Un groupe inconnu est rejeté (FK active)")
  void clef_etrangere_active_un_groupe_inconnu_est_rejete() {
    assertThatThrownBy(() -> dao.insert(new Taxon("Xxxxxx", null, null, 9999L)))
        .as("PRAGMA foreign_keys=ON doit refuser une FK vers un groupe absent")
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("Supprimer un groupe encore référencé par un taxon est rejeté")
  void supprimer_un_groupe_reference_est_rejete() {
    GroupeTaxonomiqueDao groupes =
        new GroupeTaxonomiqueDao(new SourceDeDonnees(new Workspace(dossier)));

    assertThatThrownBy(() -> groupes.delete(idGenrePipistrellus))
        .as("le groupe Pipistrellus est référencé par le taxon Pippip : suppression refusée")
        .isInstanceOf(DataAccessException.class);
  }
}
