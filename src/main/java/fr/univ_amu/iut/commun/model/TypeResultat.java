package fr.univ_amu.iut.commun.model;

/// Nature d'un [ResultatRecherche] de la recherche globale (#144) : sert à grouper les résultats dans
/// la liste déroulante et à décider de l'écran cible lors de la navigation.
public enum TypeResultat {
    /// Un site (carré) de l'utilisateur.
    SITE("Sites"),
    /// Un point d'écoute d'un site.
    POINT("Points"),
    /// Un passage (nuit de capture) sur un point.
    PASSAGE("Passages");

    private final String libellePluriel;

    TypeResultat(String libellePluriel) {
        this.libellePluriel = libellePluriel;
    }

    /// Libellé d'en-tête de groupe (pluriel) affiché au-dessus des résultats de ce type.
    public String libellePluriel() {
        return libellePluriel;
    }
}
