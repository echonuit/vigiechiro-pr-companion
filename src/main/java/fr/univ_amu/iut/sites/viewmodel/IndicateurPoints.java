package fr.univ_amu.iut.sites.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.IndicateurAccueil;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.Objects;

/// Compteur d'accueil de la feature `sites` : nombre de points d'écoute. Passe par le service
/// métier (couche `viewmodel → service → dao`). Enregistré dans le `Multibinder<IndicateurAccueil>`
/// par [fr.univ_amu.iut.sites.di.SitesModule].
public final class IndicateurPoints implements IndicateurAccueil {

    private final ServiceSites serviceSites;

    @Inject
    public IndicateurPoints(ServiceSites serviceSites) {
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
    }

    @Override
    public int ordre() {
        return 20;
    }

    @Override
    public String iconeLiteral() {
        return "fas-map-marker-alt";
    }

    @Override
    public String couleur() {
        return "#16a085";
    }

    @Override
    public String libelle() {
        return "Points d'écoute";
    }

    @Override
    public long valeur() {
        return serviceSites.compterPoints();
    }
}
