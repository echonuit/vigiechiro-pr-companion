package fr.univ_amu.iut.passage.model;

/// Indice **non bloquant** de concordance acoustique d'une réactivation par hydratation (#1682, EPIC
/// #1653) : parmi les séquences dont les cris étaient **mesurables**, combien présentent bien les cris
/// attendus dans l'audio régénéré.
///
/// Il est **rapporté par transparence**, pas utilisé pour décider : l'audio régénéré est un extrait
/// **verbatim** du brut désigné (transformation déterministe, copie PCM sans rééchantillonnage), donc
/// correct par construction ; et le détecteur acoustique (filtre de Goertzel) produit des faux négatifs
/// sur des cris réels faibles. Refuser une séquence sur cette base écarterait le bon audio. L'indice sert
/// donc à **informer** l'utilisateur du degré de concordance, sans jamais bloquer un rebranchement.
///
/// @param mesurees séquences rebranchées dont au moins un cri était mesurable (fenêtre et bande valides)
/// @param concordantes parmi elles, celles où les cris attendus ont été retrouvés (majorité présente)
public record IndiceAcoustique(int mesurees, int concordantes) {

    /// `true` s'il y a au moins une séquence mesurée : sinon il n'y a rien à afficher.
    public boolean estRenseigne() {
        return mesurees > 0;
    }
}
