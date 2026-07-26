package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.passage.di.CampagneModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet d'intégration Guice de la feature `campagne` : le module (dans `passage/di`) assemble
/// [ServiceCampagne] depuis le socle, sans conflit. On épingle aussi l'identité de la feature : renommer
/// son id casserait le flag de désactivation persisté (leçon #1537).
class CampagneModuleTest {

    @TempDir
    Path workspaceJetable;

    @AfterEach
    void nettoyerLaSurcharge() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("CampagneModule résout ServiceCampagne via Guice")
    void campagne_module_resout_le_service() {
        System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

        Injector injecteur = Guice.createInjector(
                new CommunModule(), new PersistenceModule(), new PassageModule(), new CampagneModule());

        assertThat(injecteur.getInstance(ServiceCampagne.class)).isNotNull();
    }

    @Test
    @DisplayName("l'identité de la feature est stable : id « campagne », désactivable")
    void identite_de_la_feature_est_stable() {
        var fonctionnalite = new CampagneModule().fonctionnalite();
        assertThat(fonctionnalite.id()).isEqualTo("campagne");
        assertThat(fonctionnalite.libelle()).isEqualTo("Campagnes de suivi");
        assertThat(fonctionnalite.categorie()).isEqualTo(Categorie.OPTIONNELLE);
    }
}
