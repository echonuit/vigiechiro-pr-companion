package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// Port de résolution géographique : la [Commune] qui contient une position GPS, ou **vide** si la
/// résolution est impossible (hors ligne, position en mer ou hors du référentiel, service en panne).
///
/// **Best-effort par contrat** (#2791) : une implémentation ne lève jamais pour une cause réseau -
/// la commune est un confort dérivé et rattrapable, pas une donnée dont un geste métier dépend.
/// Implémentation de production : `ResolveurCommuneApiGeo` (API Géo, `commun.api`) ; en test, une
/// lambda suffit.
@FunctionalInterface
public interface ResolveurCommune {

    /// La commune contenant `position`, ou vide si la résolution n'aboutit pas.
    Optional<Commune> resoudre(PositionGeo position);
}
