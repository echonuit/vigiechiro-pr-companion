package fr.univ_amu.iut.validation.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.GroupeTaxonomiqueDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import fr.univ_amu.iut.validation.view.NavigationValidation;
import fr.univ_amu.iut.validation.viewmodel.ValidationViewModel;

/// Module Guice de la feature `validation` : fournit ses DAO, ses moteurs CSV
/// ([ParserCsvTadarida], [ExportVuCsv]) et son service ([ServiceValidation]) à partir de la
/// [SourceDeDonnees] (binder en singleton par `CommunModule`).
///
/// Comme `SitesModule`, on utilise des méthodes `@Provides` (et non `@Inject` sur les DAO ni le
/// service) pour garder la couche `model` indépendante du framework d'injection : DAO, parseur,
/// écrivain et service restent de simples objets réutilisables, c'est ce module qui sait les
/// assembler.
///
/// L'assemblage du service est **inter-modules** : il reçoit les DAO de `passage` ([SessionDao],
/// [SequenceDao], pour raccrocher les observations à leurs séquences) et l'[Horloge] du socle. Le
/// sens des dépendances (`validation → passage`) reste acyclique (contrôlé par
/// `ArchitectureTest`).
public class ValidationModule extends AbstractModule {

  /// Fournit le contrat de navigation socle [OuvrirValidation] : M-Passage l'injecte pour ouvrir la
  /// validation Tadarida sans dépendre de la vue de cette feature (graphe de slices acyclique).
  @Override
  protected void configure() {
    bind(OuvrirValidation.class).to(NavigationValidation.class);
  }

  @Provides
  @Singleton
  GroupeTaxonomiqueDao fournirGroupeTaxonomiqueDao(SourceDeDonnees source) {
    return new GroupeTaxonomiqueDao(source);
  }

  @Provides
  @Singleton
  TaxonDao fournirTaxonDao(SourceDeDonnees source) {
    return new TaxonDao(source);
  }

  @Provides
  @Singleton
  ResultatsIdentificationDao fournirResultatsIdentificationDao(SourceDeDonnees source) {
    return new ResultatsIdentificationDao(source);
  }

  @Provides
  @Singleton
  ObservationDao fournirObservationDao(SourceDeDonnees source) {
    return new ObservationDao(source);
  }

  @Provides
  @Singleton
  ParserCsvTadarida fournirParserCsvTadarida() {
    return new ParserCsvTadarida();
  }

  @Provides
  @Singleton
  ExportVuCsv fournirExportVuCsv() {
    return new ExportVuCsv();
  }

  @Provides
  @Singleton
  ServiceValidation fournirServiceValidation(
      ResultatsIdentificationDao resultatsDao,
      ObservationDao observationDao,
      TaxonDao taxonDao,
      SessionDao sessionDao,
      SequenceDao sequenceDao,
      ParserCsvTadarida parser,
      ExportVuCsv export,
      Horloge horloge) {
    return new ServiceValidation(
        resultatsDao, observationDao, taxonDao, sessionDao, sequenceDao, parser, export, horloge);
  }

  /// ViewModel de M-Vision-Tadarida. **Non-singleton** (un VM frais par chargement FXML).
  @Provides
  ValidationViewModel fournirValidationViewModel(ServiceValidation service) {
    return new ValidationViewModel(service);
  }
}
