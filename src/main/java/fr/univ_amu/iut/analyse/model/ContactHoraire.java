package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.Nuit;
import java.time.LocalDate;
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
/// Il porte aussi les **dimensions de contexte** (commune, carré, point, passage) : elles ne servent pas
/// à l'agrégation par espèce, mais elles rendent le contact **filtrable** par le socle `Filtres`, quand
/// la vue Activité couvre tous les passages et non un seul.
///
/// La **commune** (#2967) est arrivée en dernier, alors que la projection la traversait déjà : elle était
/// disponible sur la ligne source et simplement pas reportée. Son absence rendait Activité le seul écran
/// où l'on ne pouvait pas demander « ce que j'ai entendu sur cette commune ».
///
/// @param taxon code du taxon **retenu** (`COALESCE(observateur, tadarida)`), ou `null` (non identifié)
/// @param nomEspece nom vernaculaire de l'espèce retenue, ou `null` (souche hors référentiel)
/// @param groupe nom du groupe taxonomique parent (ex. « Chiroptères »), ou `null`
/// @param heure instant réel de capture, ou `null` (séquence non horodatée)
/// @param commune nom de la commune du point d'écoute, ou `null` (commune non résolue)
/// @param numeroCarre numéro du carré du passage, ou `null`
/// @param codePoint code du point d'écoute du passage, ou `null`
/// @param idPassage identifiant du passage d'où vient le contact, ou `null`
public record ContactHoraire(
        String taxon,
        String nomEspece,
        String groupe,
        LocalDateTime heure,
        String commune,
        String numeroCarre,
        String codePoint,
        Long idPassage) {

    /// Constructeur de compatibilité **sans contexte géographique** (commune/carré/point/passage nuls) :
    /// pour les usages qui n'agrègent ou ne trient que par espèce et heure (agrégation pure, sélection
    /// d'espèces), où les dimensions de filtre n'ont pas de sens.
    public ContactHoraire(String taxon, String nomEspece, String groupe, LocalDateTime heure) {
        this(taxon, nomEspece, groupe, heure, null, null, null, null);
    }

    /// Le **point qualifié par son carré** (« 640380 · A1 »), ou `null` si le contact n'a pas de point.
    ///
    /// Le schéma pose `UNIQUE(site_id, code)` : un code de point n'est unique **que dans son carré**, et
    /// presque tous les carrés ont un « Z1 ». Le proposer nu dans une liste de filtre laisserait choisir
    /// une valeur qui en désigne plusieurs (#2992, corrigé sur les autres écrans avant d'arriver ici).
    public String pointQualifie() {
        return codePoint == null ? null : numeroCarre + " · " + codePoint;
    }

    /// La **nuit biologique** du contact : sa date du soir ([Nuit#de], bascule à midi), ou `null` si le
    /// contact n'a pas d'heure. Sert de dimension de filtre « Nuit » (une nuit = un passage).
    public LocalDate nuit() {
        return heure == null ? null : Nuit.de(heure);
    }
}
