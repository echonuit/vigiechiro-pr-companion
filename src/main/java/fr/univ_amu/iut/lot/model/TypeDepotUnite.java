package fr.univ_amu.iut.lot.model;

/// Nature d'une **unité de dépôt** (table `depot_unite`, #981) : une archive [#ZIP] du lot, ou une
/// séquence [#WAV] individuelle. La `valeur` (minuscule) est la forme persistée. Le dépôt actuel
/// téléverse des WAV ; le spike ZIP (#984) tranchera si les archives peuvent être déposées telles
/// quelles.
public enum TypeDepotUnite {
    ZIP("zip"),
    WAV("wav");

    private final String valeur;

    TypeDepotUnite(String valeur) {
        this.valeur = valeur;
    }

    /// Forme persistée du type (colonne `depot_unite.type`).
    public String valeur() {
        return valeur;
    }

    public static TypeDepotUnite parValeur(String valeur) {
        for (TypeDepotUnite type : values()) {
            if (type.valeur.equals(valeur)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Type d'unité de dépôt inconnu : " + valeur);
    }
}
