package fr.univ_amu.iut.validation.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.commun.view.OuvrirValidation;
import fr.univ_amu.iut.passage.model.CrisAttendus;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.CrisDesObservations;
import fr.univ_amu.iut.validation.model.ExportVuCsv;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.ParserCsvTadarida;
import fr.univ_amu.iut.validation.model.RapprochementTaxons;
import fr.univ_amu.iut.validation.model.SelectionObservations;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.GroupeTaxonomiqueDao;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import fr.univ_amu.iut.validation.view.NavigationValidation;
import fr.univ_amu.iut.validation.viewmodel.IndicateurObservations;

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
public class ValidationModule extends ModuleDeFeature {

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("validation", "Validation des observations", Categorie.COEUR);
    }

    /// Fournit le contrat de navigation socle [OuvrirValidation] : M-Passage l'injecte pour ouvrir la
    /// validation Tadarida sans dépendre de la vue de cette feature (graphe de slices acyclique).
    @Override
    protected void configure() {
        bind(OuvrirValidation.class).to(NavigationValidation.class);
        // Port CrisAttendus (#1302) : cette feature possède les observations, elle fournit donc la
        // matière de la vérification acoustique (#1309) à `passage`, qui ne peut pas dépendre d'elle
        // (cycle). Même inversion que CoordonneesPoint (#547).
        OptionalBinder.newOptionalBinder(binder(), CrisAttendus.class)
                .setBinding()
                .to(CrisDesObservations.class);
        // Port socle de comptage des validations menacées : injecté par `passage` (suppression) et
        // `importation` (écrasement) pour leurs confirmations destructives.
        bind(CompteurValidations.class).to(ServiceValidation.class);
        // Compteur du tableau de bord d'accueil : nombre d'observations.
        indicateur(IndicateurObservations.class);
        // Rapprochement du référentiel taxons avec VigieChiro (#728), invoqué à la connexion.
        Multibinder.newSetBinder(binder(), RapprochementVigieChiro.class)
                .addBinding()
                .to(RapprochementTaxons.class);
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

    /// Rapprocheur des taxons (#728) : relie `taxon.code` ↔ `objectid` VigieChiro. Contribué au
    /// `Multibinder<RapprochementVigieChiro>` (cf. [#configure()]). Ne dépend que du [TaxonDao] de la
    /// feature et du [LienVigieChiroDao] du socle : aucune dépendance vers la feature `connexion`.
    @Provides
    @Singleton
    RapprochementTaxons fournirRapprochementTaxons(TaxonDao taxonDao, LienVigieChiroDao liens) {
        return new RapprochementTaxons(taxonDao, liens);
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
    MessageObservationDao fournirMessageObservationDao(SourceDeDonnees source) {
        return new MessageObservationDao(source);
    }

    @Provides
    @Singleton
    ProjectionsAnalyseDao fournirProjectionsAnalyseDao(SourceDeDonnees source) {
        return new ProjectionsAnalyseDao(source);
    }

    @Provides
    @Singleton
    ProjectionsAudioDao fournirProjectionsAudioDao(SourceDeDonnees source) {
        return new ProjectionsAudioDao(source);
    }

    /// Désignation des observations (#1311) : partagée par `lister-observations` et par **tous** les gestes
    /// de revue. C'est ce partage qui rend les gestes par filtre sûrs - on agit sur exactement ce que la
    /// liste a montré.
    @Provides
    SelectionObservations fournirSelectionObservations(ProjectionsAudioDao projections) {
        return new SelectionObservations(projections);
    }

    @Provides
    @Singleton
    ValidationManuelle fournirValidationManuelle(ObservationDao observationDao, TaxonDao taxonDao) {
        return new ValidationManuelle(observationDao, taxonDao);
    }

    @Provides
    @Singleton
    MarquageDouteux fournirMarquageDouteux(ObservationDao observationDao) {
        return new MarquageDouteux(observationDao);
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
            UniteDeTravail uniteDeTravail,
            Horloge horloge,
            MessageObservationDao messageDao,
            LienVigieChiroDao liens) {
        return new ServiceValidation(
                resultatsDao,
                observationDao,
                taxonDao,
                sessionDao,
                sequenceDao,
                parser,
                export,
                uniteDeTravail,
                horloge,
                messageDao,
                liens);
    }
}
