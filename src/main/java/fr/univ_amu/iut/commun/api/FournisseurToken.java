package fr.univ_amu.iut.commun.api;

import java.util.Optional;

/// Source du **token** d'authentification VigieChiro (chaîne de 32 caractères issue de l'OAuth de la
/// plateforme, cf. #142) : renvoie le token courant, ou vide si l'utilisateur n'est pas connecté.
///
/// Point d'extension du socle : l'implémentation réelle (lecture du token collé, stockage local,
/// péremption) est apportée par la gestion de connexion (#727). [ClientVigieChiro] le consomme sans
/// connaître cette mécanique.
@FunctionalInterface
public interface FournisseurToken {

    /// Le token courant, ou [Optional#empty()] si non connecté.
    Optional<String> token();

    /// L'API vient de refuser `jeton` en `401` (#6151) : la plateforme ne le reconnaît plus.
    ///
    /// Une source qui GARDE un jeton l'oublie ici, sinon chaque geste suivant le renverrait pour se voir
    /// refuser de même. Une source qui ne garde rien - un jeton ponctuel, un jeton de test - n'a rien à
    /// faire, d'où ce défaut. Appelé hors du fil JavaFX, depuis la tâche qui a reçu le refus.
    default void jetonRefuse(String jeton) {
        // Rien à oublier : cette source ne garde aucun jeton.
    }
}
