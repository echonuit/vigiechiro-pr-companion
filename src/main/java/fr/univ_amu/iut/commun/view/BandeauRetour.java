package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.Map;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

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

    /// Classe CSS du bandeau selon la sévérité (succès vert / info neutre / avertissement ambre /
    /// erreur rouge).
    private static final Map<RetourOperation.Severite, String> CLASSE = Map.of(
            RetourOperation.Severite.SUCCES, "retour-succes",
            RetourOperation.Severite.INFO, "retour-info",
            RetourOperation.Severite.AVERTISSEMENT, "retour-avertissement",
            RetourOperation.Severite.ERREUR, "retour-erreur");

    /// Icône par sévérité, pendant de [#CLASSE] : le bandeau dit la même chose en couleur et en forme,
    /// pour qui distingue mal les couleurs comme pour qui lit vite.
    /// L'erreur a **cédé le triangle** à l'avertissement, dont c'est le glyphe usuel, et pris le cercle
    /// barré. Les faire cohabiter sur la même forme aurait vidé la promesse ci-dessus de son sens : deux
    /// niveaux distincts qui se ressemblent ne se distinguent plus quand la couleur manque.
    private static final Map<RetourOperation.Severite, String> ICONE = Map.of(
            RetourOperation.Severite.SUCCES, "fas-check-circle",
            RetourOperation.Severite.INFO, "fas-info-circle",
            RetourOperation.Severite.AVERTISSEMENT, "fas-exclamation-triangle",
            RetourOperation.Severite.ERREUR, "fas-times-circle");

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
        FontIcon icone = new FontIcon();
        icone.getStyleClass().add("bandeau-retour-icone");
        conteneur.getChildren().add(0, icone);
        retour.addListener((obs, avant, apres) -> rendreSeverite(conteneur, icone, apres));
        rendreSeverite(conteneur, icone, retour.getValue());
        fermer.setOnAction(evenement -> surFermeture.run());
    }

    /// Rend la sévérité **une fois pour deux canaux** : la couleur du bandeau et son icône. Elles ne
    /// peuvent donc pas se contredire, et le message n'a plus à porter de marqueur.
    private static void rendreSeverite(HBox conteneur, FontIcon icone, RetourOperation retour) {
        conteneur.getStyleClass().removeAll(CLASSE.values());
        conteneur.getStyleClass().add(CLASSE.get(retour.severite()));
        icone.setIconLiteral(ICONE.get(retour.severite()));
    }
}
