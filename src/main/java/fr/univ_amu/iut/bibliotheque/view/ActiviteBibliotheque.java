package fr.univ_amu.iut.bibliotheque.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import java.util.Objects;

/// Carte d'accueil de la feature `bibliotheque` : ouvre l'écran « Bibliothèque de sons ».
///
/// Implémente le contrat du socle [ActiviteAccueil] et délègue l'ouverture à
/// [NavigationBibliotheque] (même feature). Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.bibliotheque.di.BibliothequeModule]. Rang 30 : après « Mes sites » (10) et
/// « Importer une nuit » (20), la bibliothèque étant une activité d'aval (post-validation).
public final class ActiviteBibliotheque implements ActiviteAccueil {

    private final NavigationBibliotheque navigation;

    @Inject
    public ActiviteBibliotheque(NavigationBibliotheque navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public int ordre() {
        return 30;
    }

    @Override
    public String icone() {
        return "🔊";
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
