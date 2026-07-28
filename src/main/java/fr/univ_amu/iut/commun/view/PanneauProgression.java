package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/// Suivi d'opération longue **dans l'écran qui l'a lancée** (#2642) : le contenu est greffé dans une zone
/// fournie par l'appelant, et retiré à la fin.
///
/// C'est la présentation à retenir quand le geste part d'une **modale**. Ouvrir une fenêtre y produirait
/// deux fenêtres pour un seul geste, alors que l'écran d'origine a déjà la zone où il parle à
/// l'utilisateur - celle qui annonçait « Vérification en cours… » avant, et qui annonce le résultat
/// après. Arbitrage rendu à la clôture de #2554 ; la décision correspondante de l'EPIC #2350 est tranchée.
///
/// La zone est **masquée et non gérée** entre deux opérations : elle ne réserve donc aucune place dans la
/// mise en page tant qu'il n'y a rien à montrer.
///
/// `inerte` reçoit `true` pendant l'opération et `false` après : à l'appelant d'y désactiver ce qui n'a
/// plus de sens (on ne recolle pas un jeton pendant qu'on en vérifie un). L'appelant le fait plutôt que
/// ce panneau, parce que lui seul sait quels contrôles de son écran sont concernés.
public final class PanneauProgression extends SuiviProgression {

    private final Pane zone;
    private final Consumer<Boolean> inerte;

    /// @param zone conteneur d'accueil, vidé et masqué à la fin
    /// @param inerte notifié `true` à l'ouverture, `false` au retrait
    public PanneauProgression(ExecuteurTache executeur, Pane zone, Consumer<Boolean> inerte) {
        super(executeur);
        this.zone = Objects.requireNonNull(zone, "zone");
        this.inerte = Objects.requireNonNull(inerte, "inerte");
    }

    @Override
    protected Runnable presenter(Window proprietaire, String titre, VBox contenu, JetonAnnulation jeton) {
        afficher(List.of(contenu), true);
        return () -> afficher(List.of(), false);
    }

    private void afficher(List<Node> enfants, boolean occupe) {
        zone.getChildren().setAll(enfants);
        zone.setVisible(occupe);
        // `managed` autant que `visible` : une zone seulement invisible continue de réserver sa place, et
        // la modale garderait un trou à hauteur de la barre entre deux opérations.
        zone.setManaged(occupe);
        inerte.accept(occupe);
    }
}
