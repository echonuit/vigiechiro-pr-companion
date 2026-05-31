package fr.univ_amu.iut.commun.view;

/// Point d'entrée d'une feature, présenté comme une « carte » sur l'écran d'accueil du chrome.
///
/// Mécanisme d'inversion de dépendance : le socle (`commun.view`) déclare ce contrat ; chaque
/// feature en fournit une implémentation (dans son propre `view`) et l'enregistre dans le
/// `Multibinder<ActiviteAccueil>` de son module Guice. Le [MainController] injecte alors
/// `Set<ActiviteAccueil>` et bâtit les cartes **sans dépendre d'aucune feature** : le graphe de
/// slices reste acyclique (cf. `ArchitectureTest`), et ajouter une activité à l'accueil revient à
/// écrire une implémentation + une ligne de binding — le socle n'est jamais retouché.
public interface ActiviteAccueil {

  /// Rang d'affichage (ordre croissant : les plus petits en premier).
  int ordre();

  /// Pictogramme (emoji) affiché en tête de carte.
  String icone();

  /// Titre court de l'activité (ex. « Mes sites »).
  String titre();

  /// Courte phrase d'invite décrivant l'activité.
  String description();

  /// Ouvre l'activité (typiquement via la façade de navigation de la feature, qui publie une
  /// nouvelle vue dans le [Navigateur]).
  void ouvrir();
}
