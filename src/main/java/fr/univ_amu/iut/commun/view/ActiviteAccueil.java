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

    /// **Prisme** auquel rattacher l'activité : le [MainController] regroupe les cartes d'accueil en deux
    /// sections selon cette valeur (« Collecte & passages » / « Espèces & biodiversité »).
    Prisme prisme();

    /// Rang d'affichage **dans son prisme** (ordre croissant : les plus petits en premier).
    int ordre();

    /// Code d'icône [Ikonli](https://kordamp.org/ikonli/) FontAwesome 5 affiché en tête de carte
    /// (ex. `"fas-map-marked-alt"`). C'est le socle qui en construit un `FontIcon` : la feature
    /// reste libre de toute dépendance JavaFX/Ikonli (elle ne manipule qu'une chaîne).
    String iconeLiteral();

    /// Couleur d'accent de la feature en hexadécimal CSS (ex. `"#4a90d9"`). Teinte la pastille
    /// d'icône, le titre de la carte et le chevron qui apparaît au survol.
    String couleur();

    /// Titre court de l'activité (ex. « Mes sites »).
    String titre();

    /// Courte phrase d'invite décrivant l'activité.
    String description();

    /// Ouvre l'activité (typiquement via la façade de navigation de la feature, qui publie une
    /// nouvelle vue dans le [Navigateur]).
    void ouvrir();
}
