package fr.univ_amu.iut.commun.model;

import java.util.Locale;
import java.util.Optional;

/// Ce que le référentiel d'activité dit de la solidité de ses propres seuils (#2351).
///
/// Cette colonne **n'est pas décorative** : c'est elle qui arrête la descente vers les déclinaisons
/// fines. Une moyenne calculée sur douze nuits d'une région donnée est plus **spécifique** qu'une
/// moyenne nationale, et souvent plus **fausse**. Le référentiel le sait et le dit ; le produit doit
/// l'écouter.
public enum ConfianceReferentiel {
    TRES_BONNE("Tres bonne", true),
    BONNE("Bonne", true),
    MODEREE("Moderee", false),
    FAIBLE("Faible", false);

    private final String libelleSource;
    private final boolean fiable;

    ConfianceReferentiel(String libelleSource, boolean fiable) {
        this.libelleSource = libelleSource;
        this.fiable = fiable;
    }

    /// Assez solide pour qu'on s'arrête à cette déclinaison ? `Très bonne` et `Bonne` le sont ;
    /// `Modérée` et `Faible` ne le sont pas : leurs seuils restent affichables, mais **marqués
    /// indicatifs**, et seulement faute de mieux.
    public boolean fiable() {
        return fiable;
    }

    /// Lit la colonne `confiance` de la ressource. Vide sur une valeur inconnue : une confiance qu'on
    /// ne sait pas interpréter ne doit pas être prise pour de la fiabilité par défaut.
    public static Optional<ConfianceReferentiel> depuis(String valeur) {
        if (valeur == null) {
            return Optional.empty();
        }
        String normalise = valeur.trim().toLowerCase(Locale.ROOT);
        for (ConfianceReferentiel confiance : values()) {
            if (confiance.libelleSource.toLowerCase(Locale.ROOT).equals(normalise)) {
                return Optional.of(confiance);
            }
        }
        return Optional.empty();
    }
}
