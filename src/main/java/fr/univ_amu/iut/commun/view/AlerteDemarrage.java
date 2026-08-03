package fr.univ_amu.iut.commun.view;

import javafx.scene.control.Alert;

/// Les rares dialogues que l'application doit ouvrir **avant** d'avoir un injecteur.
///
/// Le [Notificateur] est un port injectable ([ADR
/// 0010](https://companion-dev.echonuit.fr/decisions/0010-dialogues-bloquants-sont-des-ports/)),
/// et c'est ce qui rend les vues testables sans écran. Mais un refus de **démarrage** survient avant
/// la composition de l'injecteur : il n'y a alors aucun port à injecter, et rien à tester non plus,
/// puisque l'application ne démarre pas.
///
/// Ces dialogues vivent donc ici, dans le paquet des **adaptateurs**, où l'appel direct à [Alert] est
/// légitime, plutôt que dispersés dans l'amorçage où ils seraient des appels en dur.
public final class AlerteDemarrage {

    private AlerteDemarrage() {}

    /// Dit à l'utilisateur pourquoi l'application ne démarre pas, et attend qu'il l'ait lu.
    public static void refusDeDemarrage(String entete, String explication) {
        Alert alerte = new Alert(Alert.AlertType.WARNING);
        alerte.setHeaderText(entete);
        alerte.setContentText(explication);
        alerte.showAndWait();
    }
}
