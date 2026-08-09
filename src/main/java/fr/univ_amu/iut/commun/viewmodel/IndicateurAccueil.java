package fr.univ_amu.iut.commun.viewmodel;

/// Indicateur chiffré du **tableau de bord d'accueil** (bandeau de compteurs au-dessus des
/// cartes d'activités) : « 12 sites », « 340 observations »…
///
/// Même mécanisme d'inversion de dépendance que
/// [fr.univ_amu.iut.commun.view.ActiviteAccueil] : le socle déclare ce contrat ; chaque feature en
/// fournit une implémentation (qui interroge ses propres DAO/services) et l'enregistre dans le
/// `Multibinder<IndicateurAccueil>` de son module Guice. Le socle bâtit le bandeau **sans dépendre
/// d'aucune feature** (graphe de slices acyclique, cf. `ArchitectureTest`).
///
/// ## Pourquoi ce contrat vit en `viewmodel` et non en `view`
///
/// Il y était, et rien dans son contenu ne le justifiait : cinq accesseurs de types primitifs et de
/// chaînes, aucune classe JavaFX. Sa place en `view` venait de son seul consommateur d'alors, le
/// chrome. Depuis #1376, il est aussi lu par un ViewModel, et un `viewmodel` qui dépend de `view`
/// inverse les couches. Le contrat a donc rejoint la couche que son contenu désignait déjà.
///
/// La [#valeur] est **recalculée à chaque révision des données**
/// ([fr.univ_amu.iut.commun.model.JournalMutations], #3541), et non plus à chaque affichage de
/// l'accueil : une mutation qui survient sans changement d'écran se voyait sinon ignorée (#1376).
public interface IndicateurAccueil {

    /// Rang d'affichage (ordre croissant : les plus petits en premier).
    int ordre();

    /// Code d'icône [Ikonli](https://kordamp.org/ikonli/) FontAwesome 5 de la pastille (ex.
    /// `"fas-moon"`). Le socle en construit un `FontIcon` coloré ; la feature ne dépend d'aucune
    /// classe JavaFX/Ikonli.
    String iconeLiteral();

    /// Couleur d'accent (hex CSS, ex. `"#a29bfe"`) appliquée à l'icône de la pastille.
    String couleur();

    /// Libellé court (ex. « Sites », « Points d'écoute »).
    String libelle();

    /// Valeur courante du compteur (calculée à la volée, ≥ 0).
    long valeur();
}
