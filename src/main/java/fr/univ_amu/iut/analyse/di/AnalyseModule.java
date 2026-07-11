package fr.univ_amu.iut.analyse.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.view.ActiviteAnalyse;
import fr.univ_amu.iut.analyse.view.NavigationAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;

/// Module Guice de la feature `analyse` (prisme « Espèces & biodiversité »). Enregistre sa carte
/// d'accueil et fournit son service (assemblé sur la projection de [ObservationDao], feature
/// `validation`, fournie par `ValidationModule`) et son ViewModel. Comme les autres modules de feature,
/// l'assemblage inter-modules est résolu par `RacineInjecteur`.
public class AnalyseModule extends ModuleDeFeature {

    /// Identité de la feature. `COEUR` pour l'instant (feuille couplée au runtime via son contrat
    /// `Ouvrir…` : la désactiver casserait l'écran consommateur) ; passera `OPTIONNELLE` une fois ce
    /// contrat neutralisé (P3, #1064).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("analyse", "Analyse des observations", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        activite(ActiviteAnalyse.class);
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
