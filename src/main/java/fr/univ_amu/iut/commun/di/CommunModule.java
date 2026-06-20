package fr.univ_amu.iut.commun.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvreurDeLienSysteme;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Path;

/// Module Guice du socle : fournit le [Workspace], la [SourceDeDonnees] et le socle IHM
/// (singletons).
///
/// Le workspace est par défaut `<Documents>/VigieChiro-Companion` (R21). Pour les tests
/// d'intégration ou une démo jetable, on peut le surcharger via la propriété système
/// `vigiechiro.workspace` (ex. `-Dvigiechiro.workspace=/tmp/vc`). Les tests unitaires des DAO,
/// eux, instancient directement `SourceDeDonnees` sur un `@TempDir` sans passer par
/// Guice.
public class CommunModule extends AbstractModule {

    @Override
    protected void configure() {
        // Socle IHM transverse : état de navigation observable + service de swap de la zone
        // centrale. Singletons pour que le chrome et toutes les features partagent la même
        // instance. Pas de @Provides : pas de logique de construction (constructeurs @Inject).
        bind(NavigationViewModel.class).in(Singleton.class);
        bind(Navigateur.class).in(Singleton.class);
        // Ouverture de liens externes (ex. coordonnées GPS -> OpenStreetMap). Singleton :
        // `App` y branche le HostServices une fois au démarrage (cf. App.start).
        bind(OuvreurDeLien.class).to(OuvreurDeLienSysteme.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    Workspace fournirWorkspace() {
        String surcharge = System.getProperty("vigiechiro.workspace");
        return surcharge != null ? new Workspace(Path.of(surcharge)) : Workspace.parDefaut();
    }

    @Provides
    @Singleton
    SourceDeDonnees fournirSourceDeDonnees(Workspace workspace) {
        return new SourceDeDonnees(workspace);
    }

    /// Horloge applicative : l'horloge système en production. Transverse (les règles de dates R3/R4
    /// et
    /// les horodatages des features la réclament), elle est donc bindée au niveau du socle. Les tests
    /// n'utilisent pas ce binding : ils injectent directement une
    /// [fr.univ_amu.iut.commun.model.HorlogeFigee].
    @Provides
    @Singleton
    Horloge fournirHorloge() {
        return Horloge.systeme();
    }
}
