package fr.univ_amu.iut.diagnostic.model;

import fr.univ_amu.iut.commun.model.Completude;
import java.time.LocalDateTime;

/// État matériel/technique consolidé d'une nuit déjà importée (parcours P6, épopée E6), tel
/// qu'affiché dans l'onglet « Diagnostic » de la fiche passage.
///
/// Agrège, en **lecture seule** (aucun re-parsing lourd des originaux) :
///
/// - [#anomalies()] : anomalies et évènements du journal du capteur (R19) ;
/// - [#climat()] : série T°/hygrométrie prête pour un graphe, avec signalement d'absence (R20) ;
/// - [#gpsLatitude()] / [#gpsLongitude()] : coordonnées du point d'écoute (feature `sites`),
///   socle de l'encart « cohérence horaires » astronomique (P6-CA3/CA4), `null` si non
///   renseignées ;
/// - [#coherenceHoraire()] : confrontation des horaires d'enregistrement à la fenêtre nocturne
///   réelle (coucher → lever du soleil au point, #548) ;
/// - [#numeroSerieEnregistreur()] : clé de comparaison inter-passages (P6-CA5).
///
/// @param idPassage passage diagnostiqué
/// @param idSession session d'enregistrement rattachée
/// @param numeroSerieEnregistreur n° de série de l'enregistreur (comparaison inter-passages)
/// @param anomalies analyse des anomalies du journal (R19)
/// @param climat série climatique de la nuit (R20)
/// @param gpsLatitude latitude du point d'écoute, ou `null`
/// @param gpsLongitude longitude du point d'écoute, ou `null`
/// @param genereLe horodatage de calcul du diagnostic (issu de l'horloge injectée)
/// @param temperatureDebutNuit température en début de nuit (°C), **optionnelle** (`null` si non
/// renseignée, #106)
/// @param coherenceHoraire cohérence des horaires d'enregistrement avec la nuit réelle (#548)
/// @param completude ce que le journal dit de la fin de la nuit, persisté à l'import (#5093).
///     **Autre axe** que la couverture du protocole : une nuit peut être couverte ET tronquée,
///     l'une venant des éphémérides et l'autre du cycle du journal
public record Diagnostic(
        Long idPassage,
        Long idSession,
        String numeroSerieEnregistreur,
        AnalyseAnomalies anomalies,
        SerieClimatique climat,
        Double gpsLatitude,
        Double gpsLongitude,
        LocalDateTime genereLe,
        Double temperatureDebutNuit,
        CoherenceHoraire coherenceHoraire,
        Completude completude) {

    /// `true` si aucun relevé climatique n'est rattaché à la session (R20, à signaler).
    public boolean releveClimatiqueAbsent() {
        return !climat.present();
    }

    /// `true` si les coordonnées GPS du point sont disponibles (précondition encart horaires).
    public boolean coordonneesGpsDisponibles() {
        return gpsLatitude != null && gpsLongitude != null;
    }
}
