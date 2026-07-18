package fr.univ_amu.iut.commun.api;

/// Bloc **météo** d'une participation (#142) : les champs que l'API VigieChiro conserve sous `meteo`.
/// Les valeurs suivent les énumérations du backend (déjà celles de l'app, #702) : `vent` ∈
/// `NUL | FAIBLE | MOYEN | FORT`, `couverture` ∈ `0-25 | 25-50 | 50-75 | 75-100`. Sérialisé tel quel
/// (clés `vent` / `couverture` / `temperature_debut` / `temperature_fin`) par [RequetesVigieChiro].
///
/// **Températures** (#1844) : le schéma serveur les porte depuis toujours (`meteo.temperature_debut`,
/// `meteo.temperature_fin`) ; l'application, elle, ne les transportait pas - saisies localement, elles
/// n'arrivaient jamais sur la fiche web. Elles sont typées **`integer`** côté serveur : un relevé
/// décimal doit être **arrondi** avant l'envoi, sous peine de refus.
///
/// @param vent code de force du vent (peut être `null` si non renseigné)
/// @param couverture tranche de couverture nuageuse (peut être `null` si non renseignée)
/// @param temperatureDebut température de début de nuit, en °C **entiers** (`null` si non renseignée)
/// @param temperatureFin température de fin de nuit, en °C **entiers** (`null` si non renseignée)
public record MeteoDepot(String vent, String couverture, Integer temperatureDebut, Integer temperatureFin) {

    /// Bloc météo sans température : conserve les appels antérieurs à #1844 (et les cas où seules les
    /// appréciations vent/couverture sont connues).
    public MeteoDepot(String vent, String couverture) {
        this(vent, couverture, null, null);
    }
}
