package fr.univ_amu.iut.multisite.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.view.ActionsDeLot;
import fr.univ_amu.iut.multisite.view.ActiviteMultisite;
import fr.univ_amu.iut.multisite.view.NavigationMultisite;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceCommunes;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Optional;

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
        // Actions de lot (#2357) : déclarées VIDES ici, le consommateur. Leur valeur est posée par la
        // feature qui possède le geste - `lot` pour les deux premières, `import-vigiechiro` pour la
        // troisième - et ces features sont DÉSACTIVABLES. Sans ces OptionalBinder, couper `lot` faisait
        // échouer l'injecteur entier.
        OptionalBinder.newOptionalBinder(binder(), Key.get(ActionGroupee.class, Names.named("action.preparerDepot")));
        OptionalBinder.newOptionalBinder(binder(), Key.get(ActionGroupee.class, Names.named("action.televerser")));
        OptionalBinder.newOptionalBinder(
                binder(), Key.get(ActionGroupee.class, Names.named("action.importerResultats")));
        OptionalBinder.newOptionalBinder(
                binder(), Key.get(ActionGroupee.class, Names.named("action.declencherCalcul")));
        activite(ActiviteMultisite.class);
        // Contrat socle « voir sur la carte » : les autres features renvoient vers la carte multi-sites.
        bind(OuvrirMultisite.class).to(NavigationMultisite.class);
    }

    /// Vue agrégée multi-sites (parcours P5). Reçoit les DAO en lecture des features `sites`
    /// ([SiteDao], [PointDao]) et `passage` ([PassageDao]), fournis par leurs modules respectifs, plus
    /// l'[Horloge] du socle. L'assemblage inter-modules est résolu par `RacineInjecteur`. Les vues
    /// mémorisées ne passent plus par ce service (#537 étape 6b) : voir le [fr.univ_amu.iut.commun.model.DepotVues]
    /// fourni par `CommunModule`.
    /// Les actions de lot (#2357), assemblées ici : chacune vient de la feature qui possède son geste,
    /// et l'écran les reçoit en un seul porteur plutôt qu'une par une.
    @Provides
    @Singleton
    ActionsDeLot fournirActionsDeLot(
            @Named("action.preparerDepot") Optional<ActionGroupee> preparerDepot,
            @Named("action.televerser") Optional<ActionGroupee> televerser,
            @Named("action.importerResultats") Optional<ActionGroupee> importerResultats,
            @Named("action.declencherCalcul") Optional<ActionGroupee> declencherCalcul) {
        return new ActionsDeLot(preparerDepot, televerser, importerResultats, declencherCalcul);
    }

    @Provides
    @Singleton
    ServiceMultisite fournirServiceMultisite(
            SiteDao siteDao,
            PointDao pointDao,
            PassageDao passageDao,
            ReleveTraitementDao relevesDao,
            ResultatsIdentificationDao resultatsDao,
            PointCommuneDao communesDao,
            Optional<ServiceCampagne> campagnes,
            Horloge horloge) {
        return new ServiceMultisite(
                siteDao, pointDao, passageDao, relevesDao, resultatsDao, communesDao, campagnes, horloge);
    }

    // Le ViewModel n'est volontairement PAS @Singleton (cf. SitesModule) : un VM frais par
    // chargement de vue évite que des listeners de vues fermées restent accrochés. Reçoit l'identité
    // de l'utilisateur courant publiée par SitesModule.
    @Provides
    MultisiteViewModel fournirMultisiteViewModel(
            ServiceMultisite service,
            ServiceSites serviceSites,
            ServiceCommunes communes,
            Optional<SuiviTraitement> suivi,
            @Named("idUtilisateurCourant") String idUtilisateur) {
        return new MultisiteViewModel(service, serviceSites, communes, suivi, idUtilisateur);
    }
}
