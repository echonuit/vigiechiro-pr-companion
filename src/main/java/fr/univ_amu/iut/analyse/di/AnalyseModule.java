package fr.univ_amu.iut.analyse.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.view.ActiviteAnalyse;
import fr.univ_amu.iut.analyse.view.NavigationAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;

/// Module Guice de la feature `analyse` (prisme « Espèces & biodiversité »). Enregistre sa carte
/// d'accueil et fournit son service (assemblé sur la projection de [ProjectionsAnalyseDao], feature
/// `validation`, fournie par `ValidationModule`) et son ViewModel. Comme les autres modules de feature,
/// l'assemblage inter-modules est résolu par `RacineInjecteur`.
public class AnalyseModule extends ModuleDeFeature {

    /// Identité de la feature. `OPTIONNELLE` (désactivable) : son contrat `OuvrirAnalyse` est neutralisé
    /// chez son consommateur (SonsValidationController l'injecte en `Optional` et masque les points d'entrée).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("analyse", "Analyse des observations", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        activite(ActiviteAnalyse.class);
        // Contrat socle de retour vers l'analyse (segment cliquable du fil d'Ariane de la vue audio
        // ouverte sur une source ParEspece).
        OptionalBinder.newOptionalBinder(binder(), OuvrirAnalyse.class)
                .setBinding()
                .to(NavigationAnalyse.class);
    }

    @Provides
    @Singleton
    ServiceAnalyse fournirServiceAnalyse(ProjectionsAnalyseDao observationDao) {
        return new ServiceAnalyse(observationDao);
    }

    // ViewModel non-singleton (cf. multisite) : un VM frais par chargement d'écran.
    @Provides
    AnalyseViewModel fournirAnalyseViewModel(
            ServiceAnalyse service, @Named("idUtilisateurCourant") String idUtilisateur) {
        return new AnalyseViewModel(service, idUtilisateur);
    }
}
