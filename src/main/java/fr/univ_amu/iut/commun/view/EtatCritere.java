package fr.univ_amu.iut.commun.view;

import java.util.List;

/// État mémorisable d'une **puce de filtre** : le [CritereFiltre#nom()] du critère et les valeurs de ses
/// contrôles (index de liste déroulante, valeur de curseur) dans l'ordre de l'arbre ; liste vide pour un
/// critère booléen. Photographié/restitué par [GestionnaireFiltres] (mémorisation de session, #484).
///
/// Type-valeur **indépendant du type de ligne filtrée** : il ne décrit que des contrôles d'IHM, ce qui le
/// rend partageable tel quel entre les vues (socle #537).
///
/// @param nom clé stable du critère
/// @param valeurs valeurs des contrôles de la puce, dans l'ordre de l'arbre
public record EtatCritere(String nom, List<Double> valeurs) {}
