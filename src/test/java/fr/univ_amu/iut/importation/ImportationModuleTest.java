package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.importation.di.ImportationModule;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet d'intégration Guice de la feature `importation` : on assemble un injecteur dédié
/// (socle + `PassageModule` + `SitesModule` + `ImportationModule`) et on vérifie que
/// [ServiceImport] et ses moteurs sont résolubles, et que la feature coexiste sans conflit de
/// binding avec `passage` (dont elle écrit les tables). `SitesModule` est requis car le ViewModel
/// d'import dépend de `sites`.
class ImportationModuleTest {

  @TempDir Path workspaceJetable;

  @AfterEach
  void nettoyerLaSurcharge() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  @DisplayName("L'injecteur résout ServiceImport et tous les moteurs de l'import")
  void resout_le_service_et_ses_moteurs() {
    System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

    // SitesModule est inclus car ImportationModule fournit désormais ImportationViewModel, qui
    // dépend de ServiceSites et de l'utilisateur courant (bindings fournis par SitesModule).
    Injector injecteur =
        Guice.createInjector(
            new CommunModule(),
            new PersistenceModule(),
            new PassageModule(),
            new SitesModule(),
            new ImportationModule());

    assertThat(injecteur.getInstance(InspecteurDossier.class)).isNotNull();
    assertThat(injecteur.getInstance(CopieProtegee.class)).isNotNull();
    assertThat(injecteur.getInstance(Renommeur.class)).isNotNull();
    assertThat(injecteur.getInstance(TransformationAudio.class)).isNotNull();
    assertThat(injecteur.getInstance(AgregatImportDao.class)).isNotNull();
    assertThat(injecteur.getInstance(ServiceImport.class)).isNotNull();
  }
}
