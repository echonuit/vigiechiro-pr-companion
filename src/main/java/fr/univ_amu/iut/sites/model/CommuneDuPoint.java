package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.Commune;
import java.util.Objects;

/// Une ligne de la table latérale `point_commune` (#2791) : la [Commune] résolue d'un point
/// d'écoute. L'absence de ligne dit « commune non résolue » - le cas d'un point sans GPS, d'une
/// création hors ligne ou d'un point hors du référentiel (cf. ADR 2791).
public record CommuneDuPoint(Long idPoint, Commune commune) {

    public CommuneDuPoint {
        Objects.requireNonNull(idPoint, "idPoint");
        Objects.requireNonNull(commune, "commune");
    }
}
