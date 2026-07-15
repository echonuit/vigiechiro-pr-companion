package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests purs (sans base) du moteur [GenerateurSelection] (R12).
///
/// Les séquences sont fabriquées en mémoire avec des noms de fichiers zéro-paddés dont
/// l'ordre lexicographique coïncide avec l'ordre chronologique (cf. invariant R6/R7/R8
/// documenté dans le moteur). On vérifie la répartition temporelle déterministe, le respect
/// de la fourchette 10-30, le caractère configurable de la taille, ainsi que les méthodes
/// Aléatoire et Manuel.
class GenerateurSelectionTest {

    private final GenerateurSelection generateur = new GenerateurSelection();

    /// Séquence factice dont le nom de fichier croît avec `t` (ordre = ordre chronologique).
    private static SequenceDEcoute sequence(int t) {
        return sequence(t, 5.0);
    }

    /// Variante avec durée réelle imposée (`null` = durée inconnue), pour exercer le filtrage
    /// des séquences tronquées (#1507).
    private static SequenceDEcoute sequence(int t, Double dureeSecondes) {
        String nom = String.format("Car040962-2026-Pass1-A1-PaRecPR1925492_20260620_%06d_000.wav", t);
        return new SequenceDEcoute(
                (long) (t + 1), nom, 1L, t, (double) t, dureeSecondes, "/ws/transformes/" + nom, false, 1L);
    }

    private static List<SequenceDEcoute> nuit(int taille) {
        return new ArrayList<>(IntStream.range(0, taille)
                .mapToObj(GenerateurSelectionTest::sequence)
                .toList());
    }

    @Test
    @DisplayName("R12 : RéparTemporel retient la taille demandée, première et dernière incluses")
    void repartition_temporelle_taille_et_bornes() {
        List<SequenceDEcoute> nuit = nuit(100);

        List<SequenceDEcoute> selection = generateur.selectionner(nuit, MethodeSelection.REPARTITION_TEMPORELLE, 20);

        assertThat(selection).hasSize(20);
        assertThat(selection.get(0)).as("première séquence de la nuit incluse").isEqualTo(nuit.get(0));
        assertThat(selection.get(19)).as("dernière séquence de la nuit incluse").isEqualTo(nuit.get(99));
        assertThat(selection).doesNotHaveDuplicates();
        assertThat(selection)
                .as("séquences retenues dans l'ordre chronologique")
                .isSortedAccordingTo((a, b) -> a.nomFichier().compareTo(b.nomFichier()));
    }

    @Test
    @DisplayName("R12 : RéparTemporel est déterministe, même si l'entrée est désordonnée")
    void repartition_temporelle_deterministe() {
        List<SequenceDEcoute> nuit = nuit(100);
        List<SequenceDEcoute> nuitMelangee = new ArrayList<>(nuit);
        Collections.shuffle(nuitMelangee, new Random(7));

        List<SequenceDEcoute> run1 = generateur.selectionner(nuit, MethodeSelection.REPARTITION_TEMPORELLE, 20);
        List<SequenceDEcoute> run2 = generateur.selectionner(nuitMelangee, MethodeSelection.REPARTITION_TEMPORELLE, 20);

        assertThat(run2).as("même résultat quel que soit l'ordre d'entrée").isEqualTo(run1);
    }

    @Test
    @DisplayName("R12 : la taille par défaut tombe dans la fourchette 10-30")
    void taille_par_defaut_dans_la_fourchette() {
        List<SequenceDEcoute> selection = generateur.selectionner(
                nuit(100), MethodeSelection.REPARTITION_TEMPORELLE, GenerateurSelection.TAILLE_DEFAUT);

        assertThat(selection.size()).isBetween(GenerateurSelection.TAILLE_MIN, GenerateurSelection.TAILLE_MAX);
    }

    @Test
    @DisplayName("La taille est configurable (15, 30…)")
    void taille_configurable() {
        assertThat(generateur.selectionner(nuit(100), MethodeSelection.REPARTITION_TEMPORELLE, 15))
                .hasSize(15);
        assertThat(generateur.selectionner(nuit(100), MethodeSelection.REPARTITION_TEMPORELLE, 30))
                .hasSize(30);
    }

    @Test
    @DisplayName("Moins de séquences que demandé : on retient tout ce qui existe")
    void moins_de_sequences_que_demande() {
        List<SequenceDEcoute> nuit = nuit(8);

        List<SequenceDEcoute> selection = generateur.selectionner(nuit, MethodeSelection.REPARTITION_TEMPORELLE, 20);

        assertThat(selection).hasSize(8).containsExactlyElementsOf(nuit);
    }

