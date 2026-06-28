package fr.univ_amu.iut.validation.model;

/// Projection **inventaire par carré** (#analyse) : un carré (site) de l'utilisateur, agrégé sur les
/// observations qui s'y rattachent (selon le filtre de statut). Donne la **richesse spécifique** (nombre
/// d'espèces distinctes) — l'angle « centré lieu » du pivot espèce ↔ lieu.
///
/// L'espèce comptée est `COALESCE(taxon_observer, taxon_tadarida)`, pseudo-taxons `noise`/`piaf` exclus.
///
/// @param numeroCarre n° de carré (square_number) du site
/// @param nomSite nom convivial du site (optionnel)
/// @param richesse nombre d'espèces distinctes observées sur le carré
/// @param nbObservations nombre total de détections sur le carré
/// @param anneeMin première année d'observation
/// @param anneeMax dernière année d'observation
public record CarreEspeces(
        String numeroCarre, String nomSite, int richesse, int nbObservations, int anneeMin, int anneeMax) {}
