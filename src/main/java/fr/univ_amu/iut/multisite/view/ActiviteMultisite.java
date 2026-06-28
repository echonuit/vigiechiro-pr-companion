package fr.univ_amu.iut.multisite.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.util.Objects;

/// Carte d'accueil de la feature `multisite` : ouvre l'écran « Carte & passages ».
///
/// Implémente le contrat du socle [ActiviteAccueil] et délègue l'ouverture à
/// [NavigationMultisite] (même feature). Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.multisite.di.MultisiteModule]. Prisme **Collecte & passages**, rang 20 : après
/// « Mes sites » (10).
public final class ActiviteMultisite implements ActiviteAccueil {

    private final NavigationMultisite navigation;

    @Inject
    public ActiviteMultisite(NavigationMultisite navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public Prisme prisme() {
        return Prisme.COLLECTE_PASSAGES;
    }

    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public String iconeLiteral() {
        // Carte dépliée : évoque la vue cartographique d'ensemble. Distinct du marqueur de « Mes sites »
        // (fas-map-marked-alt), qui cible un site précis.
        return "fas-map";
    }

    @Override
    public String couleur() {
        return "#e8a838";
    }

    @Override
    public String titre() {
        return "Carte & passages";
    }

    @Override
    public String description() {
        return "La carte de vos sites et le tableau de tous les passages : filtres, tri et export.";
    }

    @Override
    public void ouvrir() {
        navigation.ouvrirAccueil();
    }
}
