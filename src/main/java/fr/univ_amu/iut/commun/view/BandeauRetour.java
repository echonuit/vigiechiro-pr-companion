package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/// Câble le **bandeau de retour d'opération** (import / export / valider / corriger, action refusée) à
/// la propriété [RetourOperation] du ViewModel : le libellé, la visibilité (présent / absent), la
/// couleur de sévérité (classe CSS portée par le conteneur, héritée par le texte et la croix) et la
/// **croix de fermeture**. Isolé du controller pour le garder léger (PMD).
///
/// Attend dans le FXML un conteneur de classe `bandeau-retour` (style porté par `commun/view/design.css`,
/// que les écrans attachent déjà) contenant le libellé et le bouton de fermeture.
///
/// Né dans la vue audio, remonté dans `commun` quand l'Inventaire a eu besoin du même bandeau (#1837).
public final class BandeauRetour {

    /// Classe CSS du bandeau selon la sévérité (succès vert / info neutre / erreur rouge).
    private static final Map<RetourOperation.Severite, String> CLASSE = Map.of(
            RetourOperation.Severite.SUCCES, "retour-succes",
            RetourOperation.Severite.INFO, "retour-info",
            RetourOperation.Severite.ERREUR, "retour-erreur");

    private BandeauRetour() {}

    /// Installe le bandeau : `conteneur` (couleur + visibilité), `texte` (libellé), `fermer` (la croix,
    /// qui déclenche `surFermeture`), pilotés par `retour`.
    public static void installer(
            HBox conteneur,
            Label texte,
            Button fermer,
            ObservableValue<RetourOperation> retour,
            Runnable surFermeture) {
        texte.textProperty()
                .bind(Bindings.createStringBinding(() -> retour.getValue().texte(), retour));
        var present = Bindings.createBooleanBinding(() -> retour.getValue().present(), retour);
        conteneur.visibleProperty().bind(present);
        conteneur.managedProperty().bind(present);
        retour.addListener((obs, avant, apres) -> colorer(conteneur, apres));
        colorer(conteneur, retour.getValue());
        fermer.setOnAction(evenement -> surFermeture.run());
    }

    private static void colorer(HBox conteneur, RetourOperation retour) {
        conteneur.getStyleClass().removeAll(CLASSE.values());
        conteneur.getStyleClass().add(CLASSE.get(retour.severite()));
    }
}
