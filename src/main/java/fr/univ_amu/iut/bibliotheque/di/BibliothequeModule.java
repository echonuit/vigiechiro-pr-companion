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
/// **Intégration** : ce module est installé dans `RacineInjecteur` (aux côtés de `ValidationModule`
/// et `PassageModule` qui fournissent ses DAO). Depuis la vue audio unifiée (#audio), l'**entrée
/// d'accueil** des sons de référence n'est plus enregistrée ici : c'est la carte
/// `fr.univ_amu.iut.audio.view.AccueilSonsReference` (feature `audio`) qui ouvre la vue unifiée sur la
/// source `References`. Ce module ne fournit donc plus que le service (et l'ancien `BibliothequeViewModel`,
/// conservé jusqu'au démantèlement de l'ancien écran).
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
