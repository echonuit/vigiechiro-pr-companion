package fr.univ_amu.iut.saison;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.saison.di.SaisonModule;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.sites.di.SitesModule;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Filet d'intégration Guice de la feature `saison` : le module assemble [ServiceSoldeSaison] à partir
/// des DAO de `sites` et `passage` et de l'[fr.univ_amu.iut.commun.model.Horloge] du socle, sans
/// conflit de binding. On épingle aussi l'**identité** de la feature : renommer son id casserait le
/// flag de désactivation persisté (leçon #1537).
class SaisonModuleTest {

    @TempDir
    Path workspaceJetable;

    @AfterEach
    void nettoyerLaSurcharge() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("SaisonModule résout ServiceSoldeSaison via Guice")
    void saison_module_resout_le_service() {
        System.setProperty("vigiechiro.workspace", workspaceJetable.toString());

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new SaisonModule());

        assertThat(injecteur.getInstance(ServiceSoldeSaison.class)).isNotNull();
    }

    @Test
    @DisplayName("l'identité de la feature est stable : id « saison », désactivable")
    void identite_de_la_feature_est_stable() {
        var fonctionnalite = new SaisonModule().fonctionnalite();
        assertThat(fonctionnalite.id()).isEqualTo("saison");
        assertThat(fonctionnalite.libelle()).isEqualTo("Solde de la saison");
        assertThat(fonctionnalite.categorie()).isEqualTo(Categorie.OPTIONNELLE);
    }
}
