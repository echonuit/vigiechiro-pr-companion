package fr.univ_amu.iut.passage.model;

/// Relevé météo **optionnel** d'un passage, tel que demandé au dépôt VigieChiro : les quatre grandeurs
/// vivent dans la colonne JSON `passage.weather_data` (cf. [MeteoPassage], qui préserve les autres clés).
///
/// Chaque champ est **indépendamment optionnel** (`null` = non renseigné) : un relevé partiel est normal
/// (p. ex. seule la température de début de nuit, héritage #106). Objet de données pur (aucune dépendance
/// JavaFX/JDBC) ; la mise en forme d'affichage (unités, virgule décimale) relève du `viewmodel`.
///
/// @param temperatureDebutNuit température en début de nuit (°C), ou `null`
/// @param temperatureFinNuit température en fin de nuit (°C), ou `null`
/// @param vent vent (vitesse en km/h, convention Open-Meteo), ou `null`
/// @param couvertureNuageuse couverture nuageuse (%, 0 à 100), ou `null`
public record MeteoReleve(
        Double temperatureDebutNuit, Double temperatureFinNuit, Double vent, Double couvertureNuageuse) {

    /// Relevé entièrement vide (toutes grandeurs `null`) : aucun champ renseigné.
    public static final MeteoReleve VIDE = new MeteoReleve(null, null, null, null);

    /// `true` si aucune grandeur n'est renseignée (utile pour décider d'effacer la colonne).
    public boolean estVide() {
        return temperatureDebutNuit == null && temperatureFinNuit == null && vent == null && couvertureNuageuse == null;
    }
}
