package fr.univ_amu.iut.commun.model;

import java.util.Map;

/// Traduit les **clés du référentiel d'activité** en texte lisible.
///
/// Le référentiel écrit ses déclinaisons sans accents ni apostrophes : `Provence-Alpes-Cote dAzur`,
/// `Foret`, `Riviere`. [RegionsFrancaises] le dit depuis l'origine - « ce sont des clés de jointure,
/// pas du texte d'affichage : les corriger orthographiquement les rendrait introuvables » - mais la
/// couche d'affichage correspondante n'avait jamais été écrite, et les clés remontaient telles quelles
/// à l'écran, dans l'export CSV et dans la sortie JSON.
///
/// Ce n'est resté invisible que par chance : les régions dont le nom ne porte ni accent ni apostrophe
/// (`Corse`, `Occitanie`, `Bretagne`) se lisent très bien. Le défaut s'est vu le jour où l'aperçu de
/// démonstration est passé en Provence, et affichait « region Provence-Alpes-Cote dAzur » (#3049).
///
/// **La clé n'est pas modifiée** : c'est elle qui joint la donnée. Seul l'affichage change, et il
/// change au dernier moment.
///
/// Une clé inconnue est rendue **telle quelle** plutôt que remplacée par un texte d'erreur : le
/// référentiel peut gagner une déclinaison sans que cette table le sache, et un libellé imparfait vaut
/// mieux qu'une case vide ou qu'un « ? ».
public final class LibellesReferentiel {

    private static final Map<String, String> REGIONS = Map.ofEntries(
            Map.entry("Auvergne-Rhone-Alpes", "Auvergne-Rhône-Alpes"),
            Map.entry("Bourgogne-Franche-Comte", "Bourgogne-Franche-Comté"),
            Map.entry("Bretagne", "Bretagne"),
            Map.entry("Centre-Val de Loire", "Centre-Val de Loire"),
            Map.entry("Corse", "Corse"),
            Map.entry("Grand-Est", "Grand Est"),
            Map.entry("Hauts-de-France", "Hauts-de-France"),
            Map.entry("Ile-de-France", "Île-de-France"),
            Map.entry("Normandie", "Normandie"),
            Map.entry("Nouvelle Aquitaine", "Nouvelle-Aquitaine"),
            Map.entry("Occitanie", "Occitanie"),
            Map.entry("Pays de la Loire", "Pays de la Loire"),
            Map.entry("Provence-Alpes-Cote dAzur", "Provence-Alpes-Côte d'Azur"));

    /// Les milieux composés (`Agricole-Foret`) désignent une **mosaïque** de deux milieux, d'où le
    /// « et » plutôt qu'un trait d'union qui se lirait comme un nom propre.
    private static final Map<String, String> MILIEUX = Map.of(
            "Agricole", "Agricole",
            "Agricole-Foret", "Agricole et forêt",
            "Agricole-Urbain", "Agricole et urbain",
            "Foret", "Forêt",
            "Foret-Urbain", "Forêt et urbain",
            "Riviere", "Rivière",
            "Urbain", "Urbain");

    private LibellesReferentiel() {}

    /// Le nom lisible d'une région, depuis sa clé de référentiel.
    public static String region(String cle) {
        return cle == null ? null : REGIONS.getOrDefault(cle, cle);
    }

    /// Le nom lisible d'un milieu, depuis sa clé de référentiel.
    public static String milieu(String cle) {
        return cle == null ? null : MILIEUX.getOrDefault(cle, cle);
    }

    /// Les clés de région que cette table sait traduire.
    ///
    /// Exposé pour la **garde de complétude** : le repli « clé inconnue rendue telle quelle » est un bon
    /// comportement en production et un mauvais signal en test, puisqu'il rend toute vérification
    /// silencieuse. Sans ce jeu de clés, un test ne peut pas distinguer « traduit » de « recopié ».
    public static java.util.Set<String> clesRegions() {
        return REGIONS.keySet();
    }

    /// Les clés de milieu que cette table sait traduire, pour la même garde.
    public static java.util.Set<String> clesMilieux() {
        return MILIEUX.keySet();
    }
}
