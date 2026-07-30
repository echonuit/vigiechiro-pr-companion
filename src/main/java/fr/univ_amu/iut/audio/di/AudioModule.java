package fr.univ_amu.iut.audio.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.audio.view.AccueilSonsReference;
import fr.univ_amu.iut.audio.view.NavigationAudio;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.ExporteurAudio;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.OngletReglagesAudio;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.ExportObservationsEtSons;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.PublicationMessage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;

/// Module Guice de la feature `audio` (vue audio unifiée « Sons & validation »).
///
/// Lie le contrat socle [OuvrirAudio] à son implémentation [NavigationAudio] (que les features
/// alimentant la vue injectent sans dépendre de `audio.view`) et fournit le [AudioViewModel], assemblé
/// sur les **services** de `validation` ([ServiceValidation]) et `bibliotheque` ([ServiceBibliotheque]).
/// La feature `audio` est un **puits** (aucun retour vers elle) : le graphe de slices reste acyclique
/// (cf. `ArchitectureTest`).
///
/// **Intégration** : installé dans `RacineInjecteur` après `ValidationModule` et `BibliothequeModule`
/// (qui fournissent ses services). Enregistre la carte d'accueil [AccueilSonsReference] (« Sons de
/// référence ») dans le `Multibinder<ActiviteAccueil>` du socle : elle ouvre la vue audio sur la source
/// `References` (elle remplace l'ancienne carte « Bibliothèque de sons »).
public class AudioModule extends ModuleDeFeature {

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("audio", "Sons et validation", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        bind(OuvrirAudio.class).to(NavigationAudio.class);
        // Port EspecesPrioritaires (#2353) : cette feature CONSOMME le référentiel des espèces à enjeu,
        // détenu par `validation` (table latérale du référentiel taxonomique). Défaut **vide** ici, liaison
        // réelle posée par ValidationModule dans l'application complète. Un injecteur partiel (capture,
        // test d'écran) reste ainsi construisible : sans référentiel, aucune ligne n'est marquée, ce qui
        // est exactement ce que dit une base sans espèce prioritaire, et non une anomalie.
        OptionalBinder.newOptionalBinder(binder(), EspecesPrioritaires.class)
                .setDefault()
                .toInstance(Set::of);
        activite(AccueilSonsReference.class);
        // Onglet « Audio » de l'écran Réglages (#1006) : préférences de lecture (auto-lecture, boucle),
        // partagées avec les options du menu ☰ de la vue audio.
        ongletReglages(OngletReglagesAudio.class);
        // Import VigieChiro (axe 4.2) en liaison **optionnelle** : déclaré à vide ici pour que les injecteurs
        // partiels de capture (sans `connexion`, donc sans client HTTP) résolvent `Optional<ImportVigieChiro>`
        // à vide. La liaison réelle est posée par `ImportVigieChiroModule` (injecteur applicatif complet).
        OptionalBinder.newOptionalBinder(binder(), ImportVigieChiro.class);
        // Publication des corrections (#723) : même patron (liaison réelle posée par
        // `PublicationCorrectionsModule` dans l'injecteur applicatif complet).
        OptionalBinder.newOptionalBinder(binder(), PublicationCorrections.class);
        // Répondre au validateur (#1418) : ÉCRITURE DÉFINITIVE, donc feature à part et désactivable
        // (`DiscussionModule`). Défaut-vide ici : les injecteurs partiels (captures) n'ont pas de client,
        // et la saisie du fil s'y désactive d'elle-même en le disant (affordance #789).
        OptionalBinder.newOptionalBinder(binder(), PublicationMessage.class);
        // Contrat de retour vers l'analyse : OptionalBinder VIDE (feature `analyse` désactivable, #1087).
        // `AnalyseModule` fait `setBinding` quand elle est active ; sinon SonsValidationController masque
        // « Voir sur la carte » et le segment de fil d'Ariane « Espèces & observations ».
        OptionalBinder.newOptionalBinder(binder(), OuvrirAnalyse.class);
    }

    /// La discussion avec le validateur (#1417 lire / #1418 répondre). `PublicationMessage` est optionnel :
    /// la feature `discuter-validateur` est désactivable, et les injecteurs partiels n'ont pas de client.
    /// Lire le fil continue de fonctionner sans elle.
    @Provides
    @Singleton
    DiscussionValidateur fournirDiscussionValidateur(
            ServiceValidation service, StockageConnexion connexion, Optional<PublicationMessage> publication) {
        return new DiscussionValidateur(service, connexion, publication);
    }

    // ViewModel non-singleton (cf. analyse / multisite) : un VM frais par chargement d'écran, pour éviter
    // que des listeners de vues fermées restent accrochés.
    @Provides
    AudioViewModel fournirAudioViewModel(
            ServiceValidation validation,
            ProjectionsAudioDao projectionsAudio,
            PlageNuitPassage plageNuitPassage,
            ValidationManuelle validationManuelle,
            MarquageDouteux marquageDouteux,
            SaisieCertitude saisieCertitude,
            RevueEnLot revueEnLot,
            ExporteurAudio exporteur,
            ServiceDisponibiliteAudio disponibilite,
            DiscussionValidateur discussion) {
        return new AudioViewModel(
                validation,
                projectionsAudio,
                plageNuitPassage,
                validationManuelle,
                marquageDouteux,
                saisieCertitude,
                revueEnLot,
                exporteur,
                disponibilite,
                Files::exists,
                discussion);
    }

    /// Exports de la vue audio (#2793) : assemblés ici pour que le ViewModel reste à son arité - le
    /// service d'archive (CSV + sons) vient de `validation`, la bibliothèque de `bibliotheque`.
    @Provides
    ExporteurAudio fournirExporteurAudio(
            ServiceValidation validation, ServiceBibliotheque bibliotheque, ExportObservationsEtSons exportSons) {
        return new ExporteurAudio(validation, bibliotheque, exportSons);
    }

    /// Le composeur d'archive « observations + sons » (#2792) : séquences et sessions viennent des DAO
    /// de `passage`, dépendance de modèle déjà établie (`audio` réutilise les modèles des puits).
    @Provides
    ExportObservationsEtSons fournirExportObservationsEtSons(SequenceDao sequenceDao, SessionDao sessionDao) {
        return new ExportObservationsEtSons(sequenceDao, sessionDao);
    }

    /// ViewModel dédié de l'**import VigieChiro** (axe 4.2), séparé de [AudioViewModel] (concern distinct, et
    /// pour ne pas alourdir ce VM déjà volumineux). `importVigieChiro` est vide dans les injecteurs partiels
    /// de capture, présent dans l'application complète (cf. `ImportVigieChiroModule`).
    @Provides
    ImportVigieChiroViewModel fournirImportVigieChiroViewModel(Optional<ImportVigieChiro> importVigieChiro) {
        return new ImportVigieChiroViewModel(importVigieChiro);
    }

    /// ViewModel dédié de la **publication des corrections** (#723), jumeau du VM d'import :
    /// `publication` est vide dans les injecteurs partiels de capture, présent dans l'application
    /// complète (cf. `PublicationCorrectionsModule`).
    @Provides
    PublicationCorrectionsViewModel fournirPublicationCorrectionsViewModel(
            Optional<PublicationCorrections> publication) {
        return new PublicationCorrectionsViewModel(publication);
    }
}
