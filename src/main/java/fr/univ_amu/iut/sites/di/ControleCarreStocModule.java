package fr.univ_amu.iut.sites.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.sites.model.ControleCarreStoc;

/// Liaison **réelle** du contrôle de carré STOC (#733), patron de [SynchronisationSitesModule] : il lit la
/// grille nationale sur la plateforme, il a donc besoin de la **connexion**. Chargé seulement dans l'app
/// complète (`ConnexionModule` présent), il pose l'instance sur l'`OptionalBinder` déclaré vide par
/// `SitesModule`. Hors connexion (injecteurs partiels, tests), l'`Optional` reste vide et la modale de
/// point ne contrôle rien : la saisie manuelle reste entière.
///
/// Le qualificateur `@Named` évite l'auto-référence (`RecursiveBinding`) sur l'`OptionalBinder`.
public class ControleCarreStocModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro";

    /// Identité de la feature. **Optionnelle** (désactivable) : c'est un confort de saisie — un appel réseau
    /// par point géolocalisé — dont rien d'autre ne dépend.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("controle-carre-stoc", "Contrôle du carré STOC", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), ControleCarreStoc.class)
                .setBinding()
                .to(Key.get(ControleCarreStoc.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    ControleCarreStoc fournirControleCarreStoc(ClientVigieChiro client) {
        return new ControleCarreStoc(client);
    }
}
