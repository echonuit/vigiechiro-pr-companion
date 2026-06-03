package fr.univ_amu.iut.multisite.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.dao.SavedViewDao;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;

/// Module Guice de la feature `multisite` : fournit ses DAO et son service à partir de la
/// [SourceDeDonnees] (binder en singleton par `CommunModule`).
///
/// On utilise des méthodes `@Provides` (et non `@Inject` sur les DAO/service) pour
/// garder les couches `model.dao` et `model` **indépendantes du framework**
/// d'injection : DAO et service restent de simples objets réutilisables (objectif réutilisation
/// O6). C'est ce module qui sait les assembler.
public class MultisiteModule extends AbstractModule {

    @Provides
    @Singleton
    SavedViewDao fournirSavedViewDao(SourceDeDonnees source) {
        return new SavedViewDao(source);
    }

    /// Vue agrégée multi-sites (parcours P5). Reçoit son propre [SavedViewDao] ainsi que les DAO
    /// en lecture des features `sites` ([SiteDao], [PointDao]) et `passage`
    /// ([PassageDao]), fournis par leurs modules respectifs, plus l'[Horloge] du socle.
    /// L'assemblage inter-modules est résolu par `RacineInjecteur`.
    @Provides
    @Singleton
    ServiceMultisite fournirServiceMultisite(
            SavedViewDao savedViewDao, SiteDao siteDao, PointDao pointDao, PassageDao passageDao, Horloge horloge) {
        return new ServiceMultisite(savedViewDao, siteDao, pointDao, passageDao, horloge);
    }

    // Le ViewModel n'est volontairement PAS @Singleton (cf. SitesModule) : un VM frais par
    // chargement de vue évite que des listeners de vues fermées restent accrochés. Reçoit l'identité
    // de l'utilisateur courant publiée par SitesModule.
    @Provides
    MultisiteViewModel fournirMultisiteViewModel(
            ServiceMultisite service, @Named("idUtilisateurCourant") String idUtilisateur) {
        return new MultisiteViewModel(service, idUtilisateur);
    }
}
