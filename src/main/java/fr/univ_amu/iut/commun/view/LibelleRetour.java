package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
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
/// La sévérité pilote la classe CSS, comme dans le bandeau, et **le message ne porte plus de marqueur** :
/// avant #2050 ces libellés commençaient par un « ⚠ » écrit dans la chaîne, faute d'un niveau
/// `AVERTISSEMENT` dans le type (#2045).
public final class LibelleRetour {

    /// Classe CSS par sévérité. Le fond, la bordure et la couleur du texte vivent dans `design.css`.
    private static final Map<Severite, String> CLASSE = Map.of(
            Severite.SUCCES, "libelle-succes",
            Severite.INFO, "libelle-info",
            Severite.AVERTISSEMENT, "libelle-avertissement",
            Severite.ERREUR, "libelle-erreur");

    /// Classe de base, portée en permanence : forme de l'encadré, indépendante de la sévérité.
    public static final String CLASSE_BASE = "libelle-retour";

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

    private static void rendreSeverite(Label libelle, RetourOperation retour) {
        libelle.getStyleClass().setAll(CLASSE_BASE, CLASSE.get(retour.severite()));
    }
}
