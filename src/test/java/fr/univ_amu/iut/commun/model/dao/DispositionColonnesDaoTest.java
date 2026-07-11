package fr.univ_amu.iut.commun.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du DAO de la disposition des colonnes (table `column_layout`, V19, #994) : upsert par
/// `(feature, table_key)` et isolation des clés.
class DispositionColonnesDaoTest {

    private DispositionColonnesDao dao;

    @BeforeEach
    void preparer() throws IOException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(Files.createTempDirectory("dispo-colonnes")));
        new MigrationSchema(source).migrer();
        dao = new DispositionColonnesDao(source);
    }

    @Test
    @DisplayName("charger : vide tant que rien n'est enregistré")
    void charger_vide_par_defaut() {
        assertThat(dao.charger("audio", "principale")).isEmpty();
    }

    @Test
    @DisplayName("enregistrer puis charger : la disposition est relue ; un second enregistrement remplace (upsert)")
    void enregistrer_puis_upsert() {
        dao.enregistrer("audio", "principale", "{\"v\":1}");
        assertThat(dao.charger("audio", "principale")).contains("{\"v\":1}");

        dao.enregistrer("audio", "principale", "{\"v\":2}");
        assertThat(dao.charger("audio", "principale")).contains("{\"v\":2}");
    }

    @Test
    @DisplayName("Les clés (feature, table_key) sont isolées les unes des autres")
    void cles_isolees() {
        dao.enregistrer("audio", "principale", "{\"a\":1}");
        dao.enregistrer("analyse", "especes", "{\"b\":2}");

        assertThat(dao.charger("audio", "principale")).contains("{\"a\":1}");
        assertThat(dao.charger("analyse", "especes")).contains("{\"b\":2}");
        assertThat(dao.charger("audio", "especes")).isEmpty();
    }
}
