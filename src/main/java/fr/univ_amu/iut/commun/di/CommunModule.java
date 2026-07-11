package fr.univ_amu.iut.commun.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.PreferenceSourceEspece;
import fr.univ_amu.iut.commun.model.SourceUniverselle;
import fr.univ_amu.iut.commun.model.SourceUniversellePreferee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.VueSauvegardeeDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.view.OuvreurDeLienSysteme;
import fr.univ_amu.iut.commun.view.OuvrirConnexion;
import fr.univ_amu.iut.commun.view.OuvrirConnexionAucun;
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
        // Ouverture de la modale de connexion depuis le menu ☰ (#741). Défaut inerte : la feature
        // `connexion` (ConnexionModule) fournit l'implémentation réelle via setBinding en app complète.
        OptionalBinder.newOptionalBinder(binder(), OuvrirConnexion.class)
                .setDefault()
                .to(OuvrirConnexionAucun.class);
        // Préférence « source des fiches espèces » (#849) : singleton pour que le menu ☰ (qui la modifie)
        // et le constructeur de liens (qui la lit) partagent le même service persistant.
        bind(PreferenceSourceEspece.class).in(Singleton.class);
    }

    /// Source universelle des fiches espèces (repli hors PNA), pilotée par la préférence utilisateur
    /// (#849) : GBIF par défaut, Wikipédia FR au choix, relue à chaque lien (effet immédiat). Alimente le
    /// `ConstructeurLienEspece` par injection.
    @Provides
    @Singleton
    SourceUniverselle fournirSourceUniverselle(PreferenceSourceEspece preference) {
        return new SourceUniversellePreferee(preference::prefereWikipedia);
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

    /// Dépôt des **vues mémorisées** (#623), partagé par toutes les vues tabulaires (multisite, puis
    /// audio / analyse) : le composant d'onglets `commun.view.GestionnaireVues` y passe (via l'interface
    /// [DepotVues]) plutôt que par le DAO concret. Bindé au socle pour éviter une liaison par feature.
    @Provides
    @Singleton
    DepotVues fournirDepotVues(SourceDeDonnees source) {
        return new VueSauvegardeeDao(source);
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
