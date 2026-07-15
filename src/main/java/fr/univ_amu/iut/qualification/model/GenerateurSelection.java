package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/// Moteur de constitution d'une sélection d'écoute (R12). À partir de l'ensemble des
/// séquences d'une nuit, il en retient un sous-ensemble selon une [MethodeSelection] et une
/// taille cible.
///
/// **Pourquoi un moteur séparé du service ?** La logique de « comment choisir les séquences »
/// est une règle pure (pas de base, pas d'IHM) : on l'isole pour la tester sans persistance
/// et la réutiliser (objectif réutilisation O6). C'est `ServiceQualification` qui l'alimente
/// avec les séquences lues en base et persiste le résultat.
///
/// **« Réparti uniformément sur la nuit, par horodatage de l'original source » (R12).** Les
/// conventions de nommage R6/R7/R8 garantissent que le nom de fichier d'une séquence vaut
/// `Car<carré>-<année>-Pass<n>-<point>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>_NNN.wav`. À
/// l'intérieur d'une même session, le préfixe et le numéro de série sont constants : la
/// seule partie variable est l'horodatage de l'enregistreur (zéro-paddé, largeur fixe) suivi
/// de l'index de tranche. **L'ordre lexicographique du nom de fichier coïncide donc
/// exactement avec l'ordre chronologique de l'original source** (c'est d'ailleurs la
/// convention déjà retenue par `SequenceDao#findBySession`, qui trie par `file_name`). Le
/// moteur trie défensivement sur le nom de fichier puis échantillonne, sans avoir à parser
/// l'horodatage.
///
/// **Déterminisme.** La méthode [MethodeSelection#REPARTITION_TEMPORELLE] (défaut R12) est
/// strictement déterministe : pour les mêmes séquences et la même taille, elle renvoie
/// toujours la même sélection. La méthode [MethodeSelection#ALEATOIRE] dépend d'un [Random]
/// (injectable pour les tests via [#aleatoire(List, int, Random)]). La méthode
/// [MethodeSelection#MANUEL] considère la liste fournie comme le choix explicite de
/// l'utilisateur.
public class GenerateurSelection {

    /// Borne basse conseillée pour la taille d'une sélection (R12 : « 10 à 30 séquences »).
    public static final int TAILLE_MIN = 10;

    /// Borne haute conseillée pour la taille d'une sélection (R12 : « 10 à 30 séquences »).
    public static final int TAILLE_MAX = 30;

    /// Taille par défaut à l'ouverture de la vue de vérification (dans la fourchette R12).
    public static final int TAILLE_DEFAUT = 20;

    /// Durée réelle minimale (en secondes, au rythme d'acquisition) pour qu'une séquence entre
    /// dans l'échantillon d'écoute (#1507). Une tranche complète dure ≈ 5 s (C8) ; les fragments
    /// de fin de fichier observés en recette (0,1 s, 0,6 s, 1,1 s) ne contiennent que du bruit de
    /// fond, sans signal exploitable. Le seuil de 2 s les écarte tous avec une marge confortable
    /// des deux côtés : bien au-dessus de la plus longue troncature observée (1,1 s), bien
    /// en-dessous d'une tranche pleine (5 s). Les séquences écartées **restent dans le passage**
    /// (elles partent au dépôt) : seule la sélection d'écoute les ignore.
    public static final double DUREE_MINIMALE_ECOUTABLE_SECONDES = 2.0;

    /// Ordre chronologique = ordre lexicographique du nom de fichier (cf. Javadoc de classe).
    private static final Comparator<SequenceDEcoute> CHRONOLOGIQUE =
            Comparator.comparing(SequenceDEcoute::nomFichier, Comparator.nullsLast(Comparator.naturalOrder()));

