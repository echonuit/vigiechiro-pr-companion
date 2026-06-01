package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.qualification.di.QualificationModule;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import fr.univ_amu.iut.sites.di.SitesModule;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet d'intégration Guice de la feature `qualification` : on assemble un injecteur dédié
/// (socle + `PassageModule` + `SitesModule` + `QualificationModule`) et on vérifie que le service
/// et les **deux** ViewModel (noyau verdict + sélection d'écoute) sont résolubles, et qu'ils sont
/// **non-singletons** (un VM frais par chargement FXML, comme `importation`/`sites`).
class QualificationModuleTest {

  @TempDir Path workspaceJetable;

  @AfterEach
  void nettoyerLaSurcharge() {
    System.clearProperty("vigiechiro.workspace");
  }

  private Injector injecteur() {
    System.setProperty("vigiechiro.workspace", workspaceJetable.toString());
    return Guice.createInjector(
        new CommunModule(),
        new PersistenceModule(),
        new PassageModule(),
        new SitesModule(),
        new QualificationModule());
  }

  @Test
  @DisplayName("L'injecteur résout le service et les deux ViewModel de la qualification")
  void resout_le_service_et_les_view_models() {
    Injector injecteur = injecteur();

    assertThat(injecteur.getInstance(ServiceQualification.class)).isNotNull();
    assertThat(injecteur.getInstance(QualificationViewModel.class)).isNotNull();
    assertThat(injecteur.getInstance(SelectionEcouteViewModel.class)).isNotNull();
  }

  @Test
  @DisplayName("Les ViewModel sont non-singletons (un VM frais par chargement FXML)")
  void view_models_non_singletons() {
    Injector injecteur = injecteur();

    assertThat(injecteur.getInstance(QualificationViewModel.class))
        .isNotSameAs(injecteur.getInstance(QualificationViewModel.class));
    assertThat(injecteur.getInstance(SelectionEcouteViewModel.class))
        .isNotSameAs(injecteur.getInstance(SelectionEcouteViewModel.class));
  }
}
