package fr.univ_amu.iut.bibliotheque.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.bibliotheque.viewmodel.BibliothequeViewModel;
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
/// **Intégration** : ce module n'est **pas (encore) installé** dans `RacineInjecteur` (fichier gelé
/// pour cette tâche). Son câblage est validé en isolation par `BibliothequeModuleTest` (injecteur
/// local fournissant les DAO feuilles). Pour le rendre résoluble par l'injecteur applicatif,
/// ajouter `new BibliothequeModule()` à `RacineInjecteur.creer()` (et installer aussi
/// `ValidationModule` et `PassageModule` qui fournissent ses DAO — `PassageModule` et
/// `ValidationModule` y sont déjà).
public class BibliothequeModule extends AbstractModule {

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
