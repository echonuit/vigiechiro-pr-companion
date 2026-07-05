package fr.univ_amu.iut.commun.model;

/// Plage horaire **nocturne** exprimée en heures pleines locales (0–23), à cheval sur minuit :
/// `heureDebut` (coucher du soleil) est en général supérieure à `heureFin` (lever).
///
/// Sert de **valeur par défaut** au critère de filtre « Heure » de la vue audio (#549) : sur un
/// passage, la nuit réelle (coucher → lever, via [EphemerideSolaire]) remplace le défaut fixe
/// 21 h → 6 h, qui écarte trop en été et trop peu en hiver.
///
/// @param heureDebut heure pleine du coucher du soleil (0–23)
/// @param heureFin heure pleine du lever du soleil (0–23)
public record PlageNuit(int heureDebut, int heureFin) {}
