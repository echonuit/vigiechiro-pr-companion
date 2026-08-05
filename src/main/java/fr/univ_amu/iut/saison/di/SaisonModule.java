package fr.univ_amu.iut.saison.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.view.ActiviteMaSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.util.Optional;

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
        // Carte d'accueil « Ma saison » (prisme Collecte & passages) : le MainController la découvre via
        // Set<ActiviteAccueil> sans que `commun` dépende de `saison`.
        activite(ActiviteMaSaison.class);
    }

    @Provides
    @Singleton
    ServiceSoldeSaison fournirServiceSoldeSaison(
            SiteDao siteDao,
            PointDao pointDao,
            PassageDao passageDao,
            PointCommuneDao communeDao,
            PassageOpportunisteDao opportunistes,
            SiteTiersDao carresDeTiers,
            Optional<ServiceCampagne> campagnes,
            Horloge horloge) {
        return new ServiceSoldeSaison(
                siteDao, pointDao, passageDao, communeDao, opportunistes, carresDeTiers, campagnes, horloge);
    }

    // ViewModel volontairement NON @Singleton (comme MultisiteViewModel) : un VM frais par chargement
    // de vue évite que des listeners de vues fermées restent accrochés. Reçoit l'identité de
    // l'utilisateur courant publiée par SitesModule.
    @Provides
    SaisonViewModel fournirSaisonViewModel(
            ServiceSoldeSaison service, @Named("idUtilisateurCourant") String idUtilisateur) {
        return new SaisonViewModel(service, idUtilisateur);
    }
}
