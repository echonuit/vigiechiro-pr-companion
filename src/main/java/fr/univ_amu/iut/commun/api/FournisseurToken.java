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
}
