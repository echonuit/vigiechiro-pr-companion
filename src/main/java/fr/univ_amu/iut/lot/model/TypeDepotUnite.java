package fr.univ_amu.iut.lot.model;

import java.util.Locale;

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

    /// Le type d'une unité d'après son **identifiant** : `.zip` est une archive, tout le reste une
    /// séquence WAV. Le plan de dépôt, l'écran et la ligne de commande le lisent ici (#5835).
    public static TypeDepotUnite deLIdentifiant(String identifiant) {
        return identifiant.toLowerCase(Locale.ROOT).endsWith(".zip") ? ZIP : WAV;
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
