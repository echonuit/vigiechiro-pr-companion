package fr.univ_amu.iut.analyse.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.analyse.view.ActiviteAnalyse;
import fr.univ_amu.iut.analyse.view.NavigationAnalyse;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.analyse.viewmodel.AnalyseViewModel;
import fr.univ_amu.iut.analyse.viewmodel.SyntheseViewModel;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.NuitsOpportunistes;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;

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
    ServiceAnalyse fournirServiceAnalyse(ProjectionsAnalyseDao observationDao, NuitsOpportunistes nuitsOpportunistes) {
        return new ServiceAnalyse(observationDao, nuitsOpportunistes);
    }

    // ViewModel non-singleton (cf. multisite) : un VM frais par chargement d'écran.
    @Provides
    AnalyseViewModel fournirAnalyseViewModel(
            ServiceAnalyse service, @Named("idUtilisateurCourant") String idUtilisateur) {
        return new AnalyseViewModel(service, idUtilisateur);
    }

    // Machinerie de l'écran « Activité de la nuit » (#2352). Fournie ici, avec le reste de la feature
    // `analyse`, et non par ActiviteModule : ce dernier ne conditionne que l'ACCÈS (contrat OuvrirActivite,
    // qui ne conditionne que l'accès). L'écran fait partie de la feature analyse, et son FXML doit
    // rester chargeable (ChargementFxmlTest) même l'entrée coupée. Non-singleton (VM frais par écran).
    @Provides
    ActiviteViewModel fournirActiviteViewModel(ServiceActivite service) {
        return new ActiviteViewModel(service);
    }

    // Machinerie de l'écran « Synthèse de la nuit » (#2351). Fournie ici, avec le reste de la feature
    // `analyse`, et non par SyntheseModule : ce dernier ne conditionne que l'ACCÈS, et le FXML doit
    // rester chargeable même l'entrée coupée (ChargementFxmlTest). Non-singleton : VM frais par écran.
    @Provides
    SyntheseViewModel fournirSyntheseViewModel(ServiceSynthese service) {
        return new SyntheseViewModel(service);
    }

    /// Singleton : le référentiel d'activité est chargé **une fois**, à la construction. C'est une
    /// donnée de référence embarquée de ~3900 lignes, elle ne change pas en cours de session.
    @Provides
    @Singleton
    ServiceSynthese fournirServiceSynthese(ProjectionsAudioDao projections) {
        return new ServiceSynthese(projections);
    }

    @Provides
    @Singleton
    ServiceActivite fournirServiceActivite(
            ProjectionsAudioDao projections,
            PlageNuitPassage plageNuitPassage,
            FenetreObserveeNuit fenetreObservee,
            NuitsOpportunistes nuitsOpportunistes) {
        return new ServiceActivite(projections, plageNuitPassage, fenetreObservee, nuitsOpportunistes);
    }
}
