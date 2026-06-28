package fr.univ_amu.iut.multisite.view;

import java.util.function.Function;
import javafx.util.StringConverter;

/// Fabriques de [StringConverter] pour les listes (ComboBox) de l'écran multisite. Extraites de
/// [MultisiteController] pour garder ce dernier sous le seuil PMD `NcssCount`.
final class Convertisseurs {

    private Convertisseurs() {}

    /// Converter d'affichage seul (liste **non éditable**) : `toString` délègue à `versTexte`, la
    /// conversion inverse est inutile (aucune saisie libre).
    static <T> StringConverter<T> parLibelle(Function<T, String> versTexte) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versTexte.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // ComboBox non éditable : conversion inverse inutile
            }
        };
    }
}
