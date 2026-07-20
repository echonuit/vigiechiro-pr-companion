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
    /// précédés d'un libellé sur une ligne dédiée. **L'aide suit le contrôle, visible** (#2085).
    ///
    /// Elle n'était rendue qu'en infobulle : le libellé s'affichait, la conséquence non. Or plusieurs
    /// réglages portent dans leur aide un **arbitrage** que l'utilisateur doit connaître avant de
    /// choisir — la forme du dépôt décide si l'audio reste récupérable côté serveur
    /// ([ADR 0034](../../../../../../dev-docs/decisions/0034-la-forme-du-depot-se-choisit.md)), la
    /// conservation des originaux coûte plusieurs Go par nuit
    /// ([ADR 0036](../../../../../../dev-docs/decisions/0036-la-copie-des-bruts-est-une-option.md)).
    /// Un texte qu'il faut survoler pour lire n'est pas lu : c'est un geste qu'on ne fait pas si on
    /// ignore qu'il y a quelque chose à lire, et qui n'existe pas au tactile.
    ///
    /// **Visible pour tous les réglages, pas seulement ceux qu'on juge importants** : cette sélection
    /// demanderait d'arbitrer réglage par réglage et dériverait. Une aide qui ne mérite pas d'être
    /// montrée ne mérite pas d'être écrite.
    private static Node ligne(DescripteurReglage descripteur, ReglagesReactifs reactifs) {
        Control controle = controle(descripteur, reactifs);
        VBox ligne = new VBox();
        ligne.getStyleClass().add("reglage-ligne");
        if (!(descripteur instanceof DescripteurReglage.Booleen)) {
            Label libelle = new Label(descripteur.libelle());
            libelle.getStyleClass().add("reglage-libelle");
            ligne.getChildren().add(libelle);
        }
        ligne.getChildren().add(controle);
        aide(descripteur).ifPresent(ligne.getChildren()::add);
        return ligne;
    }

    /// L'aide d'un réglage, rendue sous son contrôle. Vide quand le réglage n'en déclare pas.
    private static Optional<Node> aide(DescripteurReglage descripteur) {
        if (descripteur.aide().isBlank()) {
            return Optional.empty();
        }
        Label aide = new Label(descripteur.aide());
        aide.setWrapText(true);
        aide.getStyleClass().add("reglage-aide");
        return Optional.of(aide);
    }

    /// Contrôle câblé (bidirectionnel) au réglage décrit. L'aide n'est plus une infobulle : elle est
    /// rendue sous le contrôle par [#ligne] (#2085), et deux canaux pour le même texte n'en valent
    /// pas un.
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
