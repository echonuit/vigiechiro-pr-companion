package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.RetourOperation;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/// Câble le **bandeau de retour d'opération** (import / export / valider / corriger) à la propriété
/// [RetourOperation] du ViewModel : le libellé, la visibilité (présent / absent), la couleur de
/// sévérité (classe CSS portée par le conteneur, héritée par le texte et la croix) et la **croix de
/// fermeture**. Isolé du controller (même patron que [DepotFichier]) pour le garder léger (PMD).
final class BandeauRetour {

    /// Classe CSS du bandeau selon la sévérité (succès vert / info neutre / erreur rouge).
    private static final Map<RetourOperation.Severite, String> CLASSE = Map.of(
            RetourOperation.Severite.SUCCES, "retour-succes",
            RetourOperation.Severite.INFO, "retour-info",
            RetourOperation.Severite.ERREUR, "retour-erreur");

    private BandeauRetour() {}

    /// Installe le bandeau : `conteneur` (couleur + visibilité), `texte` (libellé), `fermer` (la croix,
    /// qui déclenche `surFermeture`), pilotés par `retour`.
    static void installer(
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
