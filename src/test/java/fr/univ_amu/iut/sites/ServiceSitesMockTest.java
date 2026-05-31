package fr.univ_amu.iut.sites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Variante du test de {@link ServiceSites} <b>isolant une règle pure avec Mockito</b> : les DAO
 * sont des mocks, on ne touche aucune base. Utile quand on veut vérifier la <i>logique de
 * décision</i> d'une règle (ici R5 : unicité du carré) indépendamment de SQLite. Le test « réel
 * » @TempDir reste le mode par défaut ({@link ServiceSitesTest}) ; cette approche est un complément
 * ciblé.
 */
@ExtendWith(MockitoExtension.class)
class ServiceSitesMockTest {

  @Mock private SiteDao siteDao;
  @Mock private PointDao pointDao;
  @Mock private PassageDao passageDao;

  @Test
  @DisplayName("R5 : si un carré identique existe déjà, le service refuse sans tenter d'insérer")
  void unicite_carre_refusee_sans_insertion() {
    ServiceSites service =
        new ServiceSites(siteDao, pointDao, passageDao, new HorlogeFigee(LocalDate.of(2026, 1, 1)));
    when(siteDao.findByUtilisateur("u-1"))
        .thenReturn(
            List.of(new Site(1L, "640380", null, Protocole.STANDARD, null, "2026-01-01", "u-1")));

    assertThatThrownBy(() -> service.creerSite("640380", null, Protocole.STANDARD, null, "u-1"))
        .isInstanceOf(RegleMetierException.class);

    verify(siteDao, never()).insert(ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Carré libre : le service délègue l'insertion au DAO")
  void carre_libre_delegue_insertion() {
    ServiceSites service =
        new ServiceSites(siteDao, pointDao, passageDao, new HorlogeFigee(LocalDate.of(2026, 1, 1)));
    Site attendu = new Site(7L, "640380", null, Protocole.STANDARD, null, "2026-01-01", "u-1");
    when(siteDao.findByUtilisateur("u-1")).thenReturn(List.of());
    when(siteDao.insert(ArgumentMatchers.any())).thenReturn(attendu);

    Site cree = service.creerSite("640380", null, Protocole.STANDARD, null, "u-1");

    assertThat(cree).isEqualTo(attendu);
    verify(siteDao).insert(ArgumentMatchers.any());
  }
}
