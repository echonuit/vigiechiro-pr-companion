package fr.univ_amu.iut.multisite.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;

/// Agrégat d'un **point d'écoute** pour la carte multisite (#152) : son code, ses coordonnées (nullables,
/// le GPS étant optionnel et souvent absent), le nombre de passages et le statut workflow **dominant**
/// (celui du passage le plus récent). Donnée **domaine** (pas d'IHM) : la couche `view` la traduit en
/// marqueur coloré.
///
/// @param codePoint code du point (« A1 », « Z4 »…)
/// @param latitude latitude WGS84, ou `null` si non renseignée
/// @param longitude longitude WGS84, ou `null` si non renseignée
/// @param nombrePassages nombre de passages rattachés au point
/// @param statutDominant statut du passage le plus récent, ou `null` si aucun passage
public record PointAgrege(
        String codePoint, Double latitude, Double longitude, int nombrePassages, StatutWorkflow statutDominant) {

    /// `true` si le point a des coordonnées exploitables (sinon il ne peut pas être placé sur la carte).
    public boolean estGeolocalise() {
        return latitude != null && longitude != null;
    }
}
