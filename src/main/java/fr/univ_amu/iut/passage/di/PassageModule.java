package fr.univ_amu.iut.passage.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.CoordonneesPoint;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.passage.model.BackfillEmpreintes;
import fr.univ_amu.iut.passage.model.FournisseurMeteo;
import fr.univ_amu.iut.passage.model.MeteoOpenMeteo;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.ReprefixeurSession;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceRattachement;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.MicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.RattachementDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.passage.view.NavigationPassage;
import fr.univ_amu.iut.passage.viewmodel.IndicateurPassages;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import fr.univ_amu.iut.passage.viewmodel.RattachementViewModel;
import java.util.Optional;

/// Module Guice de la feature `passage` : fournit ses DAO à partir de la [SourceDeDonnees]
/// (singleton fourni par `CommunModule`).
///
/// Même patron que `SitesModule` : des méthodes `@Provides @Singleton` assemblent des DAO restés
/// **sans annotation d'injection** (couche `model.dao` indépendante du framework, objectif
/// réutilisation O6).
///
/// **Non installé** dans `RacineInjecteur` à ce stade : l'intégration des features dans la racine
/// de composition est faite en phase 3.
public class PassageModule extends ModuleDeFeature {

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("passage", "Passages", Categorie.COEUR);
    }

    /// Fournit le contrat de navigation socle [OuvrirPassage] : `sites` (M-Site-detail) l'injecte
    /// pour ouvrir M-Passage sans dépendre du `view` de cette feature.
    @Override
    protected void configure() {
        bind(OuvrirPassage.class).to(NavigationPassage.class);
        // Compteur du tableau de bord d'accueil : nombre de passages.
        indicateur(IndicateurPassages.class);
        // Port socle CoordonneesPoint (#547) : cette feature CONSOMME le GPS d'un point (pré-remplissage
        // météo) mais ne peut pas dépendre de `sites` (cycle). Elle pose donc un défaut no-op ; l'app
        // complète installe SitesModule, dont le `setBinding` fournit l'implémentation réelle. Les
        // injecteurs partiels (captures, tests de module) restent construisibles grâce à ce défaut.
        OptionalBinder.newOptionalBinder(binder(), CoordonneesPoint.class)
                .setDefault()
                .toInstance(idPoint -> Optional.empty());

        // Port socle ReferentielPoint (axe 4) : cette feature CONSOMME l'identité VigieChiro d'un point
        // (code de localité + id du site, pour créer/mettre à jour une participation) sans dépendre de
        // `sites`. Même montage que CoordonneesPoint : défaut no-op ici, implémentation réelle par SitesModule.
        OptionalBinder.newOptionalBinder(binder(), ReferentielPoint.class)
                .setDefault()
                .toInstance(idPoint -> Optional.empty());

        // Passerelle SynchronisationParticipation (axe 4) : OptionalBinder VIDE (ni défaut ni binding). Le
        // module réel SynchronisationParticipationModule (chargé par RacineInjecteur avec la connexion) pose
        // le binding ; hors connexion, l'Optional reste vide (patron de DepotVigieChiro).
        OptionalBinder.newOptionalBinder(binder(), SynchronisationParticipation.class);

        // Contrats de navigation vers M-Diagnostic, M-Qualification et M-Lot : OptionalBinder VIDE (features
        // `diagnostic`, `qualification` et `lot` désactivables, #1087). Chaque module réel fait `setBinding`
        // quand sa feature est active ; sinon l'Optional reste vide et PassageController masque la carte
        // correspondante.
        OptionalBinder.newOptionalBinder(binder(), OuvrirDiagnostic.class);
        OptionalBinder.newOptionalBinder(binder(), OuvrirLot.class);
        OptionalBinder.newOptionalBinder(binder(), OuvrirVerification.class);
    }

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
    MaterielMicroDao fournirMaterielMicroDao(SourceDeDonnees source) {
        return new MaterielMicroDao(source);
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

    /// Disponibilité de l'audio local d'un passage (#1298) : singleton, le cache par passage est
    /// partagé par tous les consommateurs (écoute, audit, archivage).
    @Provides
    @Singleton
    ServiceDisponibiliteAudio fournirServiceDisponibiliteAudio(
            SessionDao sessionDao, SequenceDao sequenceDao, Workspace workspace) {
        return new ServiceDisponibiliteAudio(sessionDao, sequenceDao, workspace);
    }

    /// Archivage d'un passage (#1300) : purge volontaire de l'audio, marqueur explicite, capture
    /// des empreintes in extremis.
    @Provides
    @Singleton
    ServiceArchivagePassage fournirServiceArchivagePassage(
            PassageDao passageDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            BackfillEmpreintes backfillEmpreintes,
            ServicePurgeOriginaux purgeOriginaux,
            ServiceDisponibiliteAudio disponibilite,
            Horloge horloge) {
        return new ServiceArchivagePassage(
                passageDao, sessionDao, sequenceDao, backfillEmpreintes, purgeOriginaux, disponibilite, horloge);
    }

    /// Moteur (pur) des transitions de workflow d'un passage.
    @Provides
    @Singleton
    MoteurWorkflowPassage fournirMoteurWorkflowPassage() {
        return new MoteurWorkflowPassage();
    }

    /// Re-préfixage disque du dossier de session (modification du rattachement, E2.S8).
    @Provides
    @Singleton
    ReprefixeurSession fournirReprefixeurSession() {
        return new ReprefixeurSession();
    }

    /// Écritures transactionnelles de la modification du rattachement (E2.S8). Sans état : toutes ses
    /// méthodes reçoivent la connexion transactionnelle de l'[UniteDeTravail].
    @Provides
    @Singleton
    RattachementDao fournirRattachementDao() {
        return new RattachementDao();
    }

    /// Fournisseur météo (pré-remplissage du dépôt, #547) : implémentation Open-Meteo, jamais
    /// bloquante (repli silencieux hors-ligne, pas de GPS ou données absentes).
    @Provides
    @Singleton
    FournisseurMeteo fournirFournisseurMeteo() {
        return new MeteoOpenMeteo();
    }

    /// Service métier transverse de la feature. Comme le service de référence `ServiceSites`, il
    /// reste sans annotation d'injection : c'est ce module qui assemble ses dépendances (DAO de la
    /// feature, [MoteurWorkflowPassage], [Horloge], et pour E2.S8 le [ReprefixeurSession],
    /// l'[UniteDeTravail] du socle et le [RattachementDao] ; pour le pré-remplissage météo #547, le
    /// port socle [CoordonneesPoint] (GPS du point, implémenté par `sites`) et le [FournisseurMeteo]).
    @Provides
    @Singleton
    ServicePassage fournirServicePassage(
            PassageDao passageDao,
            MoteurWorkflowPassage moteur,
            Horloge horloge,
            SessionDao sessionDao,
            SequenceDao sequenceDao) {
        return new ServicePassage(passageDao, moteur, horloge, sessionDao, sequenceDao);
    }

    /// Conditions de la nuit (météo #106/#697, matériel du micro #543), extraites de ServicePassage (#1192).
    @Provides
    @Singleton
    ServiceConditionsPassage fournirServiceConditionsPassage(
            PassageDao passageDao,
            MaterielMicroDao materielDao,
            CoordonneesPoint coordonnees,
            FournisseurMeteo fournisseurMeteo) {
        return new ServiceConditionsPassage(passageDao, materielDao, coordonnees, fournisseurMeteo);
    }

    /// Rattachement rétroactif (E2.S8), extrait de ServicePassage (#1192).
    @Provides
    @Singleton
    ServiceRattachement fournirServiceRattachement(
            PassageDao passageDao,
            SessionDao sessionDao,
            ReprefixeurSession reprefixeur,
            UniteDeTravail uniteDeTravail,
            RattachementDao rattachementDao) {
        return new ServiceRattachement(passageDao, sessionDao, reprefixeur, uniteDeTravail, rattachementDao);
    }

    /// ViewModel de l'écran M-Passage. **Non-singleton** (un VM frais par chargement FXML, comme les
    /// autres features) : un écran rouvert ne réutilise pas l'état d'un précédent.
    @Provides
    PassageViewModel fournirPassageViewModel(ServicePassage service, ServicePurgeOriginaux purge) {
        return new PassageViewModel(service, purge);
    }

    /// ViewModel de la modale « Modifier le rattachement » (E2.S8). **Non-singleton** : un VM frais
    /// par ouverture de modale.
    @Provides
    RattachementViewModel fournirRattachementViewModel(
            ServicePassage service,
            ServiceRattachement rattachement,
            ServiceConditionsPassage conditions,
            Optional<SynchronisationParticipation> synchronisation,
            Optional<ImportObservations> importObservations) {
        return new RattachementViewModel(service, rattachement, conditions, synchronisation, importObservations);
    }
}
