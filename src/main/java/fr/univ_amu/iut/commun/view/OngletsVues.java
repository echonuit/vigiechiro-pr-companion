package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.VueSauvegardee;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.javafx.FontIcon;

/// **Rendu** de la barre d'onglets de vues mémorisées : un onglet par vue, plus le bouton « + Vue ».
///
/// Extrait de [GestionnaireVues], qui garde le **cycle de vie** des vues (enregistrer, appliquer,
/// renommer, supprimer, restaurer) et lui délègue leur apparence. Les deux concerns se lisaient
/// ensemble mais ne changent pas pour les mêmes raisons : ce qu'une vue *fait* relève du dépôt et de
/// la barre de filtres, ce à quoi elle *ressemble* relève du CSS et des icônes.
///
/// Ne connaît ni le dépôt ni les filtres : les gestes lui arrivent en [Gestes], si bien qu'il se
/// construit et se regarde sans base de données.
final class OngletsVues {

    /// Ce qu'un onglet peut déclencher. Regroupés pour que [#redessiner] ne prenne pas six paramètres,
    /// et pour que l'ajout d'un geste ne change pas sa signature.
    ///
    /// @param appliquer rejouer la vue cliquée
    /// @param renommer demander un nouveau nom, puis renommer
    /// @param supprimer supprimer la vue
    /// @param enregistrerCommeNouvelle enregistrer les filtres courants comme une vue de plus
    /// @param enregistrerDansActive écraser la vue active avec les filtres courants
    record Gestes(
            Consumer<VueSauvegardee> appliquer,
            Consumer<VueSauvegardee> renommer,
            Consumer<VueSauvegardee> supprimer,
            Runnable enregistrerCommeNouvelle,
            Runnable enregistrerDansActive) {}

    private final Pane conteneur;
    private final Gestes gestes;

    OngletsVues(Pane conteneur, Gestes gestes) {
        this.conteneur = conteneur;
        this.gestes = gestes;
    }

    /// Redessine la barre entière : un onglet par vue, puis le bouton « + Vue ».
    ///
    /// @param vues les vues à représenter, dans l'ordre d'affichage
    /// @param active la vue active (mise en évidence), ou `null`
    /// @param modifiee vrai si les filtres courants ont divergé de la vue active
    /// @param estParDefaut dit si une vue est en lecture seule (pas de renommage ni de suppression)
    void redessiner(
            List<VueSauvegardee> vues,
            VueSauvegardee active,
            boolean modifiee,
            Predicate<VueSauvegardee> estParDefaut) {
        conteneur.getChildren().clear();
        for (VueSauvegardee vue : vues) {
            conteneur.getChildren().add(onglet(vue, vue.equals(active), modifiee, estParDefaut.test(vue)));
        }
        conteneur.getChildren().add(boutonNouvelle());
    }

    private HBox onglet(VueSauvegardee vue, boolean estActive, boolean modifiee, boolean parDefaut) {
        Label nom = new Label(vue.nom());
        nom.getStyleClass().add("onglet-vue-nom");
        nom.setOnMouseClicked(evenement -> gestes.appliquer().accept(vue));

        HBox onglet = new HBox(4.0, nom);
        onglet.getStyleClass().add("onglet-vue");
        if (estActive) {
            onglet.getStyleClass().add("onglet-vue-actif");
        }
        if (parDefaut) {
            onglet.getStyleClass().add("onglet-vue-defaut");
        }
        // Vue active dont les filtres ont divergé : bouton 💾. Sur une vue **par défaut** (lecture seule), il
        // enregistre les filtres courants comme une NOUVELLE vue ; sur une vue **utilisateur**, il écrase la vue.
        if (estActive && modifiee) {
            onglet.getStyleClass().add("onglet-vue-modifie");
            onglet.getChildren()
                    .add(
                            parDefaut
                                    ? bouton(
                                            "fas-save",
                                            "Enregistrer les filtres courants comme une nouvelle vue",
                                            gestes.enregistrerCommeNouvelle())
                                    : bouton(
                                            "fas-save",
                                            "Enregistrer les filtres courants dans la vue " + vue.nom(),
                                            gestes.enregistrerDansActive()));
        }
        // Renommer / supprimer : uniquement sur les vues utilisateur (les vues par défaut sont en lecture seule).
        if (!parDefaut) {
            onglet.getChildren()
                    .add(bouton(
                            "fas-pen",
                            "Renommer la vue " + vue.nom(),
                            () -> gestes.renommer().accept(vue)));
            onglet.getChildren()
                    .add(bouton(
                            "fas-times",
                            "Supprimer la vue " + vue.nom(),
                            () -> gestes.supprimer().accept(vue)));
        }
        return onglet;
    }

    /// Bouton d'action d'un onglet (enregistrer / renommer / supprimer) : une **icône Ikonli** (FontAwesome)
    /// plutôt qu'un glyphe de police. Les emojis (type 💾) ne se rendent pas dans toutes les polices : même
    /// constat que pour les indicateurs ⭐/💬 de la table audio, passés en `FontIcon` pour la même raison. Le
    /// libellé accessible porte le sens de l'action ; l'icône est colorée par la classe CSS `onglet-vue-icone`.
    private static Button bouton(String iconeLiteral, String accessible, Runnable action) {
        FontIcon icone = new FontIcon(iconeLiteral);
        icone.getStyleClass().add("onglet-vue-icone");
        Button bouton = new Button();
        bouton.setGraphic(icone);
        bouton.getStyleClass().add("onglet-vue-action");
        bouton.setAccessibleText(accessible);
        bouton.setOnAction(evenement -> action.run());
        return bouton;
    }

    private Button boutonNouvelle() {
        Button ajout = new Button("+ Vue");
        ajout.getStyleClass().add("onglet-vue-nouvelle");
        ajout.setAccessibleText("Enregistrer les filtres courants comme une nouvelle vue");
        ajout.setOnAction(evenement -> gestes.enregistrerCommeNouvelle().run());
        return ajout;
    }
}