    @Test
    @DisplayName("Une taille < 1 est refusée")
    void taille_invalide_refusee() {
        assertThatThrownBy(() -> generateur.selectionner(nuit(10), MethodeSelection.REPARTITION_TEMPORELLE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Aléatoire à graine fixée est reproductible et trié chronologiquement")
    void aleatoire_reproductible_a_graine_fixee() {
        List<SequenceDEcoute> nuit = nuit(100);

        List<SequenceDEcoute> run1 = generateur.aleatoire(nuit, 12, new Random(42));
        List<SequenceDEcoute> run2 = generateur.aleatoire(nuit, 12, new Random(42));

        assertThat(run1).hasSize(12);
        assertThat(run2).isEqualTo(run1);
        assertThat(nuit).containsAll(run1);
        assertThat(run1).isSortedAccordingTo((a, b) -> a.nomFichier().compareTo(b.nomFichier()));
    }

    @Test
    @DisplayName("Manuel : la liste fournie est considérée comme le choix de l'utilisateur")
    void manuel_renvoie_le_choix_fourni() {
        List<SequenceDEcoute> choixUtilisateur = nuit(5);

        List<SequenceDEcoute> selection = generateur.selectionner(choixUtilisateur, MethodeSelection.MANUEL, 5);

        assertThat(selection).containsExactlyElementsOf(choixUtilisateur);
    }

    @Test
    @DisplayName("#1507 : les séquences tronquées (durée < 2 s) sont écartées de l'échantillon")
    void tronquees_ecartees_de_l_echantillon() {
        // Nuit mêlant tranches pleines (5 s) et fragments de fin de fichier (0,1 / 0,6 / 1,1 s).
        List<SequenceDEcoute> nuit = List.of(
                sequence(0, 5.0),
                sequence(1, 0.1),
                sequence(2, 5.0),
                sequence(3, 0.6),
                sequence(4, 5.0),
                sequence(5, 1.1),
                sequence(6, 5.0));

        List<SequenceDEcoute> selection = generateur.selectionner(nuit, MethodeSelection.REPARTITION_TEMPORELLE, 20);

        assertThat(selection)
                .as("seules les 4 tranches pleines subsistent")
                .containsExactly(sequence(0, 5.0), sequence(2, 5.0), sequence(4, 5.0), sequence(6, 5.0));
        assertThat(selection)
                .as("aucune séquence sous le seuil")
                .allMatch(s -> s.dureeSecondes() >= GenerateurSelection.DUREE_MINIMALE_ECOUTABLE_SECONDES);
    }

    @Test
    @DisplayName("#1507 : une durée inconnue (null) n'est pas une troncature, on la conserve")
    void duree_inconnue_conservee() {
        List<SequenceDEcoute> nuit =
                List.of(sequence(0, 5.0), sequence(1, (Double) null), sequence(2, 0.6), sequence(3, 5.0));

        List<SequenceDEcoute> selection = generateur.selectionner(nuit, MethodeSelection.REPARTITION_TEMPORELLE, 20);

        assertThat(selection)
                .as("la séquence de durée inconnue reste, seule la 0,6 s est écartée")
                .containsExactly(sequence(0, 5.0), sequence(1, (Double) null), sequence(3, 5.0));
    }

    @Test
    @DisplayName("#1507 : Manuel conserve les tronquées (choix explicite de l'utilisateur)")
    void manuel_conserve_les_tronquees() {
        List<SequenceDEcoute> choixUtilisateur = List.of(sequence(0, 5.0), sequence(1, 0.1), sequence(2, 1.1));

        List<SequenceDEcoute> selection = generateur.selectionner(choixUtilisateur, MethodeSelection.MANUEL, 3);

        assertThat(selection).containsExactlyElementsOf(choixUtilisateur);
    }

    @Test
    @DisplayName("#1507 : nuit intégralement tronquée : garde-fou, on retombe sur tout")
    void nuit_integralement_tronquee_retombe_sur_tout() {
        List<SequenceDEcoute> nuit = List.of(sequence(0, 0.1), sequence(1, 0.6), sequence(2, 1.1));

        List<SequenceDEcoute> selection = generateur.selectionner(nuit, MethodeSelection.REPARTITION_TEMPORELLE, 20);

        assertThat(selection)
                .as("plutôt qu'une sélection vide, on retient les 3 fragments")
                .hasSize(3);
    }
}
