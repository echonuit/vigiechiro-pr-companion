package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests du [MoteurTraitementGroupe] (#2357). L'action est un espion en mémoire : le moteur n'ayant
/// aucune dépendance métier, ces tests n'ont besoin ni de base, ni de réseau, ni de JavaFX.
class MoteurTraitementGroupeTest {

    private static final CiblePassage P1 = new CiblePassage(1L, "640380 / A1 / 2026 n°1");
    private static final CiblePassage P2 = new CiblePassage(2L, "640380 / A1 / 2026 n°2");
    private static final CiblePassage P3 = new CiblePassage(3L, "640381 / B1 / 2026 n°1");

    private final MoteurTraitementGroupe moteur = new MoteurTraitementGroupe();

    /// Action espionne : note les passages traités, écarte ceux que `inelig` désigne, échoue sur ceux
    /// que `echoue` désigne.
    private static final class ActionEspionne implements ActionGroupee {
        private final List<Long> traites = new ArrayList<>();
        private final Predicate<CiblePassage> inelig;
        private final Predicate<CiblePassage> echoue;
        private Runnable pendant = () -> {};

        ActionEspionne(Predicate<CiblePassage> inelig, Predicate<CiblePassage> echoue) {
            this.inelig = inelig;
            this.echoue = echoue;
        }

        @Override
        public String libelle() {
            return "Préparer le dépôt";
        }

        @Override
        public Optional<String> motifNonEligible(CiblePassage cible) {
            return inelig.test(cible) ? Optional.of("déjà déposé") : Optional.empty();
        }

        @Override
        public void executer(CiblePassage cible, JetonAnnulation jeton) {
            pendant.run();
            if (echoue.test(cible)) {
                throw new IllegalStateException("disque plein");
            }
            traites.add(cible.idPassage());
        }
    }

    private static ActionEspionne action() {
        return new ActionEspionne(cible -> false, cible -> false);
    }

    @Test
    @DisplayName("Nominal : chaque passage est traité, dans l'ordre, et rendu réussi")
    void nominal() {
        ActionEspionne action = action();

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(P1, P2, P3), JetonAnnulation.neutre());

        assertThat(action.traites).containsExactly(1L, 2L, 3L);
        assertThat(resultat.reussis()).isEqualTo(3);
        assertThat(resultat.interrompu()).isFalse();
        assertThat(resultat.resume()).isEqualTo("3/3 passage(s) traité(s)");
    }

    @Test
    @DisplayName("Les inéligibles sont écartés SANS être tentés, et gardent leur motif")
    void ineligibles_ecartes_avec_motif() {
        ActionEspionne action = new ActionEspionne(cible -> cible.equals(P2), cible -> false);

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(P1, P2, P3), JetonAnnulation.neutre());

        assertThat(action.traites).as("P2 n'a jamais été tenté").containsExactly(1L, 3L);
        assertThat(resultat.issues())
                .extracting(IssueTraitement::statut, IssueTraitement::motif)
                .containsExactly(
                        tuple(IssueTraitement.Statut.REUSSI, ""),
                        tuple(IssueTraitement.Statut.ECARTE, "déjà déposé"),
                        tuple(IssueTraitement.Statut.REUSSI, ""));
        assertThat(resultat.ecartes()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un échec n'arrête pas le lot : les suivants sont traités, le motif est conservé")
    void un_echec_n_arrete_pas_le_lot() {
        ActionEspionne action = new ActionEspionne(cible -> false, cible -> cible.equals(P1));

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(P1, P2, P3), JetonAnnulation.neutre());

        assertThat(action.traites)
                .as("le lot continue après l'échec du premier")
                .containsExactly(2L, 3L);
        assertThat(resultat.echecs()).isEqualTo(1);
        assertThat(resultat.reussis()).isEqualTo(2);
        assertThat(resultat.issues().getFirst().motif()).isEqualTo("disque plein");
    }

    @Test
    @DisplayName("#2357 : l'annulation s'arrête ENTRE deux passages — le passage courant va au bout")
    void annulation_apres_le_passage_courant() {
        JetonAnnulation jeton = new JetonAnnulation();
        ActionEspionne action = action();
        // Le premier passage demande l'arrêt PENDANT son exécution : il doit malgré tout aller au bout.
        action.pendant = jeton::annuler;

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(P1, P2, P3), jeton);

        assertThat(action.traites)
                .as("P1 est allé au bout ; P2 et P3 n'ont pas été touchés")
                .containsExactly(1L);
        assertThat(resultat.issues())
                .extracting(IssueTraitement::statut)
                .containsExactly(
                        IssueTraitement.Statut.REUSSI,
                        IssueTraitement.Statut.NON_TRAITE,
                        IssueTraitement.Statut.NON_TRAITE);
        assertThat(resultat.interrompu()).isTrue();
        assertThat(resultat.resume()).contains("interrompu");
    }

    @Test
    @DisplayName("Jeton déjà annulé : aucun passage n'est touché, tous sont rendus non traités")
    void jeton_deja_annule() {
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        ActionEspionne action = action();

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(P1, P2), jeton);

        assertThat(action.traites).isEmpty();
        assertThat(resultat.nonTraites()).isEqualTo(2);
        assertThat(resultat.interrompu()).isTrue();
    }

    @Test
    @DisplayName("Sélection vide : lot valide et vide, aucun appel")
    void selection_vide() {
        ActionEspionne action = action();

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(), JetonAnnulation.neutre());

        assertThat(resultat.issues()).isEmpty();
        assertThat(resultat.reussis()).isZero();
        assertThat(resultat.interrompu()).isFalse();
        assertThat(action.traites).isEmpty();
    }

    @Test
    @DisplayName("Sélection entièrement inéligible : rien n'est tenté, chaque motif est rendu")
    void selection_entierement_ineligible() {
        ActionEspionne action = new ActionEspionne(cible -> true, cible -> false);

        ResultatTraitementGroupe resultat = moteur.executer(action, List.of(P1, P2), JetonAnnulation.neutre());

        assertThat(action.traites).isEmpty();
        assertThat(resultat.ecartes()).isEqualTo(2);
        assertThat(resultat.issues())
                .allSatisfy(issue -> assertThat(issue.motif()).isEqualTo("déjà déposé"));
    }

    @Test
    @DisplayName("#2357 : chaque ligne de journal identifie son passage")
    void journal_prefixe_par_passage() {
        List<String> journal = new ArrayList<>();
        ActionEspionne action = new ActionEspionne(cible -> cible.equals(P2), cible -> cible.equals(P3));

        moteur.executer(action, List.of(P1, P2, P3), JetonAnnulation.neutre(), journal::add);

        assertThat(journal)
                .as("aucune ligne anonyme : un lot interrompu doit rester diagnosticable")
                .allSatisfy(ligne -> assertThat(ligne).startsWith("["));
        assertThat(journal).anyMatch(ligne -> ligne.startsWith("[640380 / A1 / 2026 n°2] écarté : déjà déposé"));
        assertThat(journal).anyMatch(ligne -> ligne.startsWith("[640381 / B1 / 2026 n°1] ÉCHEC : disque plein"));
    }

    @Test
    @DisplayName("Une cible sans désignation est refusée : le journal doit rester lisible")
    void cible_sans_designation_refusee() {
        assertThatThrownBy(() -> new CiblePassage(1L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nommable");
    }
}
