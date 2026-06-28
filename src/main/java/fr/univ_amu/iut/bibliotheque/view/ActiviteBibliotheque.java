package fr.univ_amu.iut.bibliotheque.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.util.Objects;

/// Carte d'accueil de la feature `bibliotheque` : ouvre l'écran « Bibliothèque de sons ».
///
/// Implémente le contrat du socle [ActiviteAccueil] et délègue l'ouverture à
/// [NavigationBibliotheque] (même feature). Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.bibliotheque.di.BibliothequeModule]. Prisme **Espèces & biodiversité** (corpus de
/// sons de référence **par espèce**), rang 20 : après « Espèces & observations » (10).
public final class ActiviteBibliotheque implements ActiviteAccueil {

    private final NavigationBibliotheque navigation;

    @Inject
    public ActiviteBibliotheque(NavigationBibliotheque navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public Prisme prisme() {
        return Prisme.ESPECES_BIODIVERSITE;
    }

    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public String iconeLiteral() {
        return "fas-volume-up";
    }

    @Override
    public String couleur() {
        return "#8e44ad";
    }

    @Override
    public String titre() {
        return "Bibliothèque de sons";
    }

    @Override
    public String description() {
        return "Vos sons de référence à écouter et exporter.";
    }

    @Override
    public void ouvrir() {
        navigation.ouvrirAccueil();
    }
}
