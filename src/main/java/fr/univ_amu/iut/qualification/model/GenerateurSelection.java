package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Moteur de constitution d'une sélection d'écoute (R12). À partir de l'ensemble des séquences d'une
 * nuit, il en retient un sous-ensemble selon une {@link MethodeSelection} et une taille cible.
 *
 * <p><b>Pourquoi un moteur séparé du service ?</b> La logique de « comment choisir les séquences »
 * est une règle pure (pas de base, pas d'IHM) : on l'isole pour la tester sans persistance et la
 * réutiliser (objectif réutilisation O6). C'est {@code ServiceQualification} qui l'alimente avec
 * les séquences lues en base et persiste le résultat.
 *
 * <p><b>« Réparti uniformément sur la nuit, par horodatage de l'original source » (R12).</b> Les
 * conventions de nommage R6/R7/R8 garantissent que le nom de fichier d'une séquence vaut {@code
 * Car<carré>-<année>-Pass<n>-<point>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>_NNN.wav}. À l'intérieur d'une
 * même session, le préfixe et le numéro de série sont constants : la seule partie variable est
 * l'horodatage de l'enregistreur (zéro-paddé, largeur fixe) suivi de l'index de tranche. <b>L'ordre
 * lexicographique du nom de fichier coïncide donc exactement avec l'ordre chronologique de
 * l'original source</b> (c'est d'ailleurs la convention déjà retenue par {@code
 * SequenceDao#findBySession}, qui trie par {@code file_name}). Le moteur trie défensivement sur le
 * nom de fichier puis échantillonne, sans avoir à parser l'horodatage.
 *
 * <p><b>Déterminisme.</b> La méthode {@link MethodeSelection#REPARTITION_TEMPORELLE} (défaut R12)
 * est strictement déterministe : pour les mêmes séquences et la même taille, elle renvoie toujours
 * la même sélection. La méthode {@link MethodeSelection#ALEATOIRE} dépend d'un {@link Random}
 * (injectable pour les tests via {@link #aleatoire(List, int, Random)}). La méthode {@link
 * MethodeSelection#MANUEL} considère la liste fournie comme le choix explicite de l'utilisateur.
 */
public class GenerateurSelection {

  /** Borne basse conseillée pour la taille d'une sélection (R12 : « 10 à 30 séquences »). */
  public static final int TAILLE_MIN = 10;

  /** Borne haute conseillée pour la taille d'une sélection (R12 : « 10 à 30 séquences »). */
  public static final int TAILLE_MAX = 30;

  /** Taille par défaut à l'ouverture de la vue de vérification (dans la fourchette R12). */
  public static final int TAILLE_DEFAUT = 20;

  /** Ordre chronologique = ordre lexicographique du nom de fichier (cf. Javadoc de classe). */
  private static final Comparator<SequenceDEcoute> CHRONOLOGIQUE =
      Comparator.comparing(
          SequenceDEcoute::nomFichier, Comparator.nullsLast(Comparator.naturalOrder()));

  /**
   * Constitue la liste ordonnée des séquences retenues pour une sélection.
   *
   * <p>La taille effective vaut {@code min(taille, nombre de séquences disponibles)} : si la nuit
   * compte moins de séquences que demandé, on retient tout ce qui existe (« 10 à 30 » suppose un
   * volume suffisant). La taille demandée n'est pas plafonnée à {@link #TAILLE_MAX} car elle est
   * configurable au-delà (cf. P3 : l'utilisateur peut monter à 50).
   *
   * @param sequencesDeLaNuit toutes les séquences d'écoute de la nuit (ordre quelconque)
   * @param methode méthode de constitution (R12)
   * @param taille taille cible (≥ 1)
   * @return les séquences retenues, ordonnées chronologiquement (par nom de fichier)
   * @throws IllegalArgumentException si {@code taille < 1}
   */
  public List<SequenceDEcoute> selectionner(
      List<SequenceDEcoute> sequencesDeLaNuit, MethodeSelection methode, int taille) {
    Objects.requireNonNull(sequencesDeLaNuit, "sequencesDeLaNuit");
    Objects.requireNonNull(methode, "methode");
    if (taille < 1) {
      throw new IllegalArgumentException(
          "La taille demandée doit être au moins 1 (reçu : " + taille + ").");
    }
    List<SequenceDEcoute> triees = new ArrayList<>(sequencesDeLaNuit);
    triees.sort(CHRONOLOGIQUE);
    int k = Math.min(taille, triees.size());
    if (k == 0) {
      return List.of();
    }
    return switch (methode) {
      case REPARTITION_TEMPORELLE -> repartitionTemporelle(triees, k);
      case ALEATOIRE -> aleatoire(triees, k, new Random());
      case MANUEL -> new ArrayList<>(triees.subList(0, k));
    };
  }

  /**
   * Échantillonnage temporel déterministe (R12, méthode par défaut) : retient {@code taille}
   * séquences <b>réparties uniformément</b> de la première à la dernière de la nuit.
   *
   * <p>Algorithme : après tri chronologique des {@code n} séquences, on retient les indices {@code
   * round(i × (n-1) / (taille-1))} pour {@code i = 0 … taille-1}. La première et la dernière
   * séquence de la nuit sont toujours incluses, les autres sont espacées régulièrement. Comme
   * {@code taille < n} implique un pas {@code > 1}, les indices retenus sont strictement croissants
   * (donc distincts).
   *
   * @param sequences séquences candidates (triées défensivement)
   * @param taille nombre de séquences à retenir
   * @return les séquences retenues, ordre chronologique
   */
  public List<SequenceDEcoute> repartitionTemporelle(List<SequenceDEcoute> sequences, int taille) {
    Objects.requireNonNull(sequences, "sequences");
    List<SequenceDEcoute> triees = new ArrayList<>(sequences);
    triees.sort(CHRONOLOGIQUE);
    int n = triees.size();
    int k = Math.min(Math.max(taille, 0), n);
    if (k == 0) {
      return List.of();
    }
    if (k == 1) {
      return List.of(triees.get(n / 2));
    }
    if (k >= n) {
      return triees;
    }
    List<SequenceDEcoute> choisies = new ArrayList<>(k);
    for (int i = 0; i < k; i++) {
      int index = (int) Math.round((double) i * (n - 1) / (k - 1));
      choisies.add(triees.get(index));
    }
    return choisies;
  }

  /**
   * Échantillonnage aléatoire : retient {@code taille} séquences tirées au hasard, puis les
   * réordonne chronologiquement pour un affichage cohérent. Déterministe à {@link Random} fixé
   * (utile en test).
   *
   * @param sequences séquences candidates
   * @param taille nombre de séquences à retenir
   * @param alea source d'aléa (un {@code new Random(graine)} rend le résultat reproductible)
   * @return les séquences retenues, ordre chronologique
   */
  public List<SequenceDEcoute> aleatoire(List<SequenceDEcoute> sequences, int taille, Random alea) {
    Objects.requireNonNull(sequences, "sequences");
    Objects.requireNonNull(alea, "alea");
    List<SequenceDEcoute> copie = new ArrayList<>(sequences);
    int k = Math.min(Math.max(taille, 0), copie.size());
    if (k == 0) {
      return List.of();
    }
    Collections.shuffle(copie, alea);
    List<SequenceDEcoute> choisies = new ArrayList<>(copie.subList(0, k));
    choisies.sort(CHRONOLOGIQUE);
    return choisies;
  }
}
