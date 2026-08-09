package fr.univ_amu.iut.sites.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.IndicateurAccueil;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.Objects;

/// Compteur d'accueil de la feature `sites` : nombre de sites de suivi. Passe par le service
/// métier (et non le DAO) pour respecter la couche `viewmodel → service → dao`. Enregistré dans
/// le `Multibinder<IndicateurAccueil>` par [fr.univ_amu.iut.sites.di.SitesModule].
public final class IndicateurSites implements IndicateurAccueil {

    private final ServiceSites serviceSites;

    @Inject
    public IndicateurSites(ServiceSites serviceSites) {
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
    }

    @Override
    public int ordre() {
        return 10;
    }

    @Override
    public String iconeLiteral() {
        return "fas-map-marked-alt";
    }

    @Override
    public String couleur() {
        return "#4a90d9";
    }

    @Override
    public String libelle() {
        return "Sites";
    }

    @Override
    public long valeur() {
        return serviceSites.compterSites();
    }
}
