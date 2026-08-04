package fr.univ_amu.iut.commun.view.carte;

import fr.univ_amu.iut.commun.model.RegionDuCarre;
import java.util.Objects;
import javafx.scene.paint.Color;

/// Carré (site Vigie-Chiro 2 km) à tracer sur la [CarteSites] : son numéro, son [EmpriseCarre] et une
/// couleur de remplissage (décidée par l'appelant, p. ex. selon la densité de passages : tableau de bord).
/// Donnée de **présentation** (couche `view`).
///
/// @param numeroCarre numéro à 6 chiffres (jamais nul ; sert de libellé accessible du tracé)
/// @param emprise emprise géographique du carré (jamais nulle)
/// @param remplissage couleur de remplissage du tracé (jamais nulle ; opacité à la charge de l'appelant)
/// @param infobulle mini-stats affichées au survol (et exposées en `accessibleHelp`, #163) ; `null` = aucune
public record CarreGeo(String numeroCarre, EmpriseCarre emprise, Color remplissage, String infobulle) {

    public CarreGeo {
        Objects.requireNonNull(numeroCarre, "numeroCarre");
        Objects.requireNonNull(emprise, "emprise");
        Objects.requireNonNull(remplissage, "remplissage");
    }

    /// Tracé sans info-bulle (forme rétro-compatible).
    public CarreGeo(String numeroCarre, EmpriseCarre emprise, Color remplissage) {
        this(numeroCarre, emprise, remplissage, null);
    }

    /// Code département (2 premiers chiffres du numéro de carré, cf. brief Glossaire métier), ou chaîne
    /// vide si le numéro est trop court. Seule information géographique dérivable du numéro.
    ///
    /// La règle elle-même vit dans [RegionDuCarre#departement] (#2848) : elle s'écrivait ici, dans
    /// [fr.univ_amu.iut.sites.viewmodel.SiteDetailViewModel] et dans la classe qui porte son ADR, et
    /// trois copies d'une même règle finissent par diverger. Seule la **marque d'absence** reste propre
    /// à ce record : la chaîne vide, sa forme historique.
    public String departement() {
        return RegionDuCarre.departement(numeroCarre).orElse("");
    }
}
