package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;

/// Câble un **libellé de retour posé dans le flux de l'écran**, à côté de ce qu'il commente : jumeau
/// inline de [BandeauRetour], qui occupe lui le haut de la vue.
///
/// Les deux surfaces existent parce que les messages n'ont pas la même portée. Un bandeau annonce
/// l'issue d'un geste que l'utilisateur vient de faire ; un libellé inline commente un **champ** ou une
/// **section** et reste tant que la situation dure. Les confondre reviendrait à faire remonter en tête
/// d'écran un avertissement qui ne veut rien dire loin du champ qu'il concerne.
///
/// La forme vient de `.encart` (socle, #1974) ; seule la sévérité change de classe. Le message, lui, ne
/// porte plus de marqueur :
/// avant #2050 ces libellés commençaient par un « ⚠ » écrit dans la chaîne, faute d'un niveau
/// `AVERTISSEMENT` dans le type (#2045).
public final class LibelleRetour {

    /// Classe CSS par sévérité. Le fond, la bordure et la couleur du texte vivent dans `design.css`.
    private static final Map<Severite, String> CLASSE = Map.of(
            Severite.SUCCES, "encart-succes",
            Severite.INFO, "encart-info",
            Severite.AVERTISSEMENT, "encart-avertissement",
            Severite.ERREUR, "encart-erreur");

    /// Classe de base, portée en permanence : la **forme** de l'encart, indépendante de la sévérité.
    ///
    /// C'est celle du socle (`.encart`, #1974), pas une famille propre à ce composant. La première
    /// version en avait créé une - `.libelle-retour` + quatre sévérités - sans voir que `.encart` +
    /// `.encart-avertissement` venait d'être posé pour exactement ce concept, et sur exactement ce
    /// principe (« la forme et la sévérité se séparent »). C'est le défaut que #1974 corrigeait, refait
    /// sur la PR d'après, et rattrapé à la clôture de #2004.
    public static final String CLASSE_BASE = "encart";

    private LibelleRetour() {}

    /// Installe le libellé : texte, visibilité (présent / absent) et couleur de sévérité.
    ///
    /// Le libellé se **retire de la mise en page** quand il n'a rien à dire, sans quoi il laisserait un
    /// encadré vide au milieu du formulaire.
    public static void installer(Label libelle, ObservableValue<RetourOperation> retour) {
        libelle.setWrapText(true);
        libelle.textProperty()
                .bind(Bindings.createStringBinding(() -> retour.getValue().texte(), retour));
        var present = Bindings.createBooleanBinding(() -> retour.getValue().present(), retour);
        libelle.visibleProperty().bind(present);
        libelle.managedProperty().bind(present);
        retour.addListener((observable, avant, apres) -> rendreSeverite(libelle, apres));
        rendreSeverite(libelle, retour.getValue());
    }

    /// La sévérité, **deux fois** : la classe CSS (couleur) et le glyphe (forme).
    ///
    /// Les encarts déclarés en FXML - Diagnostic, Qualification - portent tous leur icône ; celui-ci
    /// serait le seul de la famille à n'avoir que la couleur. La table est celle de [IconesSeverite],
    /// partagée avec le bandeau et le compte rendu, pour qu'un même niveau ait partout la même forme.
    private static void rendreSeverite(Label libelle, RetourOperation retour) {
        String classe = CLASSE.get(retour.severite());
        libelle.getStyleClass().setAll(CLASSE_BASE, classe);
        libelle.setGraphic(IconesSeverite.icone(retour.severite()));
    }
}
