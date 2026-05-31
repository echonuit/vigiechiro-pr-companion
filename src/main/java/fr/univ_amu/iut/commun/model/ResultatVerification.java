package fr.univ_amu.iut.commun.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrégat immuable d'{@link Alerte} produit par la vérification d'une ou plusieurs règles métier
 * <b>soft</b>.
 *
 * <p>C'est le <b>type de retour conventionnel</b> des méthodes de service qui appliquent des règles
 * non bloquantes (ex. les rappels R3/R4 sur un site {@code PointFixeStandard}) : plutôt que de
 * lever une exception, le service renvoie la liste des avertissements et laisse l'IHM décider de
 * leur présentation. Un résultat sans alerte ({@link #ok()}) signifie « rien à signaler ».
 *
 * <p>À l'inverse, les règles <b>dures</b> (unicité R5, refus d'intégrité) sont signalées par une
 * {@link RegleMetierException} levée par le service, pas par ce type.
 *
 * @param alertes liste des alertes (copie défensive immuable)
 */
public record ResultatVerification(List<Alerte> alertes) {

  public ResultatVerification {
    alertes = List.copyOf(alertes);
  }

  /** Résultat conforme : aucune alerte. */
  public static ResultatVerification ok() {
    return new ResultatVerification(List.of());
  }

  /** Résultat regroupant les alertes fournies. */
  public static ResultatVerification de(Alerte... alertes) {
    return new ResultatVerification(List.of(alertes));
  }

  /** Renvoie un nouveau résultat enrichi de {@code alerte} (accumulation immuable et fluente). */
  public ResultatVerification avec(Alerte alerte) {
    List<Alerte> copie = new ArrayList<>(alertes);
    copie.add(alerte);
    return new ResultatVerification(copie);
  }

  /** {@code true} si au moins une alerte est bloquante. */
  public boolean estBloquant() {
    return alertes.stream().anyMatch(Alerte::estBloquante);
  }

  /** {@code true} si aucune alerte n'a été émise (rien à signaler). */
  public boolean estConforme() {
    return alertes.isEmpty();
  }

  /** Sous-liste des seules alertes bloquantes. */
  public List<Alerte> alertesBloquantes() {
    return alertes.stream().filter(Alerte::estBloquante).toList();
  }

  /** Messages de toutes les alertes, dans l'ordre. */
  public List<String> messages() {
    return alertes.stream().map(Alerte::message).toList();
  }
}
