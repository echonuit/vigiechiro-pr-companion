package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet d'intégration Guice de la feature `validation` : vérifie que la racine de composition
/// sait résoudre le [ServiceValidation] et ses moteurs CSV. Valide le câblage inter-modules (le
/// service reçoit `SessionDao` / `SequenceDao` de `passage` et l'`Horloge` du socle). On
/// surcharge le workspace vers un `@TempDir` pour ne pas toucher au workspace réel.
class ValidationInjectionTest {

  @TempDir Path workspaceJetable;

  @AfterEach
  void nettoyerLaSurcharge() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  @DisplayName("La racine résout ServiceValidation, ParserCsvTadarida et ExportVuCsv")
  void resout_le_service_et_ses_moteurs() {
    System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

    Injector injecteur = RacineInjecteur.creer();

    assertThat(injecteur.getInstance(ParserCsvTadarida.class)).isNotNull();
    assertThat(injecteur.getInstance(ExportVuCsv.class)).isNotNull();
    assertThat(injecteur.getInstance(ServiceValidation.class)).isNotNull();
  }
}
