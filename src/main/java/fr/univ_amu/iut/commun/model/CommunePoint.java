package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// Port socle donnant la **commune** d'un point d'écoute, à partir de son identifiant (#3442).
///
/// Même montage d'inversion de dépendance que [CoordonneesPoint] : la donnée appartient à la feature
/// `sites` (qui possède les points et leur table latérale `point_commune`), mais d'autres features en
/// ont besoin sans pouvoir en dépendre - `sites` dépend déjà de `passage`, donc `passage → sites`
/// fermerait un cycle ([ADR 0004](../../../../../../dev-docs/decisions/0004-cross-feature-sans-cycle-ports-commun.md)).
/// `sites` fournit l'implémentation, les consommateurs ne connaissent que ce contrat.
///
/// ## À quoi elle sert ici
///
/// La commune est **déjà dérivée du GPS** du point (ADR 2791) et stockée à côté de lui. Son code INSEE
/// désigne un territoire sans ambiguïté, donc un **fuseau horaire** ([FuseauDuSite#pour]) : c'est par
/// ce chemin que les heures d'une nuit d'outre-mer cessent d'être lues à l'heure de la métropole.
///
/// L'implémentation est **jamais bloquante** et **tolérante** : un identifiant inconnu, un point sans
/// GPS ou une commune non encore résolue rendent [Optional#empty()]. L'appelant retombe alors sur le
/// comportement d'avant, jamais sur pire.
@FunctionalInterface
public interface CommunePoint {

    /// Commune du point d'identifiant `idPoint`, ou vide si le point est inconnu, sans coordonnées, ou
    /// si sa commune n'a pas encore été résolue (y compris `idPoint` null).
    Optional<Commune> pour(Long idPoint);
}
