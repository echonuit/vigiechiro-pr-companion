package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.util.Objects;
import javafx.stage.Window;

/// Plomberie commune aux **quatre** entrées ☰ de sauvegarde / restauration (#1405) : elle détient
/// l'unique [ActionsSauvegarde] de l'entrée et lui fournit, au clic, la fenêtre propriétaire de ses
/// dialogues.
///
/// Les quatre construisaient jusqu'ici leur `ActionsSauvegarde` **dans** `executer()`, avec ses vrais
/// dialogues. Deux conséquences : la même plomberie recopiée quatre fois, et surtout **aucun test
/// possible** de ce câblage - rien ne vérifiait que « Restaurer » appelle bien `restaurer()` et non
/// `sauvegarder()`. Sur quatre entrées quasi identiques dont deux **écrasent la base**, c'est
/// précisément le copier-coller qui passe inaperçu.
///
/// L'action vit maintenant aussi longtemps que l'entrée de menu, ce qui la rend **atteignable** : un
/// test remplace ses trois porteurs de dialogue ([ActionsSauvegarde#selecteur],
/// [ActionsSauvegarde#confirmateur], [ActionsSauvegarde#notificateur]) puis déclenche l'entrée.
///
/// **Un porteur, pas un geste.** Le préfixe `Geste*` désigne ailleurs l'action **elle-même**, avec ses
/// dialogues (`GesteReset`, #1419) - le pendant d'[ActionsSauvegarde]. Cette
/// classe-ci ne *fait* rien : elle **porte** l'action et lui tend la fenêtre du clic. C'est le même
/// sens de « porteur » que [ConfirmateurModifiable] ou [NotificateurModifiable], qui portent une
/// stratégie sans en être une.
final class PorteurSauvegarde {

    /// Fenêtre propriétaire des dialogues, posée au clic : l'action la lit **paresseusement** (elle
    /// n'existe pas encore quand l'entrée de menu est construite).
    private Window proprietaire;

    private final ActionsSauvegarde actions;

    PorteurSauvegarde(ServiceSauvegarde service, Navigateur navigateur, OccupationChrome occupation) {
        Objects.requireNonNull(navigateur, "navigateur");
        this.actions = new ActionsSauvegarde(
                Objects.requireNonNull(service, "service"),
                Objects.requireNonNull(occupation, "occupation"),
                () -> proprietaire,
                navigateur::afficherAccueil);
    }

    /// L'action, prête à jouer sous `proprietaire` (la fenêtre du clic).
    ActionsSauvegarde sous(Window proprietaire) {
        this.proprietaire = proprietaire;
        return actions;
    }

    /// L'action exposée aux tests : `actions().selecteur()/confirmateur()/notificateur()`.
    ActionsSauvegarde actions() {
        return actions;
    }
}
