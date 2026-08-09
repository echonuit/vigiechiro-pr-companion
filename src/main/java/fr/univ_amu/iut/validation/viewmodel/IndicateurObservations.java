package fr.univ_amu.iut.validation.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.IndicateurAccueil;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.util.Objects;

/// Compteur d'accueil de la feature `validation` : nombre d'observations (détections de
/// chauves-souris). Passe par le service métier (couche `viewmodel → service → dao`). Enregistré
/// dans le `Multibinder<IndicateurAccueil>` par [fr.univ_amu.iut.validation.di.ValidationModule].
public final class IndicateurObservations implements IndicateurAccueil {

    private final ServiceValidation serviceValidation;

    @Inject
    public IndicateurObservations(ServiceValidation serviceValidation) {
        this.serviceValidation = Objects.requireNonNull(serviceValidation, "serviceValidation");
    }

    @Override
    public int ordre() {
        return 40;
    }

    @Override
    public String iconeLiteral() {
        return "fas-paw";
    }

    @Override
    public String couleur() {
        return "#f39c12";
    }

    @Override
    public String libelle() {
        return "Observations";
    }

    @Override
    public long valeur() {
        return serviceValidation.compterObservations();
    }
}
