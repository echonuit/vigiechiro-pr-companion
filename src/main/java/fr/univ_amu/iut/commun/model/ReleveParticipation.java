package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import java.util.Map;

/// Ce que la plateforme portait pour une participation à **notre dernière lecture** (#4706).
///
/// Il sert de **base** : constater un conflit demande la base, notre valeur et la leur, et sans lui
/// une modification faite ici ne se distingue pas d'une modification faite là-bas.
///
/// **Il ne dit pas ce qui est vrai, il dit ce que nous avions vu.** La vérité reste côté serveur, et
/// ce relevé ne se montre jamais à l'utilisateur comme une donnée.
///
/// @param passageId le passage local dont la participation a été relue
/// @param participationId l'`_id` de la participation relevée, pour la traçabilité
/// @param dateDebut début de nuit tel que la plateforme le portait, ou `null`
/// @param dateFin fin de nuit tel que la plateforme la portait, ou `null`
/// @param meteo bloc météo distant, ou `null` s'il était absent
/// @param configuration dictionnaire matériel distant, jamais `null` (vide si absent)
/// @param releveLe horodatage ISO de notre lecture
public record ReleveParticipation(
        Long passageId,
        String participationId,
        String dateDebut,
        String dateFin,
        MeteoDepot meteo,
        Map<String, String> configuration,
        String releveLe) {}
