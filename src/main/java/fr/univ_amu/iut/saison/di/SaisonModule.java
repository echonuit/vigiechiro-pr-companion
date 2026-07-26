package fr.univ_amu.iut.saison.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;

/// Module Guice de la feature `saison` (#2356) : assemble [ServiceSoldeSaison] à partir des DAO des
/// features `sites` et `passage` et de l'[Horloge] du socle. On utilise une méthode `@Provides` (et non
/// `@Inject` sur le service) pour garder la couche `model` **indépendante du framework** d'injection,
/// comme `MultisiteModule` : c'est ce module qui sait assembler.
///
/// `OPTIONNELLE` (désactivable, active par défaut) : l'écran « Ma saison » et la commande
/// `solde-saison` sont un **confort de pilotage**, pas un socle dont dépend une autre feature. Coupée,
/// la feature retire simplement sa carte et sa commande, sans casser l'injecteur.
public class SaisonModule extends ModuleDeFeature {

    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("saison", "Solde de la saison", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        // Aucune contribution au socle pour l'instant : la carte d'accueil « Ma saison » arrivera avec
        // l'écran (lot 2, PR 3). Ce module ne fait pour l'heure que fournir le service ci-dessous.
    }

    @Provides
    @Singleton
    ServiceSoldeSaison fournirServiceSoldeSaison(
            SiteDao siteDao, PointDao pointDao, PassageDao passageDao, Horloge horloge) {
        return new ServiceSoldeSaison(siteDao, pointDao, passageDao, horloge);
    }
}
