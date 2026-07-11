package fr.univ_amu.iut.lot.model;

/// Statut d'une **unité de dépôt** (table `depot_unite`, #981) : une archive ZIP ou une séquence WAV
/// à téléverser vers VigieChiro. La `valeur` (minuscule, sans accent) est la forme persistée.
///
/// Une unité laissée [#EN_COURS] par une interruption est **à re-tenter** : son téléversement n'a
/// jamais été confirmé (le moteur reprenable #982 la traite comme [#A_DEPOSER]).
public enum StatutDepotUnite {
    A_DEPOSER("a_deposer"),
    EN_COURS("en_cours"),
    DEPOSE("depose"),
    ECHEC("echec");

    private final String valeur;

    StatutDepotUnite(String valeur) {
        this.valeur = valeur;
    }

    /// Forme persistée du statut (colonne `depot_unite.statut`).
    public String valeur() {
        return valeur;
    }

    public static StatutDepotUnite parValeur(String valeur) {
        for (StatutDepotUnite statut : values()) {
            if (statut.valeur.equals(valeur)) {
                return statut;
            }
        }
        throw new IllegalArgumentException("Statut d'unité de dépôt inconnu : " + valeur);
    }
}
