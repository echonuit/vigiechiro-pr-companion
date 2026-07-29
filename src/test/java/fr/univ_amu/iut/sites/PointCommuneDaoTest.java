package fr.univ_amu.iut.sites;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Commune d'un point (#2791) via la table latérale `point_commune` : upsert idempotent, lecture
/// unitaire et groupée, effacement, et **cascade** à la suppression du point.
class PointCommuneDaoTest {

    private static final String ID_USER = "u-1";
    private static final Commune AIX = new Commune("Aix-en-Provence", "13001");
    private static final Commune VENELLES = new Commune("Venelles", "13113");

    @TempDir
    Path dossier;

    private PointDao points;
    private PointCommuneDao dao;
    private long idPoint;
    private long idAutrePoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        long idSite = new SiteDao(source)
                .insert(new Site(null, "130711", "Site test", Protocole.STANDARD, null, "2026-01-01", ID_USER))
                .id();
        points = new PointDao(source);
        idPoint = inserer(idSite, "A1");
        idAutrePoint = inserer(idSite, "B2");
        dao = new PointCommuneDao(source);
    }

    private long inserer(long idSite, String code) {
        return points.insert(new PointDEcoute(null, code, 43.5297, 5.4474, null, idSite))
                .id();
    }

    @Test
    @DisplayName("Par défaut, la commune d'un point est non résolue")
    void absente_par_defaut() {
        assertThat(dao.pour(idPoint)).isEmpty();
        assertThat(dao.idsResolus()).isEmpty();
    }

    @Test
    @DisplayName("definir pose la commune ; redéfinir la remplace (upsert)")
    void definir_puis_remplacer() {
        dao.definir(idPoint, AIX);
        assertThat(dao.pour(idPoint)).contains(AIX);

        dao.definir(idPoint, VENELLES);
        assertThat(dao.pour(idPoint)).as("le GPS a bougé : la commune suit").contains(VENELLES);
    }

    @Test
    @DisplayName("effacer rend la commune non résolue (idempotent)")
    void effacer() {
        dao.definir(idPoint, AIX);

        dao.effacer(idPoint);
        dao.effacer(idPoint);

        assertThat(dao.pour(idPoint)).isEmpty();
    }

    @Test
    @DisplayName("idsResolus ne remonte que les points dont la commune est posée")
    void ids_resolus() {
        dao.definir(idPoint, AIX);

        assertThat(dao.idsResolus()).containsExactly(idPoint).doesNotContain(idAutrePoint);
    }

    @Test
    @DisplayName("Supprimer le point retire sa commune en cascade")
    void cascade_suppression_point() {
        dao.definir(idPoint, AIX);

        points.delete(idPoint);

        assertThat(dao.pour(idPoint)).as("ON DELETE CASCADE").isEmpty();
    }
}
