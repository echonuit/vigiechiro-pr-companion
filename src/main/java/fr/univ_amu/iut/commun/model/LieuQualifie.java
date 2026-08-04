package fr.univ_amu.iut.commun.model;

/// L'écriture d'un lieu **qualifié** par ce qui le désambiguïse ou le nomme : « 640380 · Vallon » pour
/// un carré et son nom convivial (#3157), « 640380 · A1 » pour un point et son carré (#2992).
///
/// ## Pourquoi ici, et pas dans la vue
///
/// Cette écriture est née côté écran, où elle sert à cocher. Elle a rejoint `commun.model` quand la
/// **ligne de commande** a dû nommer les lieux comme l'écran les montre (#3159) : un refus qui liste
/// « 640380 » là où l'écran affiche « 640380 · Vallon » propose une valeur qui ne se recopie pas.
///
/// Les deux surfaces partagent donc la règle plutôt que d'en écrire chacune une version. Le modèle est
/// le seul endroit d'où elles peuvent toutes deux la lire, une vue ne pouvant pas être dépendance d'un
/// modèle (`ArchitectureTest`).
public final class LieuQualifie {

    /// Ce qui sépare les deux étiquettes. Un point médian entouré d'espaces : il se distingue d'un tiret
    /// dans un nom de commune (« Aix-en-Provence ») et d'un point dans un code.
    public static final String SEPARATEUR = " · ";

    private LieuQualifie() {}

    /// « prefixe · suffixe », le **préfixe seul** quand le suffixe manque, et `null` quand le préfixe
    /// manque.
    ///
    /// L'asymétrie est voulue : le préfixe **identifie** le lieu (le numéro d'un carré) là où le suffixe
    /// ne fait que le nommer ou le préciser. Un suffixe orphelin ne désignerait rien.
    ///
    /// Un appelant dont le **suffixe** porte l'identité - le code d'un point, « A1 » dans
    /// « 640380 · A1 » - garde donc sa propre garde avant d'appeler : sans code de point, il n'y a pas
    /// de point, et rendre le carré seul le ferait passer pour un.
    public static String qualifier(String prefixe, String suffixe) {
        if (prefixe == null) {
            return null;
        }
        return suffixe == null || suffixe.isBlank() ? prefixe : prefixe + SEPARATEUR + suffixe;
    }
}
