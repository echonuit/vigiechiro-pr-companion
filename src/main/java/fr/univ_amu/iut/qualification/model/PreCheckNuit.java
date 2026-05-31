package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.ResultatVerification;
import java.util.Objects;

/**
 * Pré-check synthétique d'une nuit d'enregistrement (P3, étape 1) : produit <b>trois feux</b> (🟢
 * vert / 🟠 orange / 🔴 rouge) résumant l'état de la nuit <i>sans écoute</i>.
 *
 * <ul>
 *   <li><b>Couverture horaire</b> : la plage observée couvre-t-elle la fenêtre théorique de la nuit
 *       (R3) ? 🟠 si l'écart dépasse {@value #TOLERANCE_COUVERTURE_MINUTES} min d'un côté, 🔴 si
 *       une moitié de nuit complète manque.
 *   <li><b>Nombre de fichiers</b> : 🔴 si aucun fichier, 🟠 si la nuit est anormalement creuse
 *       (&lt; {@value #SEUIL_FICHIERS_CREUX}), 🟢 sinon.
 *   <li><b>Cohérence du renommage</b> : 🔴 dès qu'un fichier diverge du préfixe attendu (R6), 🟢
 *       sinon.
 * </ul>
 *
 * <p><b>Moteur pur.</b> {@link #evaluer(Mesures)} décide les feux à partir de mesures déjà
 * calculées ({@link Mesures}) : aucune base, aucune IHM, aucun parsing ici (le comptage des
 * fichiers, le calcul de l'écart de couverture et la vérification du préfixe relèvent de {@code
 * ServiceQualification}, qui dispose des DAO). On peut donc tester tous les feux en JUnit nu.
 *
 * <p><b>Consultatif (R13).</b> Le pré-check ne bloque jamais le workflow : l'utilisateur reste
 * responsable, aucun seuil n'est imposé. La précision des trois couleurs est portée par {@link
 * Diagnostic} ; sa conversion {@link Diagnostic#versResultatVerification()} n'émet donc que des
 * alertes <b>soft</b> (jamais bloquantes), conformément au patron « règle soft → {@link
 * ResultatVerification} ». La seule règle réellement bloquante de cette chaîne est R14 (verdict « À
 * jeter » ⇒ exclu d'un lot), traitée en aval par la feature {@code lot}.
 */
public class PreCheckNuit {

  /** En-deçà de ce nombre d'enregistrements, la nuit est jugée anormalement creuse (🟠). */
  public static final int SEUIL_FICHIERS_CREUX = 50;

  /** Tolérance (minutes) sur la couverture horaire avant de passer le feu à l'orange. */
  public static final long TOLERANCE_COUVERTURE_MINUTES = 30;

  /** Couleur d'un indicateur du pré-check. */
  public enum Feu {
    VERT,
    ORANGE,
    ROUGE
  }

  /**
   * Mesures brutes d'une nuit, calculées en amont (par {@code ServiceQualification}) et fournies au
   * moteur. Toutes les grandeurs sont ≥ 0.
   *
   * @param nombreFichiers nombre d'enregistrements originaux de la session
   * @param fichiersMalNommes nombre d'originaux dont le nom diverge du préfixe attendu (R6)
   * @param ecartCouvertureMinutes plus grand déficit de couverture observé d'un côté (minutes)
   * @param moitieNuitManquante {@code true} si une moitié de nuit complète manque
   */
  public record Mesures(
      int nombreFichiers,
      int fichiersMalNommes,
      long ecartCouvertureMinutes,
      boolean moitieNuitManquante) {

    public Mesures {
      if (nombreFichiers < 0 || fichiersMalNommes < 0 || ecartCouvertureMinutes < 0) {
        throw new IllegalArgumentException("Les mesures de la nuit doivent être positives.");
      }
    }
  }

  /**
   * Les trois feux du pré-check.
   *
   * @param couvertureHoraire couverture de la fenêtre théorique de la nuit (R3)
   * @param nombreFichiers volume d'enregistrements
   * @param coherenceRenommage conformité des noms de fichiers au préfixe attendu (R6)
   */
  public record Diagnostic(Feu couvertureHoraire, Feu nombreFichiers, Feu coherenceRenommage) {

    public Diagnostic {
      Objects.requireNonNull(couvertureHoraire, "couvertureHoraire");
      Objects.requireNonNull(nombreFichiers, "nombreFichiers");
      Objects.requireNonNull(coherenceRenommage, "coherenceRenommage");
    }

    /** {@code true} si les trois feux sont au vert (rien à signaler). */
    public boolean toutAuVert() {
      return couvertureHoraire == Feu.VERT
          && nombreFichiers == Feu.VERT
          && coherenceRenommage == Feu.VERT;
    }

    /** {@code true} si au moins un feu est au rouge (anomalie à examiner). */
    public boolean presenteUneAnomalie() {
      return couvertureHoraire == Feu.ROUGE
          || nombreFichiers == Feu.ROUGE
          || coherenceRenommage == Feu.ROUGE;
    }

    /**
     * Restitue le diagnostic « sous forme de {@link ResultatVerification} » pour l'IHM : un feu
     * vert n'ajoute rien, un feu orange ou rouge ajoute une alerte <b>soft</b> (le pré-check est
     * consultatif, R13). {@code estConforme()} équivaut donc à {@link #toutAuVert()}, et {@code
     * estBloquant()} est toujours {@code false}.
     */
    public ResultatVerification versResultatVerification() {
      ResultatVerification resultat = ResultatVerification.ok();
      resultat = ajouter(resultat, couvertureHoraire, "Couverture horaire");
      resultat = ajouter(resultat, nombreFichiers, "Nombre de fichiers");
      resultat = ajouter(resultat, coherenceRenommage, "Cohérence du renommage");
      return resultat;
    }

    private static ResultatVerification ajouter(
        ResultatVerification resultat, Feu feu, String libelle) {
      return switch (feu) {
        case VERT -> resultat;
        case ORANGE -> resultat.avec(Alerte.soft(libelle + " : à surveiller (feu orange)."));
        case ROUGE -> resultat.avec(Alerte.soft(libelle + " : anomalie détectée (feu rouge)."));
      };
    }
  }

  /** Décide les trois feux à partir des mesures de la nuit. */
  public Diagnostic evaluer(Mesures mesures) {
    Objects.requireNonNull(mesures, "mesures");
    return new Diagnostic(
        evaluerCouverture(mesures), evaluerNombre(mesures), evaluerRenommage(mesures));
  }

  private static Feu evaluerCouverture(Mesures mesures) {
    if (mesures.moitieNuitManquante()) {
      return Feu.ROUGE;
    }
    if (mesures.ecartCouvertureMinutes() > TOLERANCE_COUVERTURE_MINUTES) {
      return Feu.ORANGE;
    }
    return Feu.VERT;
  }

  private static Feu evaluerNombre(Mesures mesures) {
    if (mesures.nombreFichiers() == 0) {
      return Feu.ROUGE;
    }
    if (mesures.nombreFichiers() < SEUIL_FICHIERS_CREUX) {
      return Feu.ORANGE;
    }
    return Feu.VERT;
  }

  private static Feu evaluerRenommage(Mesures mesures) {
    return mesures.fichiersMalNommes() > 0 ? Feu.ROUGE : Feu.VERT;
  }
}
