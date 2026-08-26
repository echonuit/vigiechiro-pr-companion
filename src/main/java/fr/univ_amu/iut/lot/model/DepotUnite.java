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
        boolean echecDefinitif,
        String majLe) {

    /// Unité **sans refus définitif** : la forme d'avant #3469, conservée parce qu'elle dit vrai pour
    /// tout ce qui n'a pas rencontré de refus.
    ///
    /// Ce n'est pas un défaut implicite qu'on aurait glissé sous le tapis : un appelant qui veut
    /// consigner un refus définitif passe par le constructeur canonique, et le dit. Celui-ci existe
    /// pour que les constructions qui n'ont **rien** à en dire n'aient pas à répondre à la question.
    public DepotUnite(
            Long id,
            Long passageId,
            String identifiantUnite,
            TypeDepotUnite type,
            StatutDepotUnite statut,
            String fichierIdDistant,
            String messageErreur,
            String majLe) {
        this(id, passageId, identifiantUnite, type, statut, fichierIdDistant, messageErreur, false, majLe);
    }

    /// Unité neuve « à déposer » du plan d'un passage (aucun téléversement entamé).
    public static DepotUnite aDeposer(Long passageId, String identifiantUnite, TypeDepotUnite type, String majLe) {
        return new DepotUnite(
                null, passageId, identifiantUnite, type, StatutDepotUnite.A_DEPOSER, null, null, false, majLe);
    }

    /// Vrai quand cette unité a échoué sur un refus que **retenter ne lèvera pas** (#3469).
    ///
    /// À ne pas confondre avec « le statut vaut `ECHEC` » : un incident réseau échoue aussi, et
    /// celui-là mérite qu'on repropose la reprise. C'est la distinction que
    /// `ReponseApi.estReessayable()` porte le temps d'un appel, et que le plan perdait ensuite.
    public boolean refuseDefinitivement() {
        return statut() == StatutDepotUnite.ECHEC && echecDefinitif;
    }
}
