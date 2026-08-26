package fr.univ_amu.iut.commun.api;

/// Ce qu'un refus de la plateforme demande de faire, quand son statut ne suffit pas à le dire (#4524).
///
/// Deux `422` de sens opposé circulent à l'écriture, et le geste à faire diffère : l'un se corrige
/// chez nous, l'autre pas du tout. Mesuré le 2026-08-26, avec son témoin.
public enum MotifDeRefus {

    /// Nous avons envoyé un champ que le schéma distant ne connaît pas : `422 « invalid field »`.
    ///
    /// C'est **notre** correspondance qui est fautive, et elle se corrige ici. Le piège documenté du
    /// champ `numero` est de cette famille.
    CHAMP_INCONNU,

    /// Le champ existe, mais la plateforme ne nous laisse pas l'écrire : `422 « field is read-only »`.
    ///
    /// Notre correspondance est juste, c'est le droit qui manque, et rien ne se corrige côté client.
    /// C'est aussi le seul des deux qui puisse apparaître **sans que rien n'ait changé chez nous**,
    /// le jour où la plateforme verrouille un champ qu'elle acceptait.
    CHAMP_FERME,

    /// Tout le reste, et ce n'est pas un fourre-tout : c'est un refus de ranger.
    ///
    /// Un classement binaire mentirait dès le premier refus inédit, et il mentirait dans le sens
    /// rassurant en désignant un geste précis qui ne conviendrait pas.
    AUTRE
}
