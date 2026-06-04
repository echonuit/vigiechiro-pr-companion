package fr.univ_amu.iut.commun.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests unitaires du socle [UtilisateurDao] : insertion (clé naturelle `local_id`, aucune clé
/// générée), relecture et mise à jour du nom affiché, sur une base SQLite jetable (`@TempDir`).
class UtilisateurDaoTest {

    @TempDir
    Path dossier;

    private UtilisateurDao dao;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new UtilisateurDao(source);
    }

    @Test
    @DisplayName("insert puis findById renvoie l'utilisateur ; un id inconnu renvoie vide")
    void insert_puis_find() {
        dao.insert(new Utilisateur("u-1", "Marie"));

        assertThat(dao.findById("u-1")).map(Utilisateur::displayName).contains("Marie");
        assertThat(dao.findById("inconnu")).isEmpty();
    }

    @Test
    @DisplayName("update modifie le nom affiché sans changer la clé")
    void update_nom() {
        dao.insert(new Utilisateur("u-1", "Marie"));

        dao.update(new Utilisateur("u-1", "Marie Curie"));

        assertThat(dao.findById("u-1")).map(Utilisateur::displayName).contains("Marie Curie");
        assertThat(dao.findAll()).hasSize(1);
    }
}
