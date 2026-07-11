package fr.univ_amu.iut.lot.model;

/// Unité de dépôt persistée (table `depot_unite`, #981) : l'avancement du téléversement d'**un**
/// fichier (archive ZIP ou séquence WAV) d'un passage vers VigieChiro. C'est le grain de la
/// **reprise** (#982) : un dépôt interrompu ne re-téléverse que les unités non confirmées.
///
/// @param id clé technique (`null` avant insertion)
/// @param passageId passage déposé (FK `passage.id`, suppression en cascade)
/// @param identifiantUnite nom du fichier téléversé, unique par passage (clé de reprise)
/// @param type nature de l'unité (archive ZIP ou séquence WAV)
/// @param statut avancement du téléversement de cette unité
/// @param fichierIdDistant `objectid` du fichier créé côté plateforme, une fois connu (sinon `null`)
/// @param messageErreur raison du dernier échec (statut [StatutDepotUnite#ECHEC], sinon `null`)
/// @param majLe horodatage ISO de la dernière mise à jour de la ligne
public record DepotUnite(
        Long id,
        Long passageId,
        String identifiantUnite,
        TypeDepotUnite type,
        StatutDepotUnite statut,
        String fichierIdDistant,
        String messageErreur,
        String majLe) {

    /// Unité neuve « à déposer » du plan d'un passage (aucun téléversement entamé).
    public static DepotUnite aDeposer(Long passageId, String identifiantUnite, TypeDepotUnite type, String majLe) {
        return new DepotUnite(null, passageId, identifiantUnite, type, StatutDepotUnite.A_DEPOSER, null, null, majLe);
    }
}
