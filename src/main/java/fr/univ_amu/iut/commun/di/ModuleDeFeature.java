package fr.univ_amu.iut.commun.di;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import fr.univ_amu.iut.commun.view.ActionMenu;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.IndicateurAccueil;
import fr.univ_amu.iut.commun.view.OngletReglages;

/// Base des modules Guice de **feature** : un [AbstractModule] doté d'un petit **DSL de contribution**
/// qui masque le boilerplate des `Multibinder` des points d'extension du socle (carte d'accueil,
/// indicateur, onglet de réglages, entrée de menu). Chaque `XxxModule extends ModuleDeFeature` devient
/// ainsi un **manifeste** lisible des contributions de sa feature :
///
/// ```
/// protected void configure() {
///     activite(ActiviteMesSites.class);
///     indicateur(IndicateurSites.class);
/// }
/// ```
///
/// Marque aussi une classe comme **module de feature** (par opposition au socle [CommunModule] /
/// [PersistenceModule], toujours explicites) : socle de l'auto-découverte introduite ensuite.
///
/// Les points d'extension **non couverts** (ex. `RapprochementVigieChiro`, ou une déclaration de
/// `Multibinder` vide) restent exprimés directement via `Multibinder` : le DSL n'a vocation qu'à
/// raccourcir les contributions les plus fréquentes, pas à tout envelopper.
public abstract class ModuleDeFeature extends AbstractModule {

    /// Décrit l'**identité** de la feature (id stable, libellé, [Categorie]) : clé du système de
    /// feature-flags (#1057), qui remplace le filtrage par nom de classe simple. Chaque module de
    /// feature la déclare ; le socle s'en sert pour la découverte, l'affichage et la désactivation.
    public abstract Fonctionnalite fonctionnalite();

    /// Contribue une **carte d'activité** à l'accueil (cf. [ActiviteAccueil]).
    protected final void activite(Class<? extends ActiviteAccueil> impl) {
        Multibinder.newSetBinder(binder(), ActiviteAccueil.class).addBinding().to(impl);
    }

    /// Contribue un **indicateur** (compteur) au tableau de bord d'accueil (cf. [IndicateurAccueil]).
    protected final void indicateur(Class<? extends IndicateurAccueil> impl) {
        Multibinder.newSetBinder(binder(), IndicateurAccueil.class).addBinding().to(impl);
    }

    /// Contribue un **onglet** à l'écran Réglages (cf. [OngletReglages]).
    protected final void ongletReglages(Class<? extends OngletReglages> impl) {
        Multibinder.newSetBinder(binder(), OngletReglages.class).addBinding().to(impl);
    }

    /// Contribue une **entrée** au menu ☰ (cf. [ActionMenu]).
    protected final void actionMenu(Class<? extends ActionMenu> impl) {
        Multibinder.newSetBinder(binder(), ActionMenu.class).addBinding().to(impl);
    }
}
