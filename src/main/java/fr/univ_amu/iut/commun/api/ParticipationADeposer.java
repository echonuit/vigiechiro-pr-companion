package fr.univ_amu.iut.commun.api;

import java.util.Map;

/// Charge utile d'une **création de participation** (`POST /sites/#id/participations`, #142). Calquée sur
/// la forme réelle d'une participation Point Fixe : la localité (`point`), la fenêtre nuit
/// (`date_debut` / `date_fin`, ISO 8601), le bloc [MeteoDepot] et une `configuration` libre (dictionnaire
/// clé → valeur : type détecteur-enregistreur, type / position / hauteur du micro…).
///
/// **Pas de champ `numero`** : le backend Eve le refuse (`422 {"numero": "invalid field"}`, vérifié en
/// réel) — le numéro de passage est déduit côté serveur, pas transmis à la création.
///
/// La sérialisation JSON (noms de champs `snake_case`, champs `null` omis) est faite par
/// [RequetesVigieChiro] ; ce record reste une donnée pure.
///
/// @param point nom de la localité du site (ex. `Z41`)
/// @param dateDebut début de la nuit, ISO 8601 (ex. `2026-07-03T21:00:00`)
/// @param dateFin fin de la nuit, ISO 8601
/// @param meteo bloc météo (vent, couverture), ou `null`
/// @param configuration dictionnaire de configuration matérielle (clés/valeurs libres), ou `null`
/// @param commentaire commentaire libre (optionnel : `null` → champ omis)
public record ParticipationADeposer(
        String point,
        String dateDebut,
        String dateFin,
        MeteoDepot meteo,
        Map<String, String> configuration,
        String commentaire) {}
