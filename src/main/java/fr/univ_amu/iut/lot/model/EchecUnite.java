package fr.univ_amu.iut.lot.model;

/// Une unité de dépôt **en échec**, avec ce qui permet de savoir si elle repartira (#3962).
///
/// ## Pourquoi ce type existe
///
/// `BilanDepot.echecs` était une `List<String>` : rien que des noms. Le dépôt jetait donc `definitif`
/// et `cause` **à l'endroit exact où il les tenait** - `TeleverseurArchive.Resultat` les porte depuis
/// #3688 et #3689, et `DepotVigieChiro` n'en gardait que l'identifiant.
///
/// Conséquence vécue : le compte rendu annonçait « « Reprendre le dépôt » ne renverra que celles-là »
/// pour des archives que la reprise ne reprendrait jamais, et la CLI disait « relancez la commande »
/// dans le même cas. Un conseil que le produit ne peut pas tenir, ce que l'ADR 3854 proscrit.
///
/// C'est la **même forme** que le défaut de #3688, où le PUT S3 d'un seul bloc perdait le statut de
/// sa réponse. Corrigé une fois en amont, le motif survivait en aval : c'est pourquoi le remède est un
/// type qui porte l'information, et non deux phrases rendues prudentes.
///
/// @param identifiantUnite le nom de l'archive ou de la séquence, tel que la table le montre
/// @param raison ce qui a été rapporté, pour l'infobulle et le journal
/// @param definitif `true` si Vigie-Chiro a **refusé** : la renvoyer telle quelle serait refusé de même
/// @param cause ce qui pourrait lever un refus définitif, ou `null` quand l'échec est rejouable
public record EchecUnite(String identifiantUnite, String raison, boolean definitif, CauseRefus cause) {

    /// Un échec **rejouable** : coupure, lenteur, incident inattendu. La reprise le reprendra.
    public static EchecUnite rejouable(String identifiantUnite, String raison) {
        return new EchecUnite(identifiantUnite, raison, false, null);
    }

    /// `true` si une **reconnexion réussie** peut rendre cette unité reprenable.
    ///
    /// C'est la seule question que le compte rendu ait le droit de poser avant de conseiller un geste :
    /// nommer « reconnectez-vous » devant un contenu refusé serait conseiller à côté de la cause.
    public boolean seRearmeParUneReconnexion() {
        return definitif && cause == CauseRefus.AUTHENTIFICATION;
    }
}
