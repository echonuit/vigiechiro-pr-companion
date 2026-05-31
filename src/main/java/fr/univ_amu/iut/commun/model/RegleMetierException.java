package fr.univ_amu.iut.commun.model;

/**
 * Exception métier : signale la violation d'une règle <b>dure</b> (refus), par opposition aux
 * règles soft restituées via {@link ResultatVerification}.
 *
 * <p>Exemples portés par {@code ServiceSites} : unicité du carré par utilisateur (R5), refus de
 * supprimer un site auquel des passages sont rattachés. À venir : R14 (un passage « À jeter » ne
 * peut pas rejoindre un lot).
 *
 * <p>Cette exception se distingue volontairement :
 *
 * <ul>
 *   <li>de {@link IllegalArgumentException} (et des {@code exigerValide(...)} des validateurs
 *       R1/R2), qui signale une <b>donnée mal formée en entrée</b> (validation de saisie) ;
 *   <li>de {@code DataAccessException}, qui enveloppe une <b>panne technique</b> de persistance.
 * </ul>
 *
 * <p>Non vérifiée ({@link RuntimeException}) : cohérent avec le reste de la base de code et adapté
 * à une remontée jusqu'à la couche IHM (qui la traduit en message).
 */
public class RegleMetierException extends RuntimeException {

  public RegleMetierException(String message) {
    super(message);
  }

  public RegleMetierException(String message, Throwable cause) {
    super(message, cause);
  }
}
