package fr.univ_amu.iut.commun.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglage;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [ReglagesDao] sur une base SQLite jetable (@TempDir), initialisée par [MigrationSchema]
/// (la table `app_setting` provient de la migration V11). On vérifie la lecture d'une clé absente,
/// l'aller-retour écriture/lecture, et surtout que [ReglagesDao#ecrire] est un **upsert**
/// (deuxième écriture = remplacement, jamais de doublon de clé).
class ReglagesDaoTest {

    @TempDir
    Path dossier;

    private ReglagesDao dao;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new ReglagesDao(source);
    }

    @Test
    @DisplayName("lire une clé jamais écrite renvoie un Optional vide")
    void lire_cle_absente_renvoie_vide() {
        assertThat(dao.lire("import.conserver-originaux")).isEmpty();
    }

    @Test
    @DisplayName("ecrire puis lire restitue la valeur")
    void ecrire_puis_lire_restitue_la_valeur() {
        dao.ecrire("import.conserver-originaux", "false");

        assertThat(dao.lire("import.conserver-originaux")).contains("false");
    }

    @Test
    @DisplayName("ecrire deux fois la même clé remplace la valeur (upsert, pas de doublon)")
    void ecrire_est_un_upsert() {
        dao.ecrire("import.conserver-originaux", "true");
        dao.ecrire("import.conserver-originaux", "false");

        assertThat(dao.lire("import.conserver-originaux")).contains("false");
        assertThat(dao.findAll())
                .as("une seule ligne pour la clé (upsert, pas d'insertion en double)")
                .hasSize(1);
    }

    @Test
    @DisplayName("findById remonte le réglage écrit tel quel")
    void findById_remonte_le_reglage() {
        dao.ecrire("theme", "sombre");

        Reglage relu = dao.findById("theme").orElseThrow();
        assertThat(relu.cle()).isEqualTo("theme");
        assertThat(relu.valeur()).isEqualTo("sombre");
    }

    @Test
    @DisplayName("delete retire le réglage")
    void delete_retire_le_reglage() {
        dao.ecrire("theme", "sombre");
        assertThat(dao.findById("theme")).isPresent();

        dao.delete("theme");

        assertThat(dao.findById("theme")).isEmpty();
    }
}
