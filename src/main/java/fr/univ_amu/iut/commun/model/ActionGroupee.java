package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// Une action applicable à **plusieurs passages** d'affilée (#2357) : préparer un dépôt, téléverser,
/// importer des résultats…
///
/// Le contrat sépare **dire si c'est possible** de **le faire**, parce que le lot annonce à
/// l'utilisateur ce qu'il écarte **avant** de démarrer : « un lot qui ignore silencieusement la moitié
/// de la sélection est pire qu'un lot qui refuse ». [#motifNonEligible] doit donc être **sans effet de
/// bord** et peu coûteux — il est appelé sur toute la sélection avant le moindre traitement.
///
/// Les implémentations vivent dans les features qui possèdent le geste (le dépôt dans `lot`, l'import
/// dans `importation`…) ; le moteur, lui, n'en connaît que cette interface.
public interface ActionGroupee {

    /// Nom du geste, à l'infinitif, tel qu'il apparaît dans le menu et le compte rendu
    /// (« Préparer le dépôt »).
    String libelle();

    /// Pourquoi ce passage ne peut **pas** recevoir l'action, ou [Optional#empty()] s'il le peut.
    ///
    /// Le motif est montré à l'utilisateur : il dit ce qui manque (« déjà déposé », « aucun fichier
    /// vérifié »), pas un code d'erreur.
    Optional<String> motifNonEligible(CiblePassage cible);

    /// Exécute l'action sur ce passage. Lever une exception vaut **échec de ce passage** : le moteur
    /// l'enregistre avec son motif et **poursuit** le lot — un passage en échec n'arrête pas les autres.
    void executer(CiblePassage cible);
}
