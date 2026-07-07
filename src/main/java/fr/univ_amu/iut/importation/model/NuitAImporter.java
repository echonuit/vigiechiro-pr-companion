package fr.univ_amu.iut.importation.model;

/// Une nuit **à importer**, associée au **numéro de passage** qui lui sera attribué. Instantané passé au
/// service quand l'import est découpé par nuit : chaque `NuitAImporter` incluse donnera un passage
/// distinct (même point, `numeroPassage` propre, date = [NuitDetectee#dateNuit()]).
///
/// @param numeroPassage n° de passage attribué à cette nuit (auto-numéroté depuis le prochain n° libre)
/// @param nuit la nuit détectée (WAV, date, complétude)
public record NuitAImporter(int numeroPassage, NuitDetectee nuit) {}
