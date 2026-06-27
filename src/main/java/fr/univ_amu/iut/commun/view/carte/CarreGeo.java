package fr.univ_amu.iut.commun.view.carte;

import java.util.Objects;
import javafx.scene.paint.Color;

/// Carré (site Vigie-Chiro 2 km) à tracer sur la [CarteSites] : son numéro, son [EmpriseCarre] et une
/// couleur de remplissage (décidée par l'appelant, p. ex. selon la densité de passages — tableau de bord).
/// Donnée de **présentation** (couche `view`).
///
/// @param numeroCarre numéro à 6 chiffres (jamais nul ; sert de libellé accessible du tracé)
/// @param emprise emprise géographique du carré (jamais nulle)
/// @param remplissage couleur de remplissage du tracé (jamais nulle ; opacité à la charge de l'appelant)
public record CarreGeo(String numeroCarre, EmpriseCarre emprise, Color remplissage) {

    public CarreGeo {
        Objects.requireNonNull(numeroCarre, "numeroCarre");
        Objects.requireNonNull(emprise, "emprise");
        Objects.requireNonNull(remplissage, "remplissage");
    }

    /// Code département (2 premiers chiffres du numéro de carré, cf. brief Glossaire métier), ou chaîne
    /// vide si le numéro est trop court. Seule information géographique dérivable du numéro.
    public String departement() {
        return numeroCarre.length() >= 2 ? numeroCarre.substring(0, 2) : "";
    }
}
