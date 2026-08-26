package fr.univ_amu.iut.commun.view;

import java.util.Objects;

/// Porteur **injectable** du compte rendu d'action, exact pendant de [ConfirmateurModifiable] : par
/// défaut le vrai dialogue ([NotificationDialogue]), remplaçable par un double en test ([#definir]) -
/// un `showAndWait` natif figerait TestFX headless.
///
/// Le contrôleur en détient une instance `final` et l'expose à ses tests, comme il le fait déjà du
/// confirmateur : c'est ce qui permet de cliquer réellement sur « Réactiver ce passage » dans un test et
/// de vérifier **ce qui a été fait** autant que **ce qui a été dit**.
///
/// **Pas de constructeur sans argument**, et c'est délibéré (#4092). Il en existait un, qui posait un
/// [NotificationDialogue] sans propriétaire ; cinq écrans l'avaient pris pour le cas ordinaire, et leurs
/// comptes rendus s'ouvraient donc là où le bureau voulait bien les mettre. Nommer sa fenêtre est
/// désormais la seule façon d'en construire un, et le compilateur le rappelle.
public final class NotificateurModifiable implements Notificateur {

    private Notificateur delegue;

    /// Porteur au notificateur **initial** fourni (ex. un [NotificationDialogue] rattaché à une fenêtre).
    public NotificateurModifiable(Notificateur initial) {
        this.delegue = Objects.requireNonNull(initial, "notificateur");
    }

    @Override
    public void notifier(NiveauNotification niveau, String entete, String message) {
        delegue.notifier(niveau, entete, message);
    }

    /// Remplace la stratégie de compte rendu (double capturant dans les tests).
    public void definir(Notificateur notificateur) {
        this.delegue = Objects.requireNonNull(notificateur, "notificateur");
    }
}
