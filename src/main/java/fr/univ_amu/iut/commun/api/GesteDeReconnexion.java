package fr.univ_amu.iut.commun.api;

/// Ce qu'un écran dit quand la plateforme ne reconnaît plus le jeton : le geste qui répare, et où le
/// faire (#6151).
///
/// Une seule phrase, parce que trois gestes la portent (la récupération des sites, la vérification
/// d'un carré, la publication d'un point) et qu'une phrase recopiée finit par nommer une entrée de
/// menu qui a changé de nom. Elle nomme l'entrée que les infobulles de blocage nomment
/// déjà.
///
/// **Réservée aux écrans.** La ligne de commande n'a pas de menu : ses messages disent seulement
/// « reconnectez-vous ».
public final class GesteDeReconnexion {

    /// « Reconnectez-vous depuis le menu principal, entrée « Se connecter à Vigie-Chiro… ». »
    public static final String A_L_ECRAN =
            "Reconnectez-vous depuis le menu principal, entrée « Se connecter à Vigie-Chiro… ».";

    private GesteDeReconnexion() {}
}
