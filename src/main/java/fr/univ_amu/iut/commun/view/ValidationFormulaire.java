package fr.univ_amu.iut.commun.view;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

/// Validation de formulaire « en direct » (#790) : plutôt que de laisser fermer une boîte de dialogue sur
/// un clic puis afficher une Alert détachée du champ fautif, on **désactive le bouton de validation** tant
/// que la saisie est invalide et on **marque les champs fautifs** (classe CSS `champ-invalide`, bordure
/// rouge, définie dans `design.css`).
///
/// Généralise ce que la modale point d'écoute faisait déjà à la main, pour les boîtes bâties en code
/// (`Dialog`/`DialogPane`) qui n'ont pas de contrôleur FXML dédié.
public final class ValidationFormulaire {

    /// Classe CSS d'un champ en erreur (bordure rouge), définie dans `commun/view/design.css`. Le
    /// [DialogPane] concerné doit charger cette feuille (cf. [#appliquerStyles]).
    public static final String CLASSE_CHAMP_INVALIDE = "champ-invalide";

    private ValidationFormulaire() {}

    /// Désactive le bouton `type` du [DialogPane] tant que `valide` est faux. Le nœud du bouton n'est créé
    /// par le DialogPane qu'une fois son [ButtonType] déclaré : à appeler **après**
    /// `getButtonTypes().add(...)`.
    public static void gaterBouton(DialogPane pane, ButtonType type, ObservableBooleanValue valide) {
        pane.lookupButton(type).disableProperty().bind(Bindings.not(valide));
    }

    /// Clé sous laquelle le nœud **retient** l'expression qu'on lui a confiée (cf. [#marquerInvalide]).
    private static final String CLE_CONDITION = "fr.univ_amu.iut.validation.condition-invalide";

    /// Ajoute (ou retire) la classe [#CLASSE_CHAMP_INVALIDE] sur `champ` selon `invalide`, réactivement.
    /// À alimenter avec une condition « saisi **mais** incorrect » : un champ encore vide ne doit pas
    /// rougir avant toute saisie.
    ///
    /// ## Pourquoi la condition est rangée dans le nœud (#3647)
    ///
    /// Les appelants passent une expression **neuve** - `anneeValide.not()`, une variable locale - que
    /// personne ne garde. Or une dépendance de binding JavaFX n'écoute son dérivé que **faiblement** :
    /// l'expression devenait donc collectable dès le retour de cette méthode, et l'écouteur posé
    /// ci-dessous disparaissait avec elle.
    ///
    /// Le champ restait alors sur l'état calculé **au câblage**. Vécu : à l'ouverture de « Modifier le
    /// passage », le ViewModel est encore vide, l'année vaut 0, le rouge est posé ; un ramasse-miettes
    /// passe avant que `demarrer` ne charge le passage, et l'année devenue 2026 ne l'efface plus. Un
    /// aperçu publié a montré ce refus imaginaire sur deux champs corrects.
    ///
    /// Retenir la condition **ici** plutôt que chez l'appelant couvre les quatre sites d'un coup, et
    /// ceux qu'on écrira ensuite : cette méthode reçoit une expression, elle en prend la garde.
    public static void marquerInvalide(Node champ, ObservableBooleanValue invalide) {
        appliquer(champ, invalide.get());
        invalide.addListener((observable, avant, apres) -> appliquer(champ, apres));
        champ.getProperties().put(CLE_CONDITION, invalide);
    }

    /// Charge sur `pane` les feuilles de style partagées (palette + design) pour qu'une boîte bâtie en
    /// code résolve `champ-invalide` et les jetons de couleur, comme un écran FXML.
    public static void appliquerStyles(DialogPane pane) {
        pane.getStylesheets()
                .addAll(
                        ValidationFormulaire.class.getResource("palette.css").toExternalForm(),
                        ValidationFormulaire.class.getResource("design.css").toExternalForm());
    }

    private static void appliquer(Node champ, boolean invalide) {
        champ.getStyleClass().remove(CLASSE_CHAMP_INVALIDE);
        if (invalide) {
            champ.getStyleClass().add(CLASSE_CHAMP_INVALIDE);
        }
    }
}
