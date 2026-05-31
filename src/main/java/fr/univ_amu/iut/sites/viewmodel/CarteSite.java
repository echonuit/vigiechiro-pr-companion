package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.sites.model.Site;

/// Données de présentation d'une carte de site sur l'écran M-Sites.
///
/// Agrège le [Site] d'origine (conservé pour la navigation vers le détail) et les valeurs
/// **calculées** affichées sur la carte : compteurs de points et de passages, codes des points,
/// et badge de fraîcheur. Aucune de ces agrégations n'est stockée en base : le
/// [SitesViewModel] les recompose à chaque rafraîchissement.
///
/// C'est un objet immuable de la couche `viewmodel` (pas d'import `javafx.scene`) : la vue s'y
/// lie sans connaître le calcul sous-jacent.
///
/// @param site site d'origine (porte le n° de carré, le nom, le commentaire/localisation)
/// @param nombrePoints nombre de points d'écoute déclarés
/// @param codesPoints codes joints pour l'affichage (ex. `A1 · B2 · C3`), `—` si aucun
/// @param passagesDeLAnnee nombre de passages enregistrés pour l'année courante
/// @param passagesAVerifier nombre de passages encore à vérifier (verdict absent)
/// @param fraicheur niveau de fraîcheur dérivé du dernier passage
/// @param libelleFraicheur libellé affiché dans le badge (ex. `Dernier passage : il y a 2 j`)
public record CarteSite(
    Site site,
    int nombrePoints,
    String codesPoints,
    int passagesDeLAnnee,
    int passagesAVerifier,
    Fraicheur fraicheur,
    String libelleFraicheur) {

  /// `true` s'il reste au moins un passage à vérifier (pilote l'indicateur `⚠`).
  public boolean aDesPassagesAVerifier() {
    return passagesAVerifier > 0;
  }
}
