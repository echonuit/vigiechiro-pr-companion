package fr.univ_amu.iut.passage.model;

/// Accès typé à la **donnée météo optionnelle** d'un passage (#106), stockée dans la colonne
/// `passage.weather_data` ([Passage#donneesMeteo]).
///
/// Pour l'instant cette colonne ne porte qu'une valeur : la **température en début de nuit** (°C),
/// **optionnelle**. On la sérialise comme un simple **nombre JSON** (ex. `8.5`), ce qui reste un JSON
/// valide (la colonne est documentée « données météo en JSON ») et évite d'ajouter un champ au record
/// [Passage] (et son lot d'appelants). `null` signifie « non renseignée » : jamais bloquant.
///
/// Objet utilitaire pur (aucune dépendance JavaFX ni JDBC) : la mise en forme d'affichage (unité,
/// virgule décimale) relève de la couche `viewmodel`.
public final class MeteoPassage {

    private MeteoPassage() {}

    /// Température en début de nuit (°C) lue depuis `donneesMeteo`, ou `null` si la colonne est vide ou
    /// illisible (on ne lève jamais : une donnée optionnelle absente ne doit pas casser l'affichage).
    public static Double temperatureDebutNuit(String donneesMeteo) {
        if (donneesMeteo == null || donneesMeteo.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(donneesMeteo.trim().replace(',', '.'));
        } catch (NumberFormatException illisible) {
            return null;
        }
    }

    /// Sérialise une température (°C) pour la colonne `weather_data`, ou `null` pour l'effacer. Format
    /// **indépendant de la locale** (point décimal), pour un stockage déterministe.
    public static String serialiser(Double temperatureDebutNuit) {
        return temperatureDebutNuit == null ? null : Double.toString(temperatureDebutNuit);
    }

    /// Lit une **saisie utilisateur** de température : vide → `null` (efface la donnée optionnelle) ;
    /// sinon nombre (virgule ou point acceptés). **Lève** `NumberFormatException` si la saisie n'est pas
    /// un nombre, pour que l'IHM puisse signaler l'erreur (contrairement à [#temperatureDebutNuit] qui,
    /// elle, est tolérante en lecture de la base).
    public static Double lireSaisie(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        return Double.valueOf(saisie.trim().replace(',', '.'));
    }
}
