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
/// Il porte aussi les **dimensions de contexte** (carré, point, passage) : elles ne servent pas à
/// l'agrégation par espèce, mais elles rendent le contact **filtrable en cascade** (carré → point →
/// passage → espèce) par le socle `Filtres`, quand la vue Activité couvre tous les passages et non un
/// seul.
///
/// @param taxon code du taxon **retenu** (`COALESCE(observateur, tadarida)`), ou `null` (non identifié)
/// @param nomEspece nom vernaculaire de l'espèce retenue, ou `null` (souche hors référentiel)
/// @param groupe nom du groupe taxonomique parent (ex. « Chiroptères »), ou `null`
/// @param heure instant réel de capture, ou `null` (séquence non horodatée)
/// @param numeroCarre numéro du carré du passage, ou `null`
/// @param codePoint code du point d'écoute du passage, ou `null`
/// @param idPassage identifiant du passage d'où vient le contact, ou `null`
public record ContactHoraire(
        String taxon,
        String nomEspece,
        String groupe,
        LocalDateTime heure,
        String numeroCarre,
        String codePoint,
        Long idPassage) {

    /// Constructeur de compatibilité **sans contexte géographique** (carré/point/passage nuls) : pour les
    /// usages qui n'agrègent ou ne trient que par espèce et heure (agrégation pure, sélection d'espèces),
    /// où les dimensions de filtre n'ont pas de sens.
    public ContactHoraire(String taxon, String nomEspece, String groupe, LocalDateTime heure) {
        this(taxon, nomEspece, groupe, heure, null, null, null);
    }
}
