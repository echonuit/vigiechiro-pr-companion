package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.Taxon;
import java.util.function.Function;
import javafx.util.StringConverter;

/// Libellés et convertisseurs des `ComboBox` de la vue audio (mode de revue, taxon). Extraits du
/// controller [SonsValidationController] (pur câblage, qui frôlait le plafond God-class) : ce sont des
/// fonctions **pures** d'affichage, sans état ni dépendance JavaFX autre que [StringConverter], donc
/// testables et réutilisables indépendamment du controller.
final class LibellesAudio {

    private LibellesAudio() {}

    /// `StringConverter` à sens unique (affichage seul) : les `ComboBox` de la vue ne sont pas éditables,
    /// la conversion inverse (texte → valeur) est donc inutile.
    static <T> StringConverter<T> converter(Function<T, String> versLibelle) {
        return new StringConverter<>() {
            @Override
            public String toString(T valeur) {
                return versLibelle.apply(valeur);
            }

            @Override
            public T fromString(String libelle) {
                return null; // ComboBox non éditables : conversion inverse inutile
            }
        };
    }

    /// « CODE (Nom vernaculaire) », ou le seul code si le nom vernaculaire est absent.
    static String taxon(Taxon taxon) {
        String nom = taxon.nomVernaculaireFr();
        return nom == null || nom.isBlank() ? taxon.code() : taxon.code() + " (" + nom + ")";
    }

    /// Libellé du mode de revue tel qu'affiché dans la liste déroulante.
    static String mode(ModeRevue mode) {
        return switch (mode) {
            case ACTIVITE -> "Activité (une par une)";
            case INVENTAIRE -> "Inventaire (propage l'espèce)";
        };
    }
}
