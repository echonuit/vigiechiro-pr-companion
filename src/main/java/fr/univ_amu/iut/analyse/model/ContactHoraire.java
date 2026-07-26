package fr.univ_amu.iut.analyse.model;

import java.time.LocalDateTime;

/// Un **contact daté** d'une espèce : l'entrée atomique de l'agrégation d'activité (#2352). Projection
/// minimale, volontairement détachée de la lourde [...validation.model.LigneObservationAudio] (une
/// trentaine de champs) pour que [AgregationActivite] reste **pure** et trivialement testable, sans
/// dépendre de la projection audio.
///
/// L'heure est l'**instant réel** de capture, issu de l'horodatage porté par le nom de fichier et
/// persisté en base (`listening_sequence.recorded_at`) : c'est la source la plus fiable pour situer un cri
/// dans la nuit, et déjà la clé de jointure avec le CSV d'observations. Un contact sans heure ne peut pas
/// être placé sur l'axe, et un contact sans taxon (séquence non identifiée) n'est pas une espèce :
/// [AgregationActivite] écarte les deux.
///
/// @param taxon code du taxon **retenu** (`COALESCE(observateur, tadarida)`), ou `null` (non identifié)
/// @param nomEspece nom vernaculaire de l'espèce retenue, ou `null` (souche hors référentiel)
/// @param groupe nom du groupe taxonomique parent (ex. « Chiroptères »), ou `null`
/// @param heure instant réel de capture, ou `null` (séquence non horodatée)
public record ContactHoraire(String taxon, String nomEspece, String groupe, LocalDateTime heure) {}
