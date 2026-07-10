package fr.univ_amu.iut.commun.api;

import java.util.Map;

/// Vue **détaillée** d'une participation (`GET /participations/#id`), au-delà de la liste
/// [ParticipationVigieChiro] : elle porte l'`_etag` (requis en en-tête `If-Match` pour un `PATCH`
/// concurrent-sûr), les métadonnées **synchronisables** (dates, météo, configuration matérielle) et l'état
/// du traitement Tadarida. Sert à **tirer** les données depuis la plateforme (pré-remplir un passage
/// préparé sur le web) et à préparer une mise à jour.
///
/// @param id `_id` de la participation
/// @param etag `_etag` courant (en-tête `If-Match` du PATCH), ou `null` si absent
/// @param point code de la localité (`point`), ou `null`
/// @param dateDebut début de nuit, ISO 8601 (`date_debut`), ou `null`
/// @param dateFin fin de nuit, ISO 8601 (`date_fin`), ou `null`
/// @param meteo bloc météo (vent/couverture), ou `null`
/// @param configuration dictionnaire matériel (`micro0_*`, `detecteur_enregistreur_*`), jamais `null`
///     (vide si absent)
/// @param etatTraitement état du traitement Tadarida (`traitement.etat`, ex. `FINI`), ou `null`
public record ParticipationDetail(
        String id,
        String etag,
        String point,
        String dateDebut,
        String dateFin,
        MeteoDepot meteo,
        Map<String, String> configuration,
        String etatTraitement) {}
