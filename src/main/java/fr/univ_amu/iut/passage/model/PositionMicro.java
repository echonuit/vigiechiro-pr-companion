package fr.univ_amu.iut.passage.model;

import java.util.Locale;

/// Position de fixation du micro pour un passage (demandée au dépôt VigieChiro) : au **sol** ou en
/// **canopée**. Domaine fermé à deux valeurs → énum (contrairement au niveau taxonomique, laissé libre).
///
/// Persistée sous son [#name] (`SOL` / `CANOPEE`) dans `passage_equipment.mic_position` ; la relecture
/// est **tolérante** ([#depuisTexte] : valeur absente ou inconnue → `null`, jamais d'exception).
public enum PositionMicro {
    SOL("Sol"),
    CANOPEE("Canopée");

    private final String libelle;

    PositionMicro(String libelle) {
        this.libelle = libelle;
    }

    /// Libellé lisible pour l'IHM (ex. « Canopée »).
    public String libelle() {
        return libelle;
    }

    /// Lit une position depuis un texte stocké/saisi : `null` ou vide → `null` ; sinon la constante
    /// correspondante (insensible à la casse), ou `null` si le texte ne correspond à aucune (tolérant).
    public static PositionMicro depuisTexte(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return valueOf(texte.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException inconnue) {
            return null;
        }
    }
}