    /// Constitue la liste ordonnée des séquences retenues pour une sélection.
    ///
    /// La taille effective vaut `min(taille, nombre de séquences disponibles)` : si la nuit
    /// compte moins de séquences que demandé, on retient tout ce qui existe (« 10 à 30 » suppose
    /// un volume suffisant). La taille demandée n'est pas plafonnée à [#TAILLE_MAX] car elle est
    /// configurable au-delà (cf. P3 : l'utilisateur peut monter à 50).
    ///
    /// **Séquences tronquées (#1507).** Pour les méthodes automatiques, les fragments de fin de
    /// fichier (durée connue sous [#DUREE_MINIMALE_ECOUTABLE_SECONDES]) sont écartés de
    /// l'échantillon avant tirage : ils ne portent aucun signal exploitable. La méthode
    /// [MethodeSelection#MANUEL] respecte le choix explicite de l'utilisateur et ne filtre pas.
    ///
    /// @param sequencesDeLaNuit toutes les séquences d'écoute de la nuit (ordre quelconque)
    /// @param methode méthode de constitution (R12)
    /// @param taille taille cible (≥ 1)
    /// @return les séquences retenues, ordonnées chronologiquement (par nom de fichier)
    /// @throws IllegalArgumentException si `taille < 1`
    public List<SequenceDEcoute> selectionner(
            List<SequenceDEcoute> sequencesDeLaNuit, MethodeSelection methode, int taille) {
        Objects.requireNonNull(sequencesDeLaNuit, "sequencesDeLaNuit");
        Objects.requireNonNull(methode, "methode");
        if (taille < 1) {
            throw new IllegalArgumentException("La taille demandée doit être au moins 1 (reçu : " + taille + ").");
        }
        List<SequenceDEcoute> triees = new ArrayList<>(ecoutablesPour(methode, sequencesDeLaNuit));
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

    /// Échantillonnage temporel déterministe (R12, méthode par défaut) : retient `taille`
    /// séquences **réparties uniformément** de la première à la dernière de la nuit.
    ///
    /// Algorithme : après tri chronologique des `n` séquences, on retient les indices
    /// `round(i × (n-1) / (taille-1))` pour `i = 0 … taille-1`. La première et la dernière
    /// séquence de la nuit sont toujours incluses, les autres sont espacées régulièrement.
    /// Comme `taille < n` implique un pas `> 1`, les indices retenus sont strictement
    /// croissants (donc distincts).
    ///
    /// @param sequences séquences candidates (triées défensivement)
    /// @param taille nombre de séquences à retenir
    /// @return les séquences retenues, ordre chronologique
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

    /// Échantillonnage aléatoire : retient `taille` séquences tirées au hasard, puis les
    /// réordonne chronologiquement pour un affichage cohérent. Déterministe à [Random] fixé
    /// (utile en test).
    ///
    /// @param sequences séquences candidates
    /// @param taille nombre de séquences à retenir
    /// @param alea source d'aléa (un `new Random(graine)` rend le résultat reproductible)
    /// @return les séquences retenues, ordre chronologique
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

    /// Restreint les candidates aux séquences réellement écoutables pour les méthodes
    /// automatiques (#1507). MANUEL respecte le choix explicite de l'utilisateur et n'est pas
    /// filtré. Garde-fou : si le filtrage vidait un ensemble non vide (nuit intégralement
    /// tronquée, cas pathologique), on retombe sur l'ensemble complet plutôt que de renvoyer une
    /// sélection vide.
    private static List<SequenceDEcoute> ecoutablesPour(MethodeSelection methode, List<SequenceDEcoute> sequences) {
        if (methode == MethodeSelection.MANUEL) {
            return sequences;
        }
        List<SequenceDEcoute> ecoutables =
                sequences.stream().filter(GenerateurSelection::estEcoutable).toList();
        return ecoutables.isEmpty() ? sequences : ecoutables;
    }

    /// `true` si la séquence est assez longue pour être écoutée (durée ≥
    /// [#DUREE_MINIMALE_ECOUTABLE_SECONDES]) **ou** si sa durée est inconnue (`null`). Une durée
    /// manquante (jeux de test, imports antérieurs au calcul de durée) n'est pas une preuve de
    /// troncature : on ne l'écarte donc pas.
    private static boolean estEcoutable(SequenceDEcoute sequence) {
        Double duree = sequence.dureeSecondes();
        return duree == null || duree >= DUREE_MINIMALE_ECOUTABLE_SECONDES;
    }
}
