package fr.univ_amu.iut.passage.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.MicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;

/**
 * Module Guice de la feature {@code passage} : fournit ses DAO à partir de la {@link
 * SourceDeDonnees} (singleton fourni par {@code CommunModule}).
 *
 * <p>Même patron que {@code SitesModule} : des méthodes {@code @Provides @Singleton} assemblent des
 * DAO restés <b>sans annotation d'injection</b> (couche {@code model.dao} indépendante du
 * framework, objectif réutilisation O6).
 *
 * <p><b>Non installé</b> dans {@code RacineInjecteur} à ce stade : l'intégration des features dans
 * la racine de composition est faite en phase 3.
 */
public class PassageModule extends AbstractModule {

  @Provides
  @Singleton
  EnregistreurDao fournirEnregistreurDao(SourceDeDonnees source) {
    return new EnregistreurDao(source);
  }

  @Provides
  @Singleton
  MicroDao fournirMicroDao(SourceDeDonnees source) {
    return new MicroDao(source);
  }

  @Provides
  @Singleton
  PassageDao fournirPassageDao(SourceDeDonnees source) {
    return new PassageDao(source);
  }

  @Provides
  @Singleton
  SessionDao fournirSessionDao(SourceDeDonnees source) {
    return new SessionDao(source);
  }

  @Provides
  @Singleton
  EnregistrementOriginalDao fournirEnregistrementOriginalDao(SourceDeDonnees source) {
    return new EnregistrementOriginalDao(source);
  }

  @Provides
  @Singleton
  SequenceDao fournirSequenceDao(SourceDeDonnees source) {
    return new SequenceDao(source);
  }

  @Provides
  @Singleton
  JournalDuCapteurDao fournirJournalDuCapteurDao(SourceDeDonnees source) {
    return new JournalDuCapteurDao(source);
  }

  @Provides
  @Singleton
  ReleveClimatiqueDao fournirReleveClimatiqueDao(SourceDeDonnees source) {
    return new ReleveClimatiqueDao(source);
  }

  /** Moteur (pur) des transitions de workflow d'un passage. */
  @Provides
  @Singleton
  MoteurWorkflowPassage fournirMoteurWorkflowPassage() {
    return new MoteurWorkflowPassage();
  }

  /**
   * Service métier transverse de la feature. Comme le service de référence {@code ServiceSites}, il
   * reste sans annotation d'injection : c'est ce module qui assemble ses dépendances (le {@link
   * PassageDao} de la feature, le {@link MoteurWorkflowPassage} et l'{@link Horloge} du socle).
   */
  @Provides
  @Singleton
  ServicePassage fournirServicePassage(
      PassageDao passageDao, MoteurWorkflowPassage moteur, Horloge horloge) {
    return new ServicePassage(passageDao, moteur, horloge);
  }
}
