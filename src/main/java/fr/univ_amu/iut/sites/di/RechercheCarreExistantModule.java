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
import fr.univ_amu.iut.sites.model.ImportSiteDistant;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;

/// Liaison **réelle** de « ce carré existe-t-il déjà ? » (#3458) et de sa suite, « alors récupérons-le »
/// (#3806) : pose la valeur de
/// l'`OptionalBinder<RechercheCarreExistant>` déclaré vide par [SitesModule]. Calqué sur
/// [ControleCarreStocModule] et [PublicationPointModule] : même raison d'être, même clé qualifiée
/// `@Named` contre l'auto-référence.
///
/// Chargé **uniquement** dans l'injecteur applicatif complet, là où `ClientVigieChiro` est lié (par
/// `ConnexionModule`) : les injecteurs partiels de capture, assemblés sans `connexion`, reçoivent un
/// `Optional.empty()` et la modale de déclaration n'y propose pas la vérification.
public class RechercheCarreExistantModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro-carre-existant";

    /// Identité de la feature. `OPTIONNELLE` : la désactiver retire de la modale la vérification et la
    /// récupération, sans rien empêcher - déclarer le carré à la main et aller le chercher sur le portail
    /// reste le repli, et c'est précisément le détour que cette feature évite (retour du 2026-08-07).
    ///
    /// ⚠️ **Une seule feature pour les deux gestes**, à dessein. Le rapatriement n'existe que comme suite
    /// du verdict : les séparer permettrait un état où la modale annonce « ce carré existe déjà » sans
    /// pouvoir rien en faire, ce qui est pire que de ne pas poser la question. L'identifiant reste
    /// `carre-existant` pour que les réglages déjà enregistrés ne se perdent pas.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite(
                "carre-existant", "Vérifier et récupérer un carré depuis Vigie-Chiro", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), RechercheCarreExistant.class)
                .setBinding()
                .to(Key.get(RechercheCarreExistant.class, Names.named(QUALIFIANT)));
        OptionalBinder.newOptionalBinder(binder(), RapatriementCarre.class)
                .setBinding()
                .to(Key.get(RapatriementCarre.class, Names.named(QUALIFIANT)));
    }

    /// Le rapatriement partage la mécanique d'import de la synchronisation périodique
    /// ([ImportSiteDistant], fourni par [SitesModule]) : un carré arrivé par l'une ou par l'autre laisse
    /// le même état local.
    @Provides
    @Singleton
    @Named(QUALIFIANT)
    RapatriementCarre fournirRapatriementCarre(ClientVigieChiro client, ImportSiteDistant imports) {
        return new RapatriementCarre(client, imports);
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    RechercheCarreExistant fournirRechercheCarreExistant(ClientVigieChiro client) {
        return new RechercheCarreExistant(client);
    }
}
