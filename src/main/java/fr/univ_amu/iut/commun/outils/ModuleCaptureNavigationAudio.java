package fr.univ_amu.iut.commun.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import fr.univ_amu.iut.commun.view.OuvrirAudio;

/// Module Guice **pour les outils de capture** fournissant une implémentation **no-op** du contrat
/// socle [OuvrirAudio].
///
/// Depuis que `NavigationValidation` (impl. d'`OuvrirValidation`) délègue à `OuvrirAudio` (#audio), tout
/// injecteur qui installe `ValidationModule` doit pouvoir résoudre `OuvrirAudio` (Guice valide le graphe
/// de bindings à la création). Les outils de capture assemblent des injecteurs **partiels** (sans la
/// feature `audio`) et rendent leur écran **directement** (sans naviguer) : ce no-op satisfait la
/// dépendance sans tirer `audio.di.AudioModule` (qui exigerait à son tour `ServiceBibliotheque` et
/// dupliquerait le binding du `AudioViewModel`). Pendant côté capture du `NavigationDeTestModule` des
/// tests.
public final class ModuleCaptureNavigationAudio extends AbstractModule {

    @Provides
    OuvrirAudio ouvrirAudio() {
        return source -> {
            // Aucun écran ouvert : les captures rendent leur vue directement, sans navigation.
        };
    }
}
