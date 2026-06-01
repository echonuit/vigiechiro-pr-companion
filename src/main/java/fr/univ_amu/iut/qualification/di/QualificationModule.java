package fr.univ_amu.iut.qualification.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;

/// Module Guice de la feature `qualification` : fournit son DAO, ses moteurs métier
/// ([GenerateurSelection], [PreCheckNuit]) et son service ([ServiceQualification]) à partir
/// de la [SourceDeDonnees] (binder en singleton par `CommunModule`).
///
/// Comme `SitesModule`, on utilise des méthodes `@Provides` (et non `@Inject` sur les
/// classes métier) pour garder la couche `model` indépendante du framework d'injection :
/// DAO, moteurs et service restent de simples objets réutilisables (objectif réutilisation
/// O6).
///
/// L'assemblage du service est **inter-modules** : il reçoit les DAO de `passage`
/// ([SequenceDao], [SessionDao], [EnregistrementOriginalDao], [PassageDao]), les DAO de
/// `sites` ([PointDao], [SiteDao], pour le préfixe R6) et l'[UniteDeTravail] du socle. Le
/// sens des dépendances (`qualification → passage`, `qualification → sites`) reste acyclique
/// (contrôlé par `ArchitectureTest`).
public class QualificationModule extends AbstractModule {

  @Provides
  @Singleton
  SelectionDao fournirSelectionDao(SourceDeDonnees source) {
    return new SelectionDao(source);
  }

  @Provides
  @Singleton
  GenerateurSelection fournirGenerateurSelection() {
    return new GenerateurSelection();
  }

  @Provides
  @Singleton
  PreCheckNuit fournirPreCheckNuit() {
    return new PreCheckNuit();
  }

  @Provides
  @Singleton
  ServiceQualification fournirServiceQualification(
      SelectionDao selectionDao,
      SequenceDao sequenceDao,
      SessionDao sessionDao,
      EnregistrementOriginalDao originalDao,
      PassageDao passageDao,
      PointDao pointDao,
      SiteDao siteDao,
      GenerateurSelection generateur,
      PreCheckNuit preCheck,
      UniteDeTravail uniteDeTravail) {
    return new ServiceQualification(
        selectionDao,
        sequenceDao,
        sessionDao,
        originalDao,
        passageDao,
        pointDao,
        siteDao,
        generateur,
        preCheck,
        uniteDeTravail);
  }

  /// ViewModel du noyau verdict de M-Qualification. **Non-singleton** (un VM frais par chargement
  /// FXML, comme les autres features) : un écran rouvert ne réutilise pas l'état d'un précédent.
  @Provides
  QualificationViewModel fournirQualificationViewModel(ServiceQualification service) {
    return new QualificationViewModel(service);
  }

  /// ViewModel de la sélection d'écoute de M-Qualification. **Non-singleton** (idem). Câblé au même
  /// [ServiceQualification] que le noyau verdict : le controller ouvre les deux VM sur le même
  /// passage.
  @Provides
  SelectionEcouteViewModel fournirSelectionEcouteViewModel(ServiceQualification service) {
    return new SelectionEcouteViewModel(service);
  }
}
