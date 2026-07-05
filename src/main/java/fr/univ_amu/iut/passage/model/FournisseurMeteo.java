package fr.univ_amu.iut.passage.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/// Port de **récupération météo** d'une nuit d'enregistrement, pour pré-remplir le relevé du dépôt
/// (#547). L'implémentation de référence interroge un service web ([MeteoOpenMeteo]).
///
/// **Jamais bloquant** : hors-ligne, sans coordonnées exploitables ou en l'absence de données, la
/// méthode renvoie [Optional#empty()] (l'utilisateur saisit alors à la main). Aucune exception ne
/// remonte à l'appelant : la météo pré-remplie est un confort, pas une dépendance dure.
public interface FournisseurMeteo {

    /// Relevé météo au point (`latitude`, `longitude`) pour la nuit démarrant à `date` : température à
    /// l'heure de `debut` (début de nuit) et à l'heure de `fin` (fin de nuit, le lendemain matin si
    /// `fin` est avant `debut`), vent et couverture nuageuse en début de nuit. Chaque grandeur absente
    /// vaut `null` dans le relevé ; un relevé introuvable donne [Optional#empty()].
    Optional<MeteoReleve> pour(double latitude, double longitude, LocalDate date, LocalTime debut, LocalTime fin);
}
