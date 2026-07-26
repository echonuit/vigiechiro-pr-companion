package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Marquage opportuniste (#2525) via la table latérale de présence `passage_opportuniste` : (dé)marquage
/// idempotent, lecture unitaire et groupée, et **cascade** à la suppression du passage.
class PassageOpportunisteDaoTest {

    private static final String SERIE = "1925492";

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
        // Chaîne de parents requise par les FK : user -> site -> point, l'enregistreur, puis les passages.
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", "u-1"));
        long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        passages = new PassageDao(source);
        idPassage = insererPassage(idPoint, 1, "2026-06-20");
        idAutrePassage = insererPassage(idPoint, 2, "2026-08-25");
        dao = new PassageOpportunisteDao(source);
    }

    private long insererPassage(long idPoint, int numero, String date) {
        return passages.insert(new Passage(
                        null,
                        numero,
                        2026,
                        date,
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE,
                        null))
                .id();
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
