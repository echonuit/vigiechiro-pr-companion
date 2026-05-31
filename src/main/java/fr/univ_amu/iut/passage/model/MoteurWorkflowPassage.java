package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.util.List;
import java.util.Optional;

/**
 * Moteur (pur) des transitions de {@link StatutWorkflow} d'un {@link Passage}.
 *
 * <p>Le workflow d'un passage est <b>linéaire</b> (C5) : {@code Importé → Transformé → Vérifié →
 * Prêt à déposer → Déposé}. Une seule transition est autorisée depuis un statut donné : son
 * <b>successeur immédiat</b>. On interdit ainsi de sauter une étape (ex. importer puis déposer
 * directement) ou de revenir en arrière (ex. re-transformer un passage déjà déposé).
 *
 * <p>Cette logique est isolée dans une classe dédiée (et non dans {@link StatutWorkflow}, qui vit
 * en {@code commun.model} et reste un simple énum de libellés) pour deux raisons :
 *
 * <ul>
 *   <li>elle est <b>spécifique à la feature {@code passage}</b> (le sens de progression n'a de sens
 *       que pour un passage) ;
 *   <li>elle est <b>purement algorithmique</b>, donc testable en JUnit nu, sans base ni mock — d'où
 *       le test {@code MoteurWorkflowPassageTest}.
 * </ul>
 *
 * <p>Une transition interdite est une violation d'<b>invariant métier</b> (règle dure) : elle lève
 * une {@link RegleMetierException}, par cohérence avec le patron du service de référence (cf.
 * SERVICE-CONVENTIONS §2.3).
 */
public final class MoteurWorkflowPassage {

  /** Ordre canonique des statuts : l'index dans cette liste définit la progression. */
  private static final List<StatutWorkflow> ORDRE =
      List.of(
          StatutWorkflow.IMPORTE,
          StatutWorkflow.TRANSFORME,
          StatutWorkflow.VERIFIE,
          StatutWorkflow.PRET_A_DEPOSER,
          StatutWorkflow.DEPOSE);

  /**
   * Successeur immédiat d'un statut, ou {@link Optional#empty()} si {@code actuel} est le statut
   * terminal ({@link StatutWorkflow#DEPOSE}).
   */
  public Optional<StatutWorkflow> suivant(StatutWorkflow actuel) {
    int index = ORDRE.indexOf(actuel);
    if (index < 0 || index == ORDRE.size() - 1) {
      return Optional.empty();
    }
    return Optional.of(ORDRE.get(index + 1));
  }

  /**
   * {@code true} si l'on peut passer de {@code actuel} à {@code cible}, c'est-à-dire si {@code
   * cible} est exactement le successeur immédiat de {@code actuel}.
   */
  public boolean estTransitionAutorisee(StatutWorkflow actuel, StatutWorkflow cible) {
    return suivant(actuel).map(attendu -> attendu == cible).orElse(false);
  }

  /**
   * Exige que la transition {@code actuel → cible} soit autorisée.
   *
   * @throws RegleMetierException si la transition n'est pas le passage à l'étape suivante (saut
   *     d'étape, retour en arrière, ou statut déjà terminal)
   */
  public void exigerTransitionAutorisee(StatutWorkflow actuel, StatutWorkflow cible) {
    if (!estTransitionAutorisee(actuel, cible)) {
      throw new RegleMetierException(
          "Transition de workflow interdite : « "
              + actuel.libelle()
              + " » → « "
              + cible.libelle()
              + " ». Seul le passage à l'étape suivante est autorisé"
              + suivant(actuel).map(s -> " (attendu : « " + s.libelle() + " »).").orElse("."));
    }
  }
}
