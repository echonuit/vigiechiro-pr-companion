package fr.univ_amu.iut.perf;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.perf.outils.GenerateurJeuDeDonnees;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le jeu du banc doit faire varier **la topologie**, pas seulement les passages.
///
/// Ce test existe parce que le banc a été **vert et aveugle**. Il semait **un** carré de dix points
/// pour mille passages, et annonçait 18 ms sous une cible de 200. Les écrans lançaient pourtant une
/// requête par site puis une par point : sur cette topologie-là, onze requêtes, invisibles. Un
/// coordinateur départemental - cent cinquante carrés - en payait plus de quatre cents, et aucun relevé
/// ne pouvait le dire.
///
/// Un jeu d'essai qui ne fait pas varier ce qui porte le défaut ne peut pas le voir, **et son vert se
/// lit comme une garantie**.
class JeuDuBancTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Le jeu du banc sème PLUSIEURS carrés, sinon il ne peut pas voir une lecture par site")
    void le_jeu_seme_plusieurs_carres() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();

        GenerateurJeuDeDonnees.peupler(source, 200, 200);

        long carres = new SiteDao(source).findAll().size();
        long points = new PointDao(source).findAll().size();
        assertThat(carres)
                .as("un seul carré rendrait invisible toute requête lancée par site")
                .isGreaterThan(1);
        assertThat(points)
                .as("les points portent la seconde lecture répétée : il en faut assez pour qu'elle pèse")
                .isGreaterThan(carres);
    }
}
