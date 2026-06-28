package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.util.Objects;

/// Carte d'accueil de la feature `analyse` (prisme **« Espèces & biodiversité »**) : ouvre l'écran
/// transverse « Espèces & observations ». Implémente le contrat socle [ActiviteAccueil] et délègue à
/// [NavigationAnalyse]. Enregistrée dans le `Multibinder<ActiviteAccueil>` par
/// [fr.univ_amu.iut.analyse.di.AnalyseModule]. Rang 10 : carte de tête de son prisme.
public final class ActiviteAnalyse implements ActiviteAccueil {

    private final NavigationAnalyse navigation;

    @Inject
    public ActiviteAnalyse(NavigationAnalyse navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public Prisme prisme() {
        return Prisme.ESPECES_BIODIVERSITE;
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public String iconeLiteral() {
        return "fas-feather-alt";
    }

    @Override
    public String couleur() {
        return "#1e8449";
    }

    @Override
    public String titre() {
        return "Espèces & observations";
    }

    @Override
    public String description() {
        return "L'inventaire de vos espèces détectées : où, quand, combien — par espèce ou par carré.";
    }

    @Override
    public void ouvrir() {
        navigation.ouvrir();
    }
}
