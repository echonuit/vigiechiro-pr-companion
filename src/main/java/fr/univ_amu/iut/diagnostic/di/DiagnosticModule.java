package fr.univ_amu.iut.diagnostic.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;

/// Module Guice de la feature `diagnostic` : assemble [ServiceDiagnostic] à partir des DAO
/// publiés par les features `passage` ([PassageDao], [SessionDao], [JournalDuCapteurDao],
/// [ReleveClimatiqueDao]) et `sites` ([PointDao]), plus l'[Horloge] du socle. Mêmes conventions
/// que `SitesModule`/`LotModule` : une méthode `@Provides @Singleton` câble un objet resté
/// **sans annotation d'injection**, les DAO inter-features étant reçus en lecture seule (sens
/// autorisé `diagnostic → passage` et `diagnostic → sites`, graphe acyclique).
///
/// **Intégration** : ce module n'est pas (encore) installé dans `RacineInjecteur` (fichier gelé
/// pour cette tâche). Son câblage est exercé en isolation par `DiagnosticModuleTest` (injecteur
/// local). Pour rendre `ServiceDiagnostic` résoluble par l'injecteur applicatif, il faudra
/// ajouter `new DiagnosticModule()` à `RacineInjecteur.creer()`.
public class DiagnosticModule extends AbstractModule {

  @Provides
  @Singleton
  ServiceDiagnostic fournirServiceDiagnostic(
      PassageDao passageDao,
      SessionDao sessionDao,
      JournalDuCapteurDao journalDao,
      ReleveClimatiqueDao releveDao,
      PointDao pointDao,
      Horloge horloge) {
    return new ServiceDiagnostic(passageDao, sessionDao, journalDao, releveDao, pointDao, horloge);
  }
}
