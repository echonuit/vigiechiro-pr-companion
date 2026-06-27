package fr.univ_amu.iut.multisite.model;

import java.util.List;

/// Agrégat d'un **carré** (site Vigie-Chiro) pour la carte multisite (#152) : son numéro, son nom
/// convivial, ses points agrégés et le nombre total de passages. Donnée **domaine** (pas d'IHM) ; la
/// couche `view` en déduit l'emprise (via un fournisseur) et les couleurs.
///
/// @param numeroCarre numéro à 6 chiffres du carré
/// @param nomConvivial nom convivial du site (peut être `null`)
/// @param points points d'écoute agrégés du carré (liste immuable, possiblement vide)
/// @param nombrePassages nombre total de passages du carré
public record CarreAgrege(String numeroCarre, String nomConvivial, List<PointAgrege> points, int nombrePassages) {

    public CarreAgrege {
        points = List.copyOf(points);
    }
}
