package fr.univ_amu.iut.commun.di;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.multisite.model.dao.SavedViewDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test de la racine de composition Guice : vérifie que {@link RacineInjecteur#creer()} assemble
 * bien <b>tous</b> les modules de features (sites, passage, qualification, validation, multisite,
 * importation, lot) en plus du socle, sans conflit de binding, et qu'il sait résoudre un DAO ou un
 * service représentatif de chaque feature.
 *
 * <p>Guice valide les liaisons en double dès la création de l'injecteur : ce test est donc le filet
 * de sécurité qui détecte qu'une future feature ait introduit une liaison concurrente ou oublié son
 * {@code @Provides}. On surcharge le workspace vers un {@code @TempDir} (propriété système {@code
 * vigiechiro.workspace}) pour ne pas toucher au workspace réel de l'utilisateur·ice.
 */
class RacineInjecteurTest {

  @TempDir Path workspaceJetable;

  @AfterEach
  void nettoyerLaSurcharge() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  void creer_assemble_tous_les_modules_et_resout_un_dao_par_feature() {
    System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

    Injector injecteur = RacineInjecteur.creer();

    assertThat(injecteur).isNotNull();
    assertThat(injecteur.getInstance(SiteDao.class)).isNotNull();
    assertThat(injecteur.getInstance(PointDao.class)).isNotNull();
    assertThat(injecteur.getInstance(PassageDao.class)).isNotNull();
    assertThat(injecteur.getInstance(SelectionDao.class)).isNotNull();
    assertThat(injecteur.getInstance(ObservationDao.class)).isNotNull();
    assertThat(injecteur.getInstance(SavedViewDao.class)).isNotNull();
    // Service de référence : valide le câblage inter-modules (SitesModule reçoit le PassageDao de
    // PassageModule et l'Horloge du socle).
    assertThat(injecteur.getInstance(ServiceSites.class)).isNotNull();
    // Features importation et lot : leurs services dépendent de DAO d'autres features (passage,
    // sites) et du socle. On vérifie que la racine les résout sans conflit de binding.
    assertThat(injecteur.getInstance(ServiceImport.class)).isNotNull();
    assertThat(injecteur.getInstance(ServiceLot.class)).isNotNull();
  }
}
