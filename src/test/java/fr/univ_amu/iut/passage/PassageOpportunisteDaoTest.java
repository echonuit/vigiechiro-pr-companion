package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Marquage opportuniste (#2525) via la table latérale de présence `passage_opportuniste` : (dé)marquage
/// idempotent, lecture unitaire et groupée, et **cascade** à la suppression du passage. Les passages sont
/// semés par [JeuDeDonneesPassage] (deux nuits sur le même point, trouver-ou-créer).
class PassageOpportunisteDaoTest {

    @TempDir
    Path dossier;

    private PassageDao passages;
    private PassageOpportunisteDao dao;
    private long idPassage;
    private long idAutrePassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        idPassage = JeuDeDonneesPassage.dans(source)
                .nuit(1, 2026, "2026-06-20")
                .semer()
                .idPassage();
        idAutrePassage = JeuDeDonneesPassage.dans(source)
                .nuit(2, 2026, "2026-08-25")
                .semer()
                .idPassage();
        passages = new PassageDao(source);
        dao = new PassageOpportunisteDao(source);
    }

    @Test
    @DisplayName("Par défaut, un passage n'est pas opportuniste")
    void absent_par_defaut() {
        assertThat(dao.estOpportuniste(idPassage)).isFalse();
    }

    @Test
    @DisplayName("marquer rend le passage opportuniste ; demarquer l'annule")
    void marquer_puis_demarquer() {
        dao.marquer(idPassage);
        assertThat(dao.estOpportuniste(idPassage)).isTrue();

        dao.demarquer(idPassage);
        assertThat(dao.estOpportuniste(idPassage)).isFalse();
    }

    @Test
    @DisplayName("marquer est idempotent (ON CONFLICT DO NOTHING)")
    void marquer_idempotent() {
        dao.marquer(idPassage);
        dao.marquer(idPassage);

        assertThat(dao.estOpportuniste(idPassage)).isTrue();
        assertThat(dao.tousLesIds()).containsExactly(idPassage);
    }

    @Test
    @DisplayName("definir (dé)marque selon le booléen")
    void definir_selon_booleen() {
        dao.definir(idPassage, true);
        assertThat(dao.estOpportuniste(idPassage)).isTrue();

        dao.definir(idPassage, false);
        assertThat(dao.estOpportuniste(idPassage)).isFalse();
    }

    @Test
    @DisplayName("tousLesIds ne remonte que les passages marqués")
    void tous_les_ids() {
        dao.marquer(idPassage);

        assertThat(dao.tousLesIds()).containsExactly(idPassage).doesNotContain(idAutrePassage);
    }

    @Test
    @DisplayName("Supprimer le passage retire son marquage en cascade")
    void cascade_suppression_passage() {
        dao.marquer(idPassage);

        passages.delete(idPassage);

        assertThat(dao.estOpportuniste(idPassage)).as("ON DELETE CASCADE").isFalse();
    }
}
