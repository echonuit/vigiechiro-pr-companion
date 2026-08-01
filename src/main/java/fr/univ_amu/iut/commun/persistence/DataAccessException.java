package fr.univ_amu.iut.commun.persistence;

/// Exception non vérifiée (unchecked) qui enveloppe une erreur de la couche de persistance.
///
/// L'API JDBC lève des [SQLException] **vérifiées** un peu partout. Les propager telles
/// quelles obligerait chaque appelant (ViewModel, service...) à les attraper, ce qui polluerait
/// tout
/// le code au-dessus de la couche DAO. La convention de la couche `commun.persistence` est
/// donc de **traduire** ces exceptions techniques en une [RuntimeException] dédiée : le
/// code métier reste lisible et peut choisir de l'attraper uniquement là où c'est pertinent
/// (tolérance aux erreurs, cf. objectifs qualité 5.3).
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Sans cause technique : la couche **refuse** d'elle-même, plutôt que de traduire l'échec d'un
    /// appel JDBC. Ainsi du refus de migrer sur un script modifié après coup (#2729), où rien n'a
    /// échoué : c'est nous qui décidons de ne pas continuer.
    public DataAccessException(String message) {
        super(message);
    }
}
