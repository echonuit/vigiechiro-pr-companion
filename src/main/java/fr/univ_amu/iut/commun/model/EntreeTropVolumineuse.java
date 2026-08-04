package fr.univ_amu.iut.commun.model;

/// Une **entrée externe** dépasse le plafond sous lequel on acceptait de la lire (#3222) : journal de
/// carte SD, corps de réponse réseau.
///
/// Refus nommé plutôt que [RegleMetierException] générique, parce qu'un appelant a besoin de le
/// **distinguer** : le transport HTTP le traduit en [fr.univ_amu.iut.commun.api.ReponseApi] refusée,
/// donc définitive, là où il traite toute autre défaillance en panne réseau, donc rejouable.
/// Réémettre une réponse trop grosse la redonnerait trop grosse.
public class EntreeTropVolumineuse extends RegleMetierException {

    private static final long serialVersionUID = 1L;

    public EntreeTropVolumineuse(String message) {
        super(message);
    }
}
