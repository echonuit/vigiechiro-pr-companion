package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.view.DescripteurReglage.Enumeration.Option;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import java.util.Optional;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/// Fabrique de contrôles d'IHM à partir des [DescripteurReglage] **déclaratifs** d'un [OngletReglages],
/// chacun câblé à [ReglagesReactifs] (donc persisté et synchronisé en direct). Construction pure, sans
/// état, extraite de [EcranReglagesController] pour le garder mince (analogue de [CartesAccueil]).
///
/// Le choix du contrôle est un `switch` **exhaustif** sur le type scellé (dispatch de rendu dans la
/// couche vue) : garder cette correspondance donnée -> contrôle ici, plutôt que sur les records,
/// évite de coupler les descripteurs (données pures) à `javafx.scene.control`.
final class ControleursReglages {

    private ControleursReglages() {}

    /// Formulaire d'un onglet : un contrôle par réglage déclaratif (dans l'ordre déclaré), suivi du
    /// nœud personnalisé de l'échappatoire [OngletReglagesPersonnalise] le cas échéant.
    static Region formulaire(OngletReglages onglet, ReglagesReactifs reactifs) {
        VBox boite = new VBox();
        boite.getStyleClass().add("formulaire-reglages");
        for (DescripteurReglage descripteur : onglet.reglages()) {
            boite.getChildren().add(ligne(descripteur, reactifs));
        }
        if (onglet instanceof OngletReglagesPersonnalise personnalise) {
            boite.getChildren().add(personnalise.formulairePersonnalise());
        }
        return boite;
    }

    /// Une ligne de réglage : la case à cocher porte déjà son libellé ; les autres contrôles sont
    /// précédés d'un libellé sur une ligne dédiée.
    private static Node ligne(DescripteurReglage descripteur, ReglagesReactifs reactifs) {
        Control controle = controle(descripteur, reactifs);
        if (descripteur instanceof DescripteurReglage.Booleen) {
            return controle;
        }
        Label libelle = new Label(descripteur.libelle());
        libelle.getStyleClass().add("reglage-libelle");
        VBox ligne = new VBox(libelle, controle);
        ligne.getStyleClass().add("reglage-ligne");
        return ligne;
    }

    /// Contrôle câblé (bidirectionnel) au réglage décrit, avec tooltip d'aide si fournie.
    static Control controle(DescripteurReglage descripteur, ReglagesReactifs reactifs) {
        Control controle =
                switch (descripteur) {
                    case DescripteurReglage.Booleen booleen -> {
                        CheckBox coche = new CheckBox(booleen.libelle());
                        coche.selectedProperty()
                                .bindBidirectional(reactifs.proprieteBooleen(booleen.cle(), booleen.defaut()));
                        yield coche;
                    }
                    case DescripteurReglage.Texte texte -> {
                        TextField champ = new TextField();
                        champ.textProperty().bindBidirectional(reactifs.proprieteTexte(texte.cle(), texte.defaut()));
                        yield champ;
                    }
                    case DescripteurReglage.Entier entier -> spinner(entier, reactifs);
                    case DescripteurReglage.Enumeration enumeration -> liste(enumeration, reactifs);
                };
        if (!descripteur.aide().isBlank()) {
            controle.setTooltip(new Tooltip(descripteur.aide()));
        }
        controle.getStyleClass().add("reglage-controle");
        return controle;
    }

    private static Spinner<Integer> spinner(DescripteurReglage.Entier entier, ReglagesReactifs reactifs) {
        IntegerProperty valeur = reactifs.proprieteEntier(entier.cle(), entier.defaut());
        Spinner<Integer> spinner = new Spinner<>(entier.min(), entier.max(), valeur.get());
        SpinnerValueFactory<Integer> fabrique = spinner.getValueFactory();
        fabrique.valueProperty().addListener((observable, ancien, nouveau) -> valeur.set(nouveau));
        valeur.addListener((observable, ancien, nouveau) -> fabrique.setValue(nouveau.intValue()));
        return spinner;
    }

    private static ComboBox<Option> liste(DescripteurReglage.Enumeration enumeration, ReglagesReactifs reactifs) {
        ComboBox<Option> combo = new ComboBox<>();
        combo.getItems().setAll(enumeration.options());
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Option option) {
                return option == null ? "" : option.libelle();
            }

            @Override
            public Option fromString(String texte) {
                return null; // liste non éditable : la conversion inverse est inutile
            }
        });
        StringProperty valeur = reactifs.proprieteTexte(enumeration.cle(), enumeration.defaut());
        optionPour(enumeration, valeur.get()).ifPresent(combo.getSelectionModel()::select);
        combo.getSelectionModel().selectedItemProperty().addListener((observable, ancien, nouveau) -> {
            if (nouveau != null) {
                valeur.set(nouveau.valeur());
            }
        });
        valeur.addListener((observable, ancien, nouveau) ->
                optionPour(enumeration, nouveau).ifPresent(combo.getSelectionModel()::select));
        return combo;
    }

    private static Optional<Option> optionPour(DescripteurReglage.Enumeration enumeration, String valeur) {
        return enumeration.options().stream()
                .filter(option -> option.valeur().equals(valeur))
                .findFirst();
    }
}
