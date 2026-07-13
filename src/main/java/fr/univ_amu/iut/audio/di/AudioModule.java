package fr.univ_amu.iut.audio.di;

import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.audio.view.AccueilSonsReference;
import fr.univ_amu.iut.audio.view.NavigationAudio;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.OngletReglagesAudio;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Files;
import java.util.Optional;

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
        // Contrat de retour vers l'analyse : OptionalBinder VIDE (feature `analyse` désactivable, #1087).
        // `AnalyseModule` fait `setBinding` quand elle est active ; sinon SonsValidationController masque
        // « Voir sur la carte » et le segment de fil d'Ariane « Espèces & observations ».
        OptionalBinder.newOptionalBinder(binder(), OuvrirAnalyse.class);
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
            ServiceBibliotheque bibliotheque,
            ServiceDisponibiliteAudio disponibilite) {
        return new AudioViewModel(
                validation,
                projectionsAudio,
                plageNuitPassage,
                validationManuelle,
                marquageDouteux,
                saisieCertitude,
                revueEnLot,
                bibliotheque,
                disponibilite,
                Files::exists);
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
