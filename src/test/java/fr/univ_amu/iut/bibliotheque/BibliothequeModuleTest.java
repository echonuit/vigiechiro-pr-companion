package fr.univ_amu.iut.bibliotheque;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.bibliotheque.di.BibliothequeModule;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.validation.di.ValidationModule;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet d'intégration Guice de la feature `bibliotheque` : on assemble un injecteur dédié
/// (socle + `ValidationModule` + `PassageModule` + `BibliothequeModule`) et on vérifie que
/// [ServiceBibliotheque] est résoluble et que la feature coexiste sans conflit de binding.
///
/// `ValidationModule` et `PassageModule` sont requis car le service reçoit leurs DAO
/// ([ObservationDao], [SequenceDao]) ; le socle (`CommunModule`) fournit le `Navigateur` et le
/// `NavigationViewModel` dont dépend la carte d'accueil
/// [fr.univ_amu.iut.bibliotheque.view.ActiviteBibliotheque] enregistrée par le module. On surcharge
/// le workspace vers un `@TempDir` (propriété `vigiechiro.workspace`) pour ne pas toucher au
/// workspace réel.
class BibliothequeModuleTest {

    @TempDir
    Path workspaceJetable;

    @AfterEach
    void nettoyerLaSurcharge() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("BibliothequeModule assemble ServiceBibliotheque via Guice")
    void bibliotheque_module_resout_le_service() {
        System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new ValidationModule(),
                new PassageModule(),
                new BibliothequeModule());

        assertThat(injecteur.getInstance(ServiceBibliotheque.class)).isNotNull();
    }
}
