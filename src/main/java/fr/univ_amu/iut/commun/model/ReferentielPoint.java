package fr.univ_amu.iut.commun.model;

import java.util.Optional;

/// **Port** (inversion de dépendance `passage`/`lot` → `sites`) donnant l'identité VigieChiro d'un point
/// d'écoute — son code de localité et l'id de son site — **sans dépendre de la feature `sites`** (le cycle
/// `passage ↔ sites` est interdit par ArchUnit). Calqué sur [CoordonneesPoint] : les features consommatrices
/// posent un défaut no-op, l'implémentation réelle (lecture via `PointDao`) est fournie par `sites`.
@FunctionalInterface
public interface ReferentielPoint {

    /// [InfosPoint] (code de localité + id du site) du point `idPoint`, ou vide si inconnu / no-op.
    Optional<InfosPoint> pour(Long idPoint);
}
