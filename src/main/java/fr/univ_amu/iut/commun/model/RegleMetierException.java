package fr.univ_amu.iut.commun.model;

/// Exception métier : signale la violation d'une règle **dure** (refus), par opposition aux
/// règles soft restituées via [ResultatVerification].
///
/// Exemples portés par `ServiceSites` : unicité du carré par utilisateur (R5), refus de
/// supprimer un site auquel des passages sont rattachés. R14 (un passage « Inexploitable » ne
/// peut pas rejoindre un lot).
///
/// Cette exception se distingue volontairement :
///
/// - de [IllegalArgumentException] (et des `exigerValide(...)` des validateurs
/// R1/R2), qui signale une **donnée mal formée en entrée** (validation de saisie) ;
/// - de `DataAccessException`, qui enveloppe une **panne technique** de persistance.
///
/// Non vérifiée ([RuntimeException]) : cohérent avec le reste de la base de code et adapté
/// à une remontée jusqu'à la couche IHM (qui la traduit en message).
public class RegleMetierException extends RuntimeException {

    public RegleMetierException(String message) {
        super(message);
    }

    public RegleMetierException(String message, Throwable cause) {
        super(message, cause);
    }
}
