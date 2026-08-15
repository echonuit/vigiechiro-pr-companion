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
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;

/// Liaison **réelle** de la recherche « ce carré existe-t-il déjà ? » (#3458) : pose la valeur de
/// l'`OptionalBinder<RechercheCarreExistant>` déclaré vide par [SitesModule]. Calqué sur
/// [ControleCarreStocModule] et [PublicationPointModule] : même raison d'être, même clé qualifiée
/// `@Named` contre l'auto-référence.
///
/// Chargé **uniquement** dans l'injecteur applicatif complet, là où `ClientVigieChiro` est lié (par
/// `ConnexionModule`) : les injecteurs partiels de capture, assemblés sans `connexion`, reçoivent un
/// `Optional.empty()` et la modale de déclaration n'y propose pas la vérification.
public class RechercheCarreExistantModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro-carre-existant";

    /// Identité de la feature. `OPTIONNELLE` : la désactiver ne retire qu'une vérification de confort de
    /// la modale de déclaration. Aller voir sur le portail Vigie-Chiro reste le repli - et c'est
    /// précisément le détour que cette feature évite (retour utilisateur du 2026-08-07).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite(
                "carre-existant", "Vérifier qu'un carré n'existe pas déjà sur Vigie-Chiro", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), RechercheCarreExistant.class)
                .setBinding()
                .to(Key.get(RechercheCarreExistant.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    RechercheCarreExistant fournirRechercheCarreExistant(ClientVigieChiro client) {
        return new RechercheCarreExistant(client);
    }
}
