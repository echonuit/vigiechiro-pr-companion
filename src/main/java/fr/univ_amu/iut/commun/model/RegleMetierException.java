package fr.univ_amu.iut.commun.model;

import java.util.Objects;
import java.util.Optional;

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

    /// Ce qui manque à l'environnement, quand c'est de cela qu'il s'agit (#2635). Absent pour la plupart
    /// des refus, qui portent sur l'état du domaine et non sur ce dont l'application dispose.
    private final transient Optional<Besoin> besoin;

    public RegleMetierException(String message) {
        this(message, Optional.empty());
    }

    public RegleMetierException(String message, Throwable cause) {
        super(message, cause);
        this.besoin = Optional.empty();
    }

    /// Refus dû à un **besoin d'environnement** : le message dit ce qui manque et ce que ça empêche, sans
    /// nommer d'écran ni de commande. La surface qui l'affiche y ajoute le geste attendu.
    public RegleMetierException(String message, Besoin besoin) {
        this(message, Optional.of(Objects.requireNonNull(besoin, "besoin")));
    }

    private RegleMetierException(String message, Optional<Besoin> besoin) {
        super(message);
        this.besoin = besoin;
    }

    /// Ce qui manque, quand le refus vient de l'environnement. À la surface d'en tirer le geste à
    /// proposer ; ignorer ce besoin laisse un message juste, seulement moins guidant.
    public Optional<Besoin> besoin() {
        return besoin;
    }
}
