package fr.univ_amu.iut.commun.model.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [VueSauvegardeeDao] sur une base SQLite jetable (@TempDir), initialisée par [MigrationSchema]
/// (table `saved_filter_view`, migration V11). On vérifie le CRUD relisible, la requête par **feature**
/// (chaque écran ne voit que ses vues) et les contraintes `NOT NULL`.
class VueSauvegardeeDaoTest {

    private static final String DESCRIPTEUR =
            "{\"texte\":\"\",\"criteres\":[{\"nom\":\"statut\",\"valeurs\":[\"VALIDEE\"]}]}";

    @TempDir
    Path dossier;

    private VueSauvegardeeDao dao;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new VueSauvegardeeDao(source);
    }

    private static VueSauvegardee nouvelleVue(String feature, String nom) {
        return new VueSauvegardee(null, feature, nom, DESCRIPTEUR);
    }

    @Test
    @DisplayName("Insérer attribue un id et rend la vue relisible (feature + descripteur JSON inclus)")
    void inserer_attribue_un_id_et_rend_la_vue_relisible() {
        VueSauvegardee insere = dao.insert(nouvelleVue("multisite", "Déposés 2026"));

        assertThat(insere.id()).as("la clé auto-incrémentée est renseignée").isNotNull();
        VueSauvegardee relue = dao.findById(insere.id()).orElseThrow();
        assertThat(relue.feature()).isEqualTo("multisite");
        assertThat(relue.nom()).isEqualTo("Déposés 2026");
        assertThat(relue.descripteurJson())
                .as("le descripteur JSON est persisté tel quel")
                .isEqualTo(DESCRIPTEUR);
    }

    @Test
    @DisplayName("Mettre à jour modifie le nom et le descripteur")
    void mettre_a_jour_modifie_les_champs() {
        VueSauvegardee insere = dao.insert(nouvelleVue("analyse", "Brouillon"));

        dao.update(new VueSauvegardee(
                insere.id(), "analyse", "Vue définitive", "{\"texte\":\"noctule\",\"criteres\":[]}"));

        VueSauvegardee relue = dao.findById(insere.id()).orElseThrow();
        assertThat(relue.nom()).isEqualTo("Vue définitive");
        assertThat(relue.descripteurJson()).isEqualTo("{\"texte\":\"noctule\",\"criteres\":[]}");
    }

    @Test
    @DisplayName("Supprimer retire la vue")
    void supprimer_retire_la_vue() {
        VueSauvegardee insere = dao.insert(nouvelleVue("audio", "À supprimer"));
        assertThat(dao.findById(insere.id())).isPresent();

        dao.delete(insere.id());

        assertThat(dao.findById(insere.id())).isEmpty();
    }

    @Test
    @DisplayName("findByFeature ne remonte que les vues de l'écran demandé")
    void rechercher_par_feature_isole_les_ecrans() {
        dao.insert(nouvelleVue("multisite", "Vue M1"));
        dao.insert(nouvelleVue("multisite", "Vue M2"));
        dao.insert(nouvelleVue("analyse", "Vue A1"));

        assertThat(dao.findByFeature("multisite"))
                .extracting(VueSauvegardee::nom)
                .containsExactly("Vue M1", "Vue M2");
        assertThat(dao.findByFeature("analyse")).extracting(VueSauvegardee::nom).containsExactly("Vue A1");
        assertThat(dao.findByFeature("audio")).isEmpty();
    }

    @Test
    @DisplayName("findAll restitue toutes les vues, toutes features confondues")
    void lister_restitue_toutes_les_vues() {
        dao.insert(nouvelleVue("multisite", "Vue M"));
        dao.insert(nouvelleVue("analyse", "Vue A"));

        assertThat(dao.findAll()).extracting(VueSauvegardee::nom).containsExactlyInAnyOrder("Vue M", "Vue A");
    }

    @Test
    @DisplayName("La feature est obligatoire (contrainte NOT NULL sur feature)")
    void feature_obligatoire_est_refusee() {
        VueSauvegardee sansFeature = new VueSauvegardee(null, null, "Vue", DESCRIPTEUR);

        assertThatThrownBy(() -> dao.insert(sansFeature))
                .as("feature NOT NULL doit refuser une vue sans écran propriétaire")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Le descripteur est obligatoire (contrainte NOT NULL sur descriptor_json)")
    void descripteur_obligatoire_est_refuse() {
        VueSauvegardee sansDescripteur = new VueSauvegardee(null, "multisite", "Vue vide", null);

        assertThatThrownBy(() -> dao.insert(sansDescripteur))
                .as("descriptor_json NOT NULL doit refuser une vue sans état de filtres")
                .isInstanceOf(DataAccessException.class);
    }
}
