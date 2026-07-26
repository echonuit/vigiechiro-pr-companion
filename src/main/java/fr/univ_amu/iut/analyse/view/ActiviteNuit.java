package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.util.Objects;

/// Carte d'accueil de la feature `analyse` (prisme **« Espèces & biodiversité »**) : ouvre l'écran
/// transverse **Activité de la nuit** sur **tous** les passages de l'utilisateur. Implémente le contrat
/// socle [ActiviteAccueil] et délègue à [NavigationActivite#ouvrirTout].
///
/// Enregistrée dans le `Multibinder<ActiviteAccueil>` par [fr.univ_amu.iut.analyse.di.ActiviteModule],
/// donc **derrière le flag expérimental `activite-nuit`** : la carte n'apparaît que lorsque la feature est
/// activée (#2352, le temps du chantier #2348). Rang 20 : juste après « Espèces & observations ».
public final class ActiviteNuit implements ActiviteAccueil {

    private final NavigationActivite navigation;

    @Inject
    public ActiviteNuit(NavigationActivite navigation) {
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
        return "fas-chart-line";
    }

    @Override
    public String couleur() {
        return "#1a5276";
    }

    @Override
    public String titre() {
        return "Activité de la nuit";
    }

    @Override
    public String description() {
        return "La forme d'une nuit : les contacts par tranche horaire et par espèce, sur l'axe nocturne.";
    }

    @Override
    public String pageDoc() {
        return "activite";
    }

    @Override
    public void ouvrir() {
        navigation.ouvrirTout();
    }
}
