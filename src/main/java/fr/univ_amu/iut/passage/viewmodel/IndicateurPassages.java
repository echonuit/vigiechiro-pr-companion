package fr.univ_amu.iut.passage.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.viewmodel.IndicateurAccueil;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.util.Objects;

/// Compteur d'accueil de la feature `passage` : nombre de passages (nuits de capture). Passe par
/// le service métier (couche `viewmodel → service → dao`). Enregistré dans le
/// `Multibinder<IndicateurAccueil>` par [fr.univ_amu.iut.passage.di.PassageModule].
public final class IndicateurPassages implements IndicateurAccueil {

    private final ServicePassage servicePassage;

    @Inject
    public IndicateurPassages(ServicePassage servicePassage) {
        this.servicePassage = Objects.requireNonNull(servicePassage, "servicePassage");
    }

    @Override
    public int ordre() {
        return 30;
    }

    @Override
    public String iconeLiteral() {
        return "fas-moon";
    }

    @Override
    public String couleur() {
        return "#a29bfe";
    }

    @Override
    public String libelle() {
        return "Passages";
    }

    @Override
    public long valeur() {
        return servicePassage.compterPassages();
    }
}
