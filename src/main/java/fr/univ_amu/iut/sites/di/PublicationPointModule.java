package fr.univ_amu.iut.sites.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.sites.model.PublicationPoint;
import fr.univ_amu.iut.sites.model.dao.PointPublieDao;

/// Liaison **réelle** de la publication d'un point vers Vigie-Chiro (#3458) : pose la valeur de
/// l'`OptionalBinder<PublicationPoint>` déclaré vide par [SitesModule]. Calqué sur
/// [ControleCarreStocModule] et sur `PublicationCorrectionsModule` (même raison d'être, même clé
/// qualifiée `@Named` contre l'auto-référence).
///
/// Chargé **uniquement** dans l'injecteur applicatif complet, là où `ClientVigieChiro` est lié (par
/// `ConnexionModule`) : les injecteurs partiels de capture, assemblés sans `connexion`, reçoivent un
/// `Optional.empty()` et la fiche site n'y propose simplement pas le geste.
public class PublicationPointModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro-point";

    /// Identité de la feature. `OPTIONNELLE` : elle ne binde aucun contrat `Ouvrir…`, et la désactiver ne
    /// retire qu'une action de la fiche site. Déclarer le point à la main sur le portail Vigie-Chiro
    /// reste le repli, et c'est le chemin que tout le monde suivait avant.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("publier-point", "Publication d'un point vers Vigie-Chiro", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), PublicationPoint.class)
                .setBinding()
                .to(Key.get(PublicationPoint.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    PublicationPoint fournirPublicationPoint(ClientVigieChiro client, PointPublieDao publies, FournisseurToken token) {
        return new PublicationPoint(client, publies, token);
    }
}
