package fr.univ_amu.iut.importation.model;

/// Référence légère vers un passage **déjà en base** correspondant à la nuit inspectée (même
/// enregistreur + même date), pour la détection de doublon à l'inspection (#147).
///
/// On ne charge que de quoi rédiger un avertissement lisible (n° de passage + année) ; le détail
/// complet reste dans la table `passage`.
///
/// @param numeroPassage n° de passage dans l'année
/// @param annee année du passage
public record PassageExistant(int numeroPassage, int annee) {}
