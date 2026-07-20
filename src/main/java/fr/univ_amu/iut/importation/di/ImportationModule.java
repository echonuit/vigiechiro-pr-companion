package fr.univ_amu.iut.importation.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.commun.view.OuvrirImportation;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.InventaireParInspection;
import fr.univ_amu.iut.importation.model.OutilsImport;
import fr.univ_amu.iut.importation.model.RegenerationParTransformationAudio;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.importation.view.NavigationImportation;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.importation.viewmodel.OngletReglagesImport;
import fr.univ_amu.iut.importation.viewmodel.PreferenceConservation;
import fr.univ_amu.iut.passage.model.InventaireBrutsSource;
import fr.univ_amu.iut.passage.model.RegenerationSequences;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.Optional;

/// Module Guice de la feature `importation` : fournit les moteurs du parcours d'import P2
/// (inspection du journal, copie protégée, renommage, transformation audio), le DAO transactionnel
/// de l'agrégat et le service d'orchestration [ServiceImport].
///
/// Même patron que `SitesModule` / `QualificationModule` : des méthodes `@Provides @Singleton`
/// assemblent des classes métier restées **sans annotation d'injection** (couche `model`
/// indépendante du framework, objectif réutilisation O6).
///
/// L'assemblage de [ServiceImport] est **inter-modules** : il reçoit l'[UniteDeTravail], le
/// [Workspace] et l'[Horloge] du socle, plus les moteurs et l'[AgregatImportDao] de la feature. Le
/// DAO écrit dans des tables possédées par `passage`, mais la dépendance va
/// `importation → passage.model` (jamais l'inverse) : le graphe reste acyclique (contrôlé par
/// `ArchitectureTest`).
///
/// **Installé** dans `RacineInjecteur` (la racine de composition de l'application) :
/// [ServiceImport] est donc résoluble par l'injecteur applicatif. Le câblage en isolation reste
/// validé par `ImportationModuleTest` (injecteur local socle + passage + importation).
public class ImportationModule extends ModuleDeFeature {

    /// Identité de la feature. `OPTIONNELLE` (désactivable) : son contrat `OuvrirImportation` est neutralisé
    /// chez son consommateur (SiteDetailController l'injecte en `Optional` et masque le bouton si absent).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("importation", "Importation Tadarida", Categorie.OPTIONNELLE);
    }

    /// L'import est une **action contextuelle** (la nuit d'un site précis) : pas de carte d'accueil. Le
    /// point d'entrée est la fiche d'un site, qui ouvre l'import pré-rattaché via le contrat socle
    /// [OuvrirImportation] — `NavigationImportation` (singleton) le fournit, sans que `sites` dépende du
    /// `view` de cette feature.
    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirImportation.class)
                .setBinding()
                .to(NavigationImportation.class);
        // Port RegenerationSequences (#1406) : c'est ici que vit la transformation, et c'est donc d'ici
        // que `passage` obtient de quoi RÉGÉNÉRER les séquences d'un brut retrouvé, sans dépendre de
        // cette feature (l'inverse serait un cycle). Feature désactivée : la voie « bruts » se refuse.
        OptionalBinder.newOptionalBinder(binder(), RegenerationSequences.class)
                .setBinding()
                .to(RegenerationParTransformationAudio.class);
        // Port InventaireBrutsSource (#1649) : c'est ici que vit la lecture du log (fréquence d'acquisition)
        // et l'inspection du dossier, d'où `passage` obtient l'inventaire des bruts d'un passage RECONSTRUIT
        // pour l'hydrater (#1650). Feature désactivée : le passage reconstruit reste sur le compte rendu
        // honnête (#1648).
        OptionalBinder.newOptionalBinder(binder(), InventaireBrutsSource.class)
                .setBinding()
                .to(InventaireParInspection.class);
        // Onglet « Import » de l'écran Réglages (#928) : contribué au point d'extension du socle,
        // sans que le socle connaisse cette feature.
        ongletReglages(OngletReglagesImport.class);
    }

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

    /// Les moyens d'écriture de l'import (#2041), avec la lecture disque réelle.
    @Provides
    @Singleton
    OutilsImport fournirOutilsImport(CopieProtegee copie, Renommeur renommeur, TransformationAudio transformation) {
        return OutilsImport.reels(copie, renommeur, transformation);
    }

    @Provides
    @Singleton
    ServiceImport fournirServiceImport(
            InspecteurDossier inspecteur,
            OutilsImport outils,
            AgregatImportDao agregatDao,
            UniteDeTravail uniteDeTravail,
            Workspace workspace,
            Horloge horloge,
            CompteurValidations compteurValidations,
            ServiceSauvegarde serviceSauvegarde,
            Optional<SynchronisationParticipation> synchronisation) {
        return new ServiceImport(
                inspecteur,
                outils,
                agregatDao,
                uniteDeTravail,
                workspace,
                horloge,
                compteurValidations,
                serviceSauvegarde,
                synchronisation);
    }

    /// ViewModel de l'assistant M-Import. **Non-singleton** (un VM frais par chargement FXML : un
    /// écran rouvert ne réutilise pas l'état d'un précédent, cf. patron `SitesModule`). Dépend de
    /// [ServiceSites] et de l'utilisateur courant (fournis par `SitesModule`) pour lister les
    /// sites/points : dépendance `importation → sites` sur le `model` d'une autre feature.
    @Provides
    ImportationViewModel fournirImportationViewModel(
            ServiceImport serviceImport,
            ServiceSites serviceSites,
            Horloge horloge,
            @Named("idUtilisateurCourant") String idUtilisateur,
            NavigationViewModel navigation,
            PreferenceConservation conservation) {
        return new ImportationViewModel(serviceImport, serviceSites, horloge, idUtilisateur, navigation, conservation);
    }

    /// Préférence « conserver les originaux » **partagée** (singleton) entre l'écran d'import (liaison de
    /// la case) et son ViewModel (recréé à chaque chargement FXML) : le choix survit à la réouverture de
    /// l'écran et, via [Reglages], d'une session à l'autre.
    @Provides
    @Singleton
    PreferenceConservation fournirPreferenceConservation(Reglages reglages) {
        return new PreferenceConservation(reglages);
    }
}
