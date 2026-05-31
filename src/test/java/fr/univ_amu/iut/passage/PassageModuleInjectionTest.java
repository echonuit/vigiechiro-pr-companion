package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Filet de câblage Guice de la feature {@code passage} : vérifie que {@code PassageModule} fournit
 * bien le moteur de workflow et le {@link ServicePassage} (qui assemble {@code PassageDao} + moteur
 * + l'{@code Horloge} du socle), via la racine de composition réelle. On surcharge le workspace
 * vers un {@code @TempDir} pour ne pas toucher au workspace réel.
 */
class PassageModuleInjectionTest {

  @TempDir Path workspaceJetable;

  @AfterEach
  void nettoyerLaSurcharge() {
    System.clearProperty("vigiechiro.workspace");
  }

  @Test
  @DisplayName("La racine de composition résout le moteur et le service de la feature passage")
  void resout_moteur_et_service_passage() {
    System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

    Injector injecteur = RacineInjecteur.creer();

    assertThat(injecteur.getInstance(MoteurWorkflowPassage.class)).isNotNull();
    assertThat(injecteur.getInstance(ServicePassage.class)).isNotNull();
  }
}
