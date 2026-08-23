package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La lecture par lot **au-delà de ce que SQLite accepte de lier**, sur une vraie base.
///
/// ⚠️ `LotsDeParametresTest` affirme qu'aucune tranche ne dépasse 999 paramètres. C'est une **hypothèse
/// sur SQLite encodée dans une assertion** : elle resterait verte si la vraie borne était plus basse, ou
/// si le découpage était correct mais la requête mal recomposée. Ce test-ci ne suppose rien - il sème
/// plus de mille sites et vérifie que **tout revient**.
///
/// Le mode de panne redouté n'est pas une exception bruyante : c'est une lecture qui rend des lignes
/// **en moins**, silencieusement, et un écran qui affiche un inventaire tronqué.
class LectureParLotSurVraiSqliteTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#4251 : mille deux cents sites lus par lot rendent leurs mille deux cents points")
    void au_dela_de_la_borne_sqlite_tout_revient() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur("u", "S"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);

        List<Long> idsSites = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            Site site = siteDao.insert(new Site(
                    null, String.format("%06d", 100000 + i), "C" + i, Protocole.STANDARD, null, "2026-01-01", "u"));
            pointDao.insert(new PointDEcoute(null, "A1", 43.5, 5.4, null, site.id()));
            idsSites.add(site.id());
        }

        Map<Long, List<PointDEcoute>> parSite = pointDao.findParSites(idsSites);

        assertThat(parSite)
                .as("chaque site semé doit rendre son point : une lecture tronquée serait silencieuse")
                .hasSize(1200);
        assertThat(parSite.values().stream().flatMap(List::stream).toList()).hasSize(1200);
    }
}
