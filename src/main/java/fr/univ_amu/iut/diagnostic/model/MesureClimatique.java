package fr.univ_amu.iut.diagnostic.model;

import java.time.LocalDate;
import java.time.LocalTime;

/// Une mesure ponctuelle du relevé climatique (C10, R20) : un point de la série temporelle
/// T°/hygrométrie de la sonde embarquée, prêt à alimenter un graphe (P6-CA1).
///
/// Le firmware Teensy écrit ~1 mesure / 600 s dans le fichier `PaRecPR<sn>_THLog.csv` (séparateur
/// tabulation). Le modèle transporte des valeurs **typées** (et non du texte brut) pour être
/// directement traçables : `date`/`heure` pour l'axe des abscisses,
/// `temperatureCelsius`/`humiditePourcent` pour les ordonnées.
///
/// @param date jour de la mesure
/// @param heure heure de la mesure
/// @param temperatureCelsius température en degrés Celsius
/// @param humiditePourcent humidité relative en pourcentage
public record MesureClimatique(
    LocalDate date, LocalTime heure, double temperatureCelsius, int humiditePourcent) {}
