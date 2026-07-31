package fr.univ_amu.iut.commun.view;

import java.util.List;

/// Valeur **sémantique** d'un critère de filtre actif, sous une forme **transportable** entre vues
/// (#537 étape 2) : le nom du critère et sa/ses valeur(s) exprimées en clair (ex. `["VALIDEE"]`,
/// `["Chiroptères"]`, `["0.5"]`, `["21", "6"]`), et non en index de contrôle d'IHM.
///
/// **Indépendant de l'éditeur** : il peut être appliqué à une **autre** vue qui possède un critère de
/// même [#nom()] (base de « Voir sur la carte », #476). Liste de valeurs **vide** pour un critère
/// booléen (sa seule présence active le filtre).
///
/// C'est la **seule** forme mémorisée depuis #3071 : un `EtatCritere` par indices de contrôles a
/// longtemps coexisté pour la mémoire de session, jusqu'à ce qu'on mesure ce qu'il ne savait pas
/// retenir - toute puce à sélection multiple, qu'il rendait vide sans le dire.
///
/// @param nom clé stable du critère (identique entre vues)
/// @param valeurs valeur(s) sémantique(s) courante(s), éventuellement vide
public record DescripteurCritere(String nom, List<String> valeurs) {

    public DescripteurCritere {
        valeurs = List.copyOf(valeurs);
    }
}
