package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Filet d'intégration Guice de la feature {@code qualification} : vérifie que la racine de
 * composition sait résoudre le service et ses moteurs. Valide en particulier le câblage
 * inter-modules de {@link ServiceQualification} (il reçoit les DAO de {@code passage} et {@code
 * sites}, et l'{@code UniteDeTravail} du socle). On surcharge le workspace vers un {@code @TempDir}
 * pour ne pas toucher au workspace réel.
 */
class QualificationInjectionTest {

  @TempDir Path workspaceJetable;

  @AfterEach
  void nettoyerLaSurcharge() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  @DisplayName("La racine résout ServiceQualification et ses moteurs (câblage inter-modules)")
  void resout_le_service_et_ses_moteurs() {
    System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

    Injector injecteur = RacineInjecteur.creer();

    assertThat(injecteur.getInstance(GenerateurSelection.class)).isNotNull();
    assertThat(injecteur.getInstance(PreCheckNuit.class)).isNotNull();
    assertThat(injecteur.getInstance(ServiceQualification.class)).isNotNull();
  }
}
