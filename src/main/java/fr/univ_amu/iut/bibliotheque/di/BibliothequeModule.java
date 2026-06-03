package fr.univ_amu.iut.bibliotheque.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.bibliotheque.view.ActiviteBibliotheque;
import fr.univ_amu.iut.bibliotheque.viewmodel.BibliothequeViewModel;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;

/// Module Guice de la feature `bibliotheque` : assemble [ServiceBibliotheque] à partir
/// des DAO publiés par les autres features ([ObservationDao] de `validation`, [SequenceDao] de
/// `passage`).
///
/// Même patron que `SitesModule` / `LotModule` : une méthode `@Provides @Singleton` câble un
/// service resté **sans annotation d'injection** (objet Java ordinaire, instanciable à la main dans
/// les tests). Les DAO inter-feature sont reçus en lecture seule (sens autorisé `bibliotheque →
/// validation` et `bibliotheque → passage`, graphe acyclique).
///
/// **Intégration** : ce module est installé dans `RacineInjecteur` (aux côtés de `ValidationModule`
/// et `PassageModule` qui fournissent ses DAO). Il enregistre la carte d'accueil
/// [ActiviteBibliotheque] dans le `Multibinder<ActiviteAccueil>` du socle : le `MainController` la
/// découvre via `Set<ActiviteAccueil>` sans que `commun` dépende de `bibliotheque` (graphe de slices
/// acyclique, cf. `ArchitectureTest`).
public class BibliothequeModule extends AbstractModule {

    /// Enregistre la carte d'accueil de la feature dans le point d'extension du socle.
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ActiviteAccueil.class).addBinding().to(ActiviteBibliotheque.class);
    }

    @Provides
    @Singleton
    ServiceBibliotheque fournirServiceBibliotheque(ObservationDao observationDao, SequenceDao sequenceDao) {
        return new ServiceBibliotheque(observationDao, sequenceDao);
    }

    // Le ViewModel n'est volontairement PAS @Singleton : le FXMLLoader recrée le controller (et ses
    // bindings) à chaque chargement de l'écran ; un VM frais évite que des listeners de vues fermées
    // restent accrochés. Même choix que les VM de `sites` (cf. SitesModule).
    @Provides
    BibliothequeViewModel fournirBibliothequeViewModel(ServiceBibliotheque service) {
        return new BibliothequeViewModel(service);
    }
}
