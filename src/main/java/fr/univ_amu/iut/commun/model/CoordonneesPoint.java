package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// Port socle de **localisation d'un point d'écoute** : à partir de l'identifiant d'un point,
/// renvoie ses coordonnées GPS si elles sont connues.
///
/// La donnée géographique appartient à la feature `sites` (qui possède les points), mais d'autres
/// features en ont besoin sans pouvoir en dépendre : `sites` dépend déjà de `passage` (elle compte
/// les passages d'un site), donc une dépendance `passage → sites` fermerait un cycle. Ce port,
/// hébergé dans `commun`, réalise l'**inversion de dépendance** : `sites` fournit l'implémentation,
/// `passage` ne connaît que le contrat. Consommé par la feature `passage` (#547) pour le
/// pré-remplissage météo au GPS du point.
///
/// L'implémentation est **jamais bloquante** et **tolérante** : un identifiant inconnu ou un point
/// sans coordonnées renvoie [Optional#empty()].
@FunctionalInterface
public interface CoordonneesPoint {

    /// Coordonnées du point d'identifiant `idPoint`, ou vide si le point est inconnu ou non
    /// géolocalisé (y compris `idPoint` null).
    Optional<PositionGeo> pour(Long idPoint);
}
