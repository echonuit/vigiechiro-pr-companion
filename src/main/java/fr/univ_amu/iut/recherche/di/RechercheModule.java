package fr.univ_amu.iut.recherche.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.RechercheGlobale;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.recherche.model.ServiceRechercheGlobale;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.validation.model.ServiceValidation;

/// Module Guice de la feature `recherche` (#144) : fournit l'implémentation de la [RechercheGlobale]
/// du socle, assemblée à partir des services des features `sites`, `multisite` et `validation` (espèces,
/// #323) et de l'identité de l'utilisateur courant (publiée par `SitesModule`). Le chrome
/// (`MainController`) consomme le contrat du socle sans connaître cette implémentation.
public class RechercheModule extends ModuleDeFeature {

    /// Identité de la feature. `OPTIONNELLE` (désactivable) : `RechercheGlobale` est un OptionalBinder ;
    /// désactivée, le chrome (MainController) masque la barre de recherche globale.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("recherche", "Recherche globale", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        // RechercheGlobale rendu OPTIONNEL (feature `recherche` désactivable, #1087) : le chrome l'injecte
        // en `Optional`. L'OptionalBinder est lié à l'implémentation construite par le @Provides qualifié
        // ci-dessous (garde ServiceRechercheGlobale, un service `model`, hors de Guice).
        OptionalBinder.newOptionalBinder(binder(), RechercheGlobale.class)
                .setBinding()
                .to(Key.get(RechercheGlobale.class, Names.named("recherche.impl")));
    }

    @Provides
    @Singleton
    @Named("recherche.impl")
    RechercheGlobale fournirRechercheGlobale(
            ServiceSites services,
            ServiceMultisite multisite,
            ServiceValidation validation,
            @Named("idUtilisateurCourant") String idUtilisateur) {
        return new ServiceRechercheGlobale(services, multisite, validation, idUtilisateur);
    }
}
