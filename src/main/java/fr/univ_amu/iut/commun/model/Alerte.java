package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/**
 * Résultat élémentaire d'une vérification de règle métier <b>non bloquante</b> ou <b>bloquante</b>.
 *
 * <p>Le modèle conceptuel distingue deux familles de règles (cf. {@code Règles métier.md}) :
 *
 * <ul>
 *   <li>les règles <b>SOFT</b> (ex. R3 : passage hors fenêtre, R4 : intervalle &lt; 1 mois) :
 *       l'application <i>alerte sans bloquer</i>, l'utilisateur reste libre de continuer ;
 *   <li>les règles <b>BLOQUANTES</b> (ex. R14 : un passage « À jeter » ne peut pas rejoindre un
 *       lot) : l'action est <i>refusée</i>.
 * </ul>
 *
 * <p>Une {@code Alerte} porte ce niveau + un message destiné à l'utilisateur. Les services agrègent
 * plusieurs alertes dans un {@link ResultatVerification}. Attention : une alerte <i>bloquante</i>
 * exprimée via ce type sert à <b>présenter</b> un refus à l'IHM (cumul d'erreurs) ; un refus qui
 * doit interrompre un traitement côté service est levé par une {@link RegleMetierException}.
 *
 * @param niveau gravité de l'alerte
 * @param message texte affichable (français)
 */
public record Alerte(Niveau niveau, String message) {

  /** Niveau de gravité d'une alerte métier. */
  public enum Niveau {
    /** Avertissement non bloquant (l'utilisateur peut passer outre). */
    SOFT,
    /** Violation bloquante (l'action est refusée). */
    BLOQUANT
  }

  public Alerte {
    Objects.requireNonNull(niveau, "niveau");
    Objects.requireNonNull(message, "message");
  }

  /** Crée une alerte non bloquante (avertissement). */
  public static Alerte soft(String message) {
    return new Alerte(Niveau.SOFT, message);
  }

  /** Crée une alerte bloquante. */
  public static Alerte bloquante(String message) {
    return new Alerte(Niveau.BLOQUANT, message);
  }

  /** {@code true} si cette alerte est bloquante. */
  public boolean estBloquante() {
    return niveau == Niveau.BLOQUANT;
  }
}
