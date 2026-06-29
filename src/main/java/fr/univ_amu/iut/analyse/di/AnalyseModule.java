package fr.univ_amu.iut.analyse.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.view.ActiviteAnalyse;
import fr.univ_amu.iut.analyse.view.NavigationAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;

/// Module Guice de la feature `analyse` (prisme « Espèces & biodiversité »). Enregistre sa carte
/// d'accueil et fournit son service (assemblé sur la projection de [ObservationDao], feature
/// `validation`, fournie par `ValidationModule`) et son ViewModel. Comme les autres modules de feature,
/// l'assemblage inter-modules est résolu par `RacineInjecteur`.
public class AnalyseModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ActiviteAccueil.class).addBinding().to(ActiviteAnalyse.class);
        // Contrat socle de retour vers l'analyse (segment cliquable du fil d'Ariane de la vue audio
        // ouverte sur une source ParEspece).
        bind(OuvrirAnalyse.class).to(NavigationAnalyse.class);
    }

    @Provides
    @Singleton
    ServiceAnalyse fournirServiceAnalyse(ObservationDao observationDao) {
        return new ServiceAnalyse(observationDao);
    }

    // ViewModel non-singleton (cf. multisite) : un VM frais par chargement d'écran.
    @Provides
    AnalyseViewModel fournirAnalyseViewModel(
            ServiceAnalyse service, @Named("idUtilisateurCourant") String idUtilisateur) {
        return new AnalyseViewModel(service, idUtilisateur);
    }
}
