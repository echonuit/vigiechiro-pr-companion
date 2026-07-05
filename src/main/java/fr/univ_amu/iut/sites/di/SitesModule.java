package fr.univ_amu.iut.sites.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.IndicateurAccueil;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.view.ActiviteMesSites;
import fr.univ_amu.iut.sites.view.NavigationSites;
import fr.univ_amu.iut.sites.viewmodel.IndicateurPoints;
import fr.univ_amu.iut.sites.viewmodel.IndicateurSites;
import fr.univ_amu.iut.sites.viewmodel.PointEditViewModel;
import fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel;
import fr.univ_amu.iut.sites.viewmodel.SitesViewModel;
import java.util.UUID;

/// Module Guice de la feature `sites` : fournit ses DAO à partir de la [SourceDeDonnees]
/// (binder en singleton par `CommunModule`).
///
/// On utilise des méthodes `@Provides` (et non `@Inject` sur les DAO) pour garder la couche
/// `model.dao` **indépendante du framework** d'injection : les DAO restent de simples objets
/// réutilisables (objectif réutilisation O6). C'est ce module qui sait les assembler.
///
/// Ce module assemble aussi la couche IHM de la feature : les trois ViewModels (M-Sites,
/// M-Site-detail, modale point) y sont fournis par `@Provides`. Les controllers FXML
/// ([fr.univ_amu.iut.sites.view.MesSitesController]…) et la façade
/// [fr.univ_amu.iut.sites.view.NavigationSites] ne sont **pas** déclarés ici : ils suivent le
/// patron du socle (constructeur `@Inject` instancié par la `controllerFactory` Guice du
/// `FXMLLoader`), exactement comme `MainController`.
public class SitesModule extends AbstractModule {

    /// Enregistre la carte d'accueil de la feature dans le point d'extension du socle. Le
    /// `MainController` la découvre via `Set<ActiviteAccueil>` sans que `commun` dépende de `sites`.
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ActiviteAccueil.class).addBinding().to(ActiviteMesSites.class);
        // Compteurs du tableau de bord d'accueil (sites + points d'écoute).
        Multibinder<IndicateurAccueil> indicateurs = Multibinder.newSetBinder(binder(), IndicateurAccueil.class);
        indicateurs.addBinding().to(IndicateurSites.class);
        indicateurs.addBinding().to(IndicateurPoints.class);
        // Contrat socle : permet à d'autres écrans (M-Passage) de rendre « Mes sites » / « Carré N »
        // cliquables dans leur fil d'Ariane sans dépendre du `view` de sites.
        bind(OuvrirSite.class).to(NavigationSites.class);
        // Port socle CoordonneesPoint : `sites` détient les coordonnées des points, elle fournit donc
        // l'implémentation « réelle ». Le défaut no-op est posé par PassageModule (feature consommatrice)
        // ; ici `setBinding` l'emporte dès que SitesModule est installé (app complète). Inversion de
        // dépendance qui évite le cycle passage ↔ sites (#547).
        OptionalBinder.newOptionalBinder(binder(), CoordonneesPoint.class)
                .setBinding()
                .to(CoordonneesPointSites.class);
    }

    @Provides
    @Singleton
    SiteDao fournirSiteDao(SourceDeDonnees source) {
        return new SiteDao(source);
    }

    @Provides
    @Singleton
    PointDao fournirPointDao(SourceDeDonnees source) {
        return new PointDao(source);
    }

    /// Service métier de référence. Reçoit ses DAO (dont [PassageDao], fourni par
    /// `PassageModule` pour la protection de suppression) et l'[Horloge] (socle). C'est ici,
    /// dans le module de feature, que les briques sont assemblées : le service lui-même reste
    /// sans annotation d'injection.
    @Provides
    @Singleton
    ServiceSites fournirServiceSites(SiteDao siteDao, PointDao pointDao, PassageDao passageDao, Horloge horloge) {
        return new ServiceSites(siteDao, pointDao, passageDao, horloge);
    }

    /// Identifiant de l'utilisateur courant (application mono-utilisateur, hors-ligne, C1).
    ///
    /// Bootstrap minimal : on retourne l'unique utilisateur local s'il existe, sinon on en crée un
    /// (UUID + nom par défaut). Hébergé ici faute de notion de session dans le socle ; à promouvoir
    /// dans `commun` quand d'autres features (passage, multisite…) en auront besoin.
    @Provides
    @Singleton
    @Named("idUtilisateurCourant")
    String fournirIdUtilisateurCourant(UtilisateurDao utilisateurDao) {
        return utilisateurDao.findAll().stream()
                .map(Utilisateur::localId)
                .findFirst()
                .orElseGet(() -> creerUtilisateurLocal(utilisateurDao));
    }

    // Les ViewModels de feature ne sont volontairement PAS @Singleton. Le FXMLLoader recrée le
    // controller (et ses bindings) à chaque chargement de vue/modale ; un VM frais par chargement
    // évite que des bindings/listeners de vues fermées restent accrochés (fuite de contrôles) et que
    // l'état d'une modale précédente fuite vers la suivante. Le NavigationViewModel du socle, lui,
    // reste @Singleton car il porte de l'état de chrome partagé entre toutes les features.
    @Provides
    SitesViewModel fournirSitesViewModel(
            ServiceSites service,
            PassageDao passageDao,
            Horloge horloge,
            @Named("idUtilisateurCourant") String idUtilisateur) {
        return new SitesViewModel(service, passageDao, horloge, idUtilisateur);
    }

    @Provides
    SiteDetailViewModel fournirSiteDetailViewModel(
            ServiceSites service, PointDao pointDao, PassageDao passageDao, Horloge horloge) {
        return new SiteDetailViewModel(service, pointDao, passageDao, horloge);
    }

    @Provides
    PointEditViewModel fournirPointEditViewModel(ServiceSites service) {
        return new PointEditViewModel(service);
    }

    private static String creerUtilisateurLocal(UtilisateurDao utilisateurDao) {
        Utilisateur local = new Utilisateur(UUID.randomUUID().toString(), "Utilisateur local");
        utilisateurDao.insert(local);
        return local.localId();
    }
}
