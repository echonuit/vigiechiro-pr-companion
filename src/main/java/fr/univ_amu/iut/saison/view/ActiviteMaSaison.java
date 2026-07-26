package fr.univ_amu.iut.saison.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.util.Objects;

/// Carte d'activité **« Ma saison »** de l'accueil (prisme *Collecte & passages*) : elle ouvre l'écran
/// du solde de saison (M-Saison, #2356). Calquée sur [fr.univ_amu.iut.sites.view.ActiviteMesSites].
///
/// La feature reste **agnostique de JavaFX/Ikonli** : la carte ne fait que déclarer un libellé d'icône
/// (`fas-...`) et déléguer l'ouverture à [NavigationSaison] ; c'est le socle (`CartesAccueil`) qui
/// construit le `FontIcon`.
public class ActiviteMaSaison implements ActiviteAccueil {

    private final NavigationSaison navigation;

    @Inject
    public ActiviteMaSaison(NavigationSaison navigation) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    @Override
    public Prisme prisme() {
        return Prisme.COLLECTE_PASSAGES;
    }

    @Override
    public int ordre() {
        // Après « Mes sites » (10) et « Carte & passages » (20) : on pilote la saison une fois les
        // sites déclarés et les passages saisis.
        return 30;
    }

    @Override
    public String iconeLiteral() {
        return "fas-calendar-check";
    }

    @Override
    public String couleur() {
        return "#27ae60";
    }

    @Override
    public String titre() {
        return "Ma saison";
    }

    @Override
    public String description() {
        return "Ce qu'il reste à faire, point par point.";
    }

    @Override
    public String pageDoc() {
        return "saison";
    }

    @Override
    public void ouvrir() {
        navigation.ouvrir();
    }
}
