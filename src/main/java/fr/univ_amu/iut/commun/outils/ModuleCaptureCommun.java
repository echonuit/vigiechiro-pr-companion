package fr.univ_amu.iut.commun.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.view.ExecuteurFiche;
import fr.univ_amu.iut.commun.view.ExecuteurFicheSynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;

/// Socle Guice **pour les outils de capture** : [CommunModule] avec les exécuteurs hors fil
/// **synchrones** ([ExecuteurTacheSynchrone], [ExecuteurFicheSynchrone]).
///
/// Un aperçu se `snapshot` immédiatement après la mise en page ([ApercuFx]) : avec les exécuteurs
/// **asynchrones** de production, un écran déporté sur le socle (#793) serait capturé **pendant** son
/// chargement - voile d'occupation « Chargement… » à la place du contenu, tables et vue audio vides
/// (le `Platform.runLater` du résultat n'est traité qu'après le snapshot). Comme les tests, les
/// captures doivent être **déterministes** : le travail « hors fil » s'exécute inline, le contenu est
/// posé avant le rendu. Pendant côté capture du binding par défaut (`@ImplementedBy` synchrone) des
/// tests ; sœur de [ModuleCaptureNavigationAudio].
public final class ModuleCaptureCommun {

    private ModuleCaptureCommun() {}

    /// [CommunModule] complet dont les exécuteurs sont surchargés en synchrone : à installer **à la
    /// place** de `new CommunModule()` dans les injecteurs partiels des outils de capture.
    public static Module communSynchrone() {
        return Modules.override(new CommunModule()).with(executeursSynchrones());
    }

    /// La surcharge seule, pour les injecteurs bâtis sur la composition complète :
    /// `Modules.override(RacineInjecteur.modules()).with(executeursSynchrones(), …)`.
    public static Module executeursSynchrones() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(ExecuteurTache.class).to(ExecuteurTacheSynchrone.class).in(Singleton.class);
                bind(ExecuteurFiche.class).to(ExecuteurFicheSynchrone.class).in(Singleton.class);
            }
        };
    }
}
