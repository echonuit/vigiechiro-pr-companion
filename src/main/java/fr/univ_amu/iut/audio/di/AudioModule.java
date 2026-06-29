package fr.univ_amu.iut.audio.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import fr.univ_amu.iut.audio.view.AccueilSonsReference;
import fr.univ_amu.iut.audio.view.NavigationAudio;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.validation.model.ServiceValidation;

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
public class AudioModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(OuvrirAudio.class).to(NavigationAudio.class);
        Multibinder.newSetBinder(binder(), ActiviteAccueil.class).addBinding().to(AccueilSonsReference.class);
    }

    // ViewModel non-singleton (cf. analyse / multisite) : un VM frais par chargement d'écran, pour éviter
    // que des listeners de vues fermées restent accrochés.
    @Provides
    AudioViewModel fournirAudioViewModel(ServiceValidation validation, ServiceBibliotheque bibliotheque) {
        return new AudioViewModel(validation, bibliotheque);
    }
}
