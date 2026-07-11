package fr.univ_amu.iut.commun.view;

import java.util.Map;

/// Pont entre une **vue** et son (ou ses) sélecteur(s) de colonnes, pour que les **vues mémorisées** (#623)
/// capturent et rejouent aussi la disposition des colonnes (#994). Le controller en fournit un à
/// [GestionnaireVues] ; une vue **sans** colonnes gérées n'en fournit pas (`null`).
///
/// La map est **indexée par table** (clé stable propre à la vue) : un écran mono-table n'a qu'une entrée
/// (ex. `"principale"`), l'analyse en a une par table (`"especes"`, `"carres"`, `"observations"`). Chaque
/// entrée est un [DescripteurColonnes] produit / rejoué par [GestionnaireColonnes#decrire] /
/// [GestionnaireColonnes#restaurer].
public interface AdaptateurColonnes {

    /// Décrit la disposition courante des colonnes de la vue, une entrée par table.
    Map<String, DescripteurColonnes> decrire();

    /// Rejoue les dispositions fournies (une entrée par table ; les clés inconnues sont ignorées, les tables
    /// absentes de la map restent inchangées).
    void restaurer(Map<String, DescripteurColonnes> dispositions);
}
