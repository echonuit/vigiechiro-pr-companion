package fr.univ_amu.iut.commun.view.carte;

/// Contrainte appliquée à la position d'un marqueur **pendant son déplacement** (mode édition de la
/// [CarteSites]). À chaque pas de glisser/clavier, la position WGS84 proposée est passée à
/// [#contraindre] ; la valeur **retournée** (éventuellement ajustée) est celle réellement appliquée au
/// marqueur et remontée par [DeplacementMarqueur].
///
/// Usage type : **garder un point dans son carré** — l'appelant clampe `(latitude, longitude)` aux bornes
/// de l'emprise du carré du `point`, de sorte que le marqueur suive la souris mais s'arrête au bord.
///
/// Par défaut, la carte n'applique **aucune** contrainte (identité). Le composant reste donc agnostique du
/// domaine : c'est l'appelant (p. ex. la vue multi-sites) qui décide de la règle.
@FunctionalInterface
public interface ContrainteDeplacement {

    /// Ajuste la position proposée pour `point` et renvoie les coordonnées retenues sous la forme
    /// `{latitude, longitude}` (jamais nul, longueur 2).
    double[] contraindre(PointGeo point, double latitude, double longitude);
}
