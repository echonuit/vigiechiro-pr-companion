package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.JournalMutations;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import javafx.beans.value.WritableValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le signal observable des mutations de données (#3541). Pas de TestFX : les `Property` JavaFX et
/// leurs listeners fonctionnent hors du fil applicatif, et l'exécuteur du fil d'affichage est
/// précisément ce que ce test fournit lui-même.
class RevisionDonneesTest {

    /// Exécuteur qui **retient** ce qu'on lui confie au lieu de l'exécuter : c'est ce qui permet de
    /// prouver que la mutation passe bien par lui, et non par une écriture directe de la propriété.
    private static final class FilRetenu implements Executor {

        private final Deque<Runnable> enAttente = new ArrayDeque<>();

        @Override
        public void execute(Runnable commande) {
            enAttente.add(commande);
        }

        void deroulerTout() {
            while (!enAttente.isEmpty()) {
                enAttente.poll().run();
            }
        }
    }

    @Test
    @DisplayName("une mutation validée fait avancer la révision")
    void une_mutation_fait_avancer_la_revision() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        long avant = revision.revisionProperty().get();

        revision.mutationStructurelleValidee();

        assertThat(revision.revisionProperty().get()).isGreaterThan(avant);
    }

    @Test
    @DisplayName("deux mutations donnent deux valeurs distinctes : un compteur, pas un drapeau")
    void deux_mutations_donnent_deux_valeurs() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);

        revision.mutationStructurelleValidee();
        long apresLaPremiere = revision.revisionProperty().get();
        revision.mutationStructurelleValidee();

        // Un drapeau qu'on lève et qu'on baisse laisserait ces deux valeurs égales, et le lecteur qui
        // a manqué la première mutation ne serait jamais réveillé par la seconde.
        assertThat(revision.revisionProperty().get()).isNotEqualTo(apresLaPremiere);
    }

    @Test
    @DisplayName("un lecteur est notifié une fois par mutation")
    void un_lecteur_est_notifie_une_fois_par_mutation() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        int[] reveils = {0};
        revision.revisionProperty().addListener((observable, avant, apres) -> reveils[0]++);

        revision.mutationStructurelleValidee();
        revision.mutationStructurelleValidee();
        revision.mutationStructurelleValidee();

        assertThat(reveils[0]).isEqualTo(3);
    }

    @Test
    @DisplayName("la mutation passe par l'exécuteur fourni, elle n'écrit pas la propriété en direct")
    void la_mutation_passe_par_l_executeur() {
        FilRetenu fil = new FilRetenu();
        RevisionDonnees revision = new RevisionDonnees(fil);

        revision.mutationStructurelleValidee();

        // Tant que le fil d'affichage n'a pas déroulé, rien n'a bougé : c'est ce qui distingue un
        // report du fil d'une écriture directe, et c'est ce qui rendra le signal légal depuis un fil
        // d'arrière-plan.
        assertThat(revision.revisionProperty().get()).isZero();

        fil.deroulerTout();

        assertThat(revision.revisionProperty().get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("la révision est en lecture seule : un lecteur ne peut pas la faire avancer")
    void la_revision_est_en_lecture_seule() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);

        assertThat((Object) revision.revisionProperty()).isNotInstanceOf(WritableValue.class);
    }

    @Test
    @DisplayName("un service ne connaît que le port, jamais la révision ni JavaFX")
    void un_service_ne_connait_que_le_port() {
        RevisionDonnees revision = new RevisionDonnees(Runnable::run);
        // Ce que voit un service de `model` : le port, et rien d'autre. Le typage est l'assertion.
        JournalMutations journal = revision;

        journal.mutationStructurelleValidee();

        assertThat(revision.revisionProperty().get()).isEqualTo(1L);
    }
}
