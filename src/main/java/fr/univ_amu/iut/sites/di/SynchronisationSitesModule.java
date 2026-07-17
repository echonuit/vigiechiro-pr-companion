package fr.univ_amu.iut.sites.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.sites.model.RapprochementSites;
import fr.univ_amu.iut.sites.model.SynchronisationSites;
import java.util.List;
import java.util.Set;

/// Liaison **réelle** de la passerelle [SynchronisationSites] (#1045), patron de
/// `SynchronisationParticipationModule` : chargée seulement dans `RacineInjecteur` (app complète,
/// `ConnexionModule` présent), elle fournit l’instance qualifiée `@Named("vigiechiro")` et la pose sur
/// l’`OptionalBinder` déclaré vide par `SitesModule`. Le qualificateur évite l’auto-référence
/// (`RecursiveBinding`). Hors connexion (injecteurs partiels, tests), l’`Optional` reste vide et
/// M-Sites masque le bouton.
public class SynchronisationSitesModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro";

    /// Identité de la feature. `COEUR` : socle non désactivable (liaison technique, pas un écran).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("synchronisation-sites", "Synchronisation des sites", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), SynchronisationSites.class)
                .setBinding()
                .to(Key.get(SynchronisationSites.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    SynchronisationSites fournirSynchronisationSites(
            RapprochementSites sites, Set<RapprochementVigieChiro> rapprocheurs, ClientVigieChiro client) {
        // Le bouton « Mes sites » rejoue la structure des sites puis ce qui en dépend (squelettes de nuits,
        // #1662), sans re-tirer le référentiel taxons (STRUCTURE global, hors sujet ici).
        List<RapprochementVigieChiro> dependants = rapprocheurs.stream()
                .filter(rapprocheur -> rapprocheur.phase() == RapprochementVigieChiro.Phase.DEPENDANTE)
                .toList();
        return new SynchronisationSites(sites, dependants, client);
    }
}
