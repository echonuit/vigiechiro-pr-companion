package fr.univ_amu.iut.multisite.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.view.ActiviteMultisite;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;

/// Module Guice de la feature `multisite` : fournit ses DAO et son service à partir de la
/// [SourceDeDonnees] (binder en singleton par `CommunModule`).
///
/// On utilise des méthodes `@Provides` (et non `@Inject` sur les DAO/service) pour
/// garder les couches `model.dao` et `model` **indépendantes du framework**
/// d'injection : DAO et service restent de simples objets réutilisables (objectif réutilisation
/// O6). C'est ce module qui sait les assembler.
public class MultisiteModule extends ModuleDeFeature {

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("multisite", "Vue multi-sites", Categorie.COEUR);
    }

    /// Enregistre la carte d'accueil de la feature dans le point d'extension du socle (le
    /// `MainController` la découvre via `Set<ActiviteAccueil>` sans que `commun` dépende de
    /// `multisite`).
    @Override
    protected void configure() {
        activite(ActiviteMultisite.class);
        // Contrat socle « voir sur la carte » : les autres features renvoient vers la carte multi-sites.
        bind(OuvrirMultisite.class).to(NavigationMultisite.class);
    }

    /// Vue agrégée multi-sites (parcours P5). Reçoit les DAO en lecture des features `sites`
    /// ([SiteDao], [PointDao]) et `passage` ([PassageDao]), fournis par leurs modules respectifs, plus
    /// l'[Horloge] du socle. L'assemblage inter-modules est résolu par `RacineInjecteur`. Les vues
    /// mémorisées ne passent plus par ce service (#537 étape 6b) : voir le [fr.univ_amu.iut.commun.model.DepotVues]
    /// fourni par `CommunModule`.
    @Provides
    @Singleton
    ServiceMultisite fournirServiceMultisite(
            SiteDao siteDao,
            PointDao pointDao,
            PassageDao passageDao,
            ReleveTraitementDao relevesDao,
            ResultatsIdentificationDao resultatsDao,
            Horloge horloge) {
        return new ServiceMultisite(siteDao, pointDao, passageDao, relevesDao, resultatsDao, horloge);
    }

    // Le ViewModel n'est volontairement PAS @Singleton (cf. SitesModule) : un VM frais par
    // chargement de vue évite que des listeners de vues fermées restent accrochés. Reçoit l'identité
    // de l'utilisateur courant publiée par SitesModule.
    @Provides
    MultisiteViewModel fournirMultisiteViewModel(
            ServiceMultisite service, ServiceSites serviceSites, @Named("idUtilisateurCourant") String idUtilisateur) {
        return new MultisiteViewModel(service, serviceSites, idUtilisateur);
    }
}
