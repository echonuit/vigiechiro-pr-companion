package fr.univ_amu.iut.importation.model;

import java.time.LocalDateTime;

/// Une entrée du journal du capteur (évènement ou anomalie) avec son **horodatage** (#1696).
///
/// L'horodatage sert à ranger l'entrée dans la bonne nuit lors d'un import multi-nuits à log unique :
/// sans lui, chaque nuit héritait des évènements de toutes les nuits. Il est `null` pour une entrée
/// **de déploiement** non rattachée à une ligne datée (ex. « sonde absente ») : une telle entrée
/// concerne alors toutes les nuits.
///
/// @param horodatage instant de la ligne de log source, ou `null` si l'entrée n'est pas datée
/// @param texte message conservé (tel qu'affiché dans le diagnostic)
record LigneJournal(LocalDateTime horodatage, String texte) {}
