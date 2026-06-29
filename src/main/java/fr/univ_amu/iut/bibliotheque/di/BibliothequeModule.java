package fr.univ_amu.iut.bibliotheque.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;

/// Module Guice de la feature `bibliotheque`, **réduite à son modèle** depuis le démantèlement de
/// l'ancien écran : il assemble [ServiceBibliotheque] (corpus de sons de référence, export) à partir des
/// DAO publiés par les autres features ([ObservationDao] de `validation`, [SequenceDao] de `passage`).
///
/// Le service est désormais consommé par la **vue audio unifiée** (#audio) : l'export de la bibliothèque
/// s'y fait sur la source `References`, et l'entrée d'accueil est la carte
/// `fr.univ_amu.iut.audio.view.AccueilSonsReference` (feature `audio`). Ce module ne fournit donc plus de
/// `view`/`viewmodel` ni de carte d'accueil — seulement le service.
///
/// Même patron que `SitesModule` / `LotModule` : une méthode `@Provides @Singleton` câble un service resté
/// **sans annotation d'injection** (objet Java ordinaire, instanciable à la main dans les tests). Les DAO
/// inter-feature sont reçus en lecture seule (sens autorisé `bibliotheque → validation` et `bibliotheque →
/// passage`, graphe acyclique). Installé dans `RacineInjecteur`.
public class BibliothequeModule extends AbstractModule {

    @Provides
    @Singleton
    ServiceBibliotheque fournirServiceBibliotheque(ObservationDao observationDao, SequenceDao sequenceDao) {
        return new ServiceBibliotheque(observationDao, sequenceDao);
    }
}
