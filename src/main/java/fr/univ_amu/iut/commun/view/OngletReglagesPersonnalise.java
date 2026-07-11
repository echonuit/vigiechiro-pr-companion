package fr.univ_amu.iut.commun.view;

import javafx.scene.Node;

/// Variante d'[OngletReglages] dotée d'une **échappatoire** : un nœud JavaFX construit par la feature,
/// rendu **sous** les contrôles générés à partir des [DescripteurReglage]. Réservée aux réglages qui
/// ne se décrivent pas de façon déclarative (aperçu interactif, éditeur spécifique…).
///
/// Contrairement à [OngletReglages] (implémentable en `viewmodel`, agnostique de JavaFX), une
/// implémentation de ce contrat manipule `javafx.scene.Node` : elle vit donc dans le `view` de la
/// feature. À n'utiliser qu'en dernier recours, le rendu déclaratif garantissant la cohérence
/// visuelle de l'écran.
public interface OngletReglagesPersonnalise extends OngletReglages {

    /// Nœud custom ajouté sous les contrôles déclaratifs de l'onglet. Ne doit jamais renvoyer `null`
    /// (renvoyer un conteneur vide si rien à afficher).
    Node formulairePersonnalise();
}
