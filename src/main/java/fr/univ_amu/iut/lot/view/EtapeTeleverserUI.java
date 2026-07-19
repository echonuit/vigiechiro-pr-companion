package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.IconeSelonEtat;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/// Câblage de l'**étape 3 du dépôt** : téléverser sur Vigie-Chiro, et l'annuler.
///
/// Deux boutons qui changent de sens en cours de route, et doivent le **dire** :
///
/// - **Téléverser** devient **Reprendre le dépôt** dès qu'un dépôt interrompu laisse des fichiers à
///   renvoyer (#1044) - l'icône suit, sans quoi le nuage du premier envoi resterait sur un bouton qui
///   reprend ;
/// - **Annuler le dépôt** se fige sur **Annulation…** le temps que le fichier en vol se termine :
///   l'annulation est coopérative, jamais une interruption brutale.
///
/// Sœur d'[EtapeDeposerUI], extraite pour la même raison : le contrôleur du lot est au plafond de taille
/// que le portail qualité lui accorde, et chaque étape du dépôt est un morceau cohérent qui vit mieux
/// nommé qu'inséré dans une méthode d'initialisation de deux cents lignes.
final class EtapeTeleverserUI {

    private EtapeTeleverserUI() {}

    static void cabler(Button televerser, FontIcon icone, Button annuler, DepotViewModel depot) {
        Objects.requireNonNull(televerser, "televerser");
        Objects.requireNonNull(annuler, "annuler");
        Objects.requireNonNull(depot, "depot");
        var resteAReprendre = depot.suiviLignes().resteAReprendreProperty();
        IconeSelonEtat.lier(icone, resteAReprendre, FontAwesomeSolid.REDO, FontAwesomeSolid.CLOUD);
        televerser
                .textProperty()
                .bind(Bindings.when(resteAReprendre)
                        .then("Reprendre le dépôt")
                        .otherwise("Téléverser sur Vigie-Chiro"));
        annuler.visibleProperty().bind(depot.enCoursProperty());
        annuler.managedProperty().bind(depot.enCoursProperty());
        annuler.disableProperty().bind(depot.annulationDemandeeProperty());
        annuler.textProperty()
                .bind(Bindings.when(depot.annulationDemandeeProperty())
                        .then("Annulation…")
                        .otherwise("Annuler le dépôt"));
    }
}
