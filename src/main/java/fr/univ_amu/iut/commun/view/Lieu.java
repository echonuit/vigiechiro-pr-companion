package fr.univ_amu.iut.commun.view;

import java.util.Objects;

/// Un segment du **fil d'Ariane** : un libellé affiché et, pour un ancêtre cliquable, l'action qui
/// ouvre l'écran correspondant.
///
/// Produit par les écrans via [EmplacementNavigation], ou par le chrome à partir de l'historique de
/// navigation (repli). Le dernier segment (l'écran courant) a une action `ouvrir` nulle : il n'est pas
/// cliquable ; les ancêtres portent une action de navigation avant.
///
/// @param libelle texte affiché du segment (ex. « Mes sites », « Carré 640380 »)
/// @param ouvrir action exécutée au clic (ancêtre), ou `null` pour le segment courant (non cliquable)
public record Lieu(String libelle, Runnable ouvrir) {

    public Lieu {
        Objects.requireNonNull(libelle, "libelle");
    }

    /// Segment terminal : l'écran courant, non cliquable.
    public static Lieu courant(String libelle) {
        return new Lieu(libelle, null);
    }

    /// Segment ancêtre cliquable : son clic exécute `ouvrir`.
    public static Lieu vers(String libelle, Runnable ouvrir) {
        return new Lieu(libelle, Objects.requireNonNull(ouvrir, "ouvrir"));
    }

    /// Vrai si le segment est cliquable (un ancêtre).
    public boolean estCliquable() {
        return ouvrir != null;
    }
}
