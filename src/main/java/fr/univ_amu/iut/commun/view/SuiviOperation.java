package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.stage.Window;

/// Contrat d'une **opération longue à suivi visuel** (#1622) : lancer un `travail` **hors du fil JavaFX**
/// en lui passant un **relais de progression** et un **jeton d'annulation**, puis restituer sur le fil
/// JavaFX **exactement une** issue : succès, annulation (renoncer n'est pas échouer) ou échec.
///
/// [DialogueProgression] l'implémente avec une **modale à barre de progression** annulable. L'intérêt de
/// l'abstraction est la **testabilité** : un geste qui déclenche l'opération (ex. l'import des observations)
/// se teste avec un double **synchrone sans fenêtre**, sans dépendre de l'ouverture d'un `Stage` (qui exige
/// le fil JavaFX).
public interface SuiviOperation {

    /// Lance `travail` sous suivi, au-dessus de `proprietaire`. `succes` reçoit le résultat, `annule` est
    /// appelé si l'utilisateur renonce (pour effacer un éventuel état « en cours »), `echec` reçoit l'erreur
    /// : exactement un des trois, sur le fil JavaFX.
    <T> void lancer(
            Window proprietaire,
            String titre,
            BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
            Consumer<T> succes,
            Runnable annule,
            Consumer<Throwable> echec);

    /// Variante **sans réaction à l'annulation** : renoncer se solde par une simple fermeture (le geste n'a
    /// aucun état « en cours » à effacer). Délègue à la forme complète avec un `annule` neutre.
    default <T> void lancer(
            Window proprietaire,
            String titre,
            BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
            Consumer<T> succes,
            Consumer<Throwable> echec) {
        lancer(proprietaire, titre, travail, succes, () -> {}, echec);
    }
}
