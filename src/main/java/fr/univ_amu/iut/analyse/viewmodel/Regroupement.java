package fr.univ_amu.iut.analyse.viewmodel;

/// Axe de regroupement de l'inventaire « Espèces & observations » (pivot espèce ↔ lieu) :
/// soit une ligne **par espèce**, soit une ligne **par carré** (richesse spécifique).
public enum Regroupement {
    /// Une ligne par espèce : où / quand / combien.
    PAR_ESPECE("Par espèce"),
    /// Une ligne par carré : richesse spécifique (nb d'espèces) et total de détections.
    PAR_CARRE("Par carré");

    private final String libelle;

    Regroupement(String libelle) {
        this.libelle = libelle;
    }

    /// Libellé affiché dans le sélecteur de regroupement.
    public String libelle() {
        return libelle;
    }
}
