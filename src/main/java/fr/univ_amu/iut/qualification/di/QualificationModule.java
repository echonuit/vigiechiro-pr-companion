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
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;

/**
 * Module Guice de la feature {@code qualification} : fournit son DAO, ses moteurs métier ({@link
 * GenerateurSelection}, {@link PreCheckNuit}) et son service ({@link ServiceQualification}) à
 * partir de la {@link SourceDeDonnees} (binder en singleton par {@code CommunModule}).
 *
 * <p>Comme {@code SitesModule}, on utilise des méthodes {@code @Provides} (et non {@code @Inject}
 * sur les classes métier) pour garder la couche {@code model} indépendante du framework d'injection
 * : DAO, moteurs et service restent de simples objets réutilisables (objectif réutilisation O6).
 *
 * <p>L'assemblage du service est <b>inter-modules</b> : il reçoit les DAO de {@code passage}
 * ({@link SequenceDao}, {@link SessionDao}, {@link EnregistrementOriginalDao}, {@link PassageDao}),
 * les DAO de {@code sites} ({@link PointDao}, {@link SiteDao}, pour le préfixe R6) et l'{@link
 * UniteDeTravail} du socle. Le sens des dépendances ({@code qualification → passage}, {@code
 * qualification → sites}) reste acyclique (contrôlé par {@code ArchitectureTest}).
 */
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
}
