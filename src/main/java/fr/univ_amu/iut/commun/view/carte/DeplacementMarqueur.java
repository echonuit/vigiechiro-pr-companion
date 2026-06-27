package fr.univ_amu.iut.commun.view.carte;

/// Rappel de **déplacement** d'un marqueur de point sur une carte éditable
/// ([CarteSites#setEditionActive]) : reçoit le point d'origine et sa **nouvelle** position WGS84,
/// après un glisser à la souris ou un déplacement au clavier (flèches). L'appelant décide quoi en
/// faire (mettre à jour des champs, persister via le service…).
@FunctionalInterface
public interface DeplacementMarqueur {

    /// @param point point déplacé (tel qu'affiché avant le déplacement)
    /// @param latitude nouvelle latitude WGS84
    /// @param longitude nouvelle longitude WGS84
    void deplace(PointGeo point, double latitude, double longitude);
}
