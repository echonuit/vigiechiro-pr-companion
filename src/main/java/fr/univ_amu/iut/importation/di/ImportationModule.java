package fr.univ_amu.iut.importation.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;

/**
 * Module Guice de la feature {@code importation} : fournit les moteurs du parcours d'import P2
 * (inspection du journal, copie protégée, renommage, transformation audio), le DAO transactionnel
 * de l'agrégat et le service d'orchestration {@link ServiceImport}.
 *
 * <p>Même patron que {@code SitesModule} / {@code QualificationModule} : des méthodes
 * {@code @Provides @Singleton} assemblent des classes métier restées <b>sans annotation
 * d'injection</b> (couche {@code model} indépendante du framework, objectif réutilisation O6).
 *
 * <p>L'assemblage de {@link ServiceImport} est <b>inter-modules</b> : il reçoit l'{@link
 * UniteDeTravail}, le {@link Workspace} et l'{@link Horloge} du socle, plus les moteurs et l'{@link
 * AgregatImportDao} de la feature. Le DAO écrit dans des tables possédées par {@code passage}, mais
 * la dépendance va {@code importation → passage.model} (jamais l'inverse) : le graphe reste
 * acyclique (contrôlé par {@code ArchitectureTest}).
 *
 * <p><b>Installé</b> dans {@code RacineInjecteur} (la racine de composition de l'application) :
 * {@link ServiceImport} est donc résoluble par l'injecteur applicatif. Le câblage en isolation
 * reste validé par {@code ImportationModuleTest} (injecteur local socle + passage + importation).
 */
public class ImportationModule extends AbstractModule {

  @Provides
  @Singleton
  AnalyseurLogPR fournirAnalyseurLogPR() {
    return new AnalyseurLogPR();
  }

  @Provides
  @Singleton
  InspecteurDossier fournirInspecteurDossier(AnalyseurLogPR analyseurLog) {
    return new InspecteurDossier(analyseurLog);
  }

  @Provides
  @Singleton
  CopieProtegee fournirCopieProtegee() {
    return new CopieProtegee();
  }

  @Provides
  @Singleton
  Renommeur fournirRenommeur() {
    return new Renommeur();
  }

  @Provides
  @Singleton
  TransformationAudio fournirTransformationAudio() {
    return new TransformationAudio();
  }

  @Provides
  @Singleton
  AgregatImportDao fournirAgregatImportDao(SourceDeDonnees source) {
    return new AgregatImportDao(source);
  }

  @Provides
  @Singleton
  ServiceImport fournirServiceImport(
      InspecteurDossier inspecteur,
      CopieProtegee copie,
      Renommeur renommeur,
      TransformationAudio transformation,
      AgregatImportDao agregatDao,
      UniteDeTravail uniteDeTravail,
      Workspace workspace,
      Horloge horloge) {
    return new ServiceImport(
        inspecteur,
        copie,
        renommeur,
        transformation,
        agregatDao,
        uniteDeTravail,
        workspace,
        horloge);
  }
}
