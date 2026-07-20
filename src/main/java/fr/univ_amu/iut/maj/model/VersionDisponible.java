package fr.univ_amu.iut.maj.model;

import java.util.Objects;

/// Une version publiée en amont, telle que le port [DerniereVersionPubliee] la rapporte (#2109).
///
/// Porte l'adresse de la page de publication en plus du numéro : la notification ne sert à rien si
/// elle ne dit pas où aller, et c'est l'amont qui sait où sa version se télécharge.
public record VersionDisponible(NumeroDeVersion numero, String adresse) {

    public VersionDisponible {
        Objects.requireNonNull(numero, "numero");
        if (adresse == null || adresse.isBlank()) {
            throw new IllegalArgumentException("adresse de publication vide");
        }
    }
}
