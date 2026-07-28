package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.cli.model.RegistrePassages.LignePassage;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `traiter-passages` (#2357, clôture du lot 3) : l'équivalent en ligne de commande du menu « Traiter la
/// sélection ».
///
/// Ce que ces tests éprouvent est ce qui **distingue cette commande d'une boucle shell** : l'écart annoncé
/// sans être tenté, l'échec qui n'arrête pas les suivants, le compte rendu par passage, et des codes de
/// sortie sur lesquels un script peut brancher.
class TraiterPassagesTest {

    private final RegistrePassages registre = mock(RegistrePassages.class);

    /// Action doublée : écarte les identifiants de `aEcarter`, lève sur ceux de `aEchouer`.
    private static final class ActionDouble implements ActionGroupee {
        private final List<Long> aEcarter;
        private final List<Long> aEchouer;
        private final List<Long> executes = new ArrayList<>();
        private Function<CiblePassage, RuntimeException> leve = cible -> new IllegalStateException("disque plein");

        ActionDouble(List<Long> aEcarter, List<Long> aEchouer) {
            this.aEcarter = aEcarter;
            this.aEchouer = aEchouer;
        }

        @Override
        public String libelle() {
            return "Téléverser vers Vigie-Chiro";
        }

        @Override
        public Optional<String> motifNonEligible(CiblePassage cible) {
            return aEcarter.contains(cible.idPassage()) ? Optional.of("déjà déposé") : Optional.empty();
        }

        @Override
        public void executer(CiblePassage cible, JetonAnnulation jeton) {
            executes.add(cible.idPassage());
            if (aEchouer.contains(cible.idPassage())) {
                throw leve.apply(cible);
            }
        }
    }

    private static LignePassage ligne(long id, String point) {
        return new LignePassage(id, "640380", point, 2026, 1, StatutWorkflow.VERIFIE, Verdict.OK);
    }

    /// La commande avec la seule action « téléverser » branchée ; les trois autres absentes.
    private CommandLine ligneDeCommande(Optional<ActionGroupee> televerser, StringWriter sortie, StringWriter erreur) {
        CommandLine ligne = new CommandLine(
                new TraiterPassages(registre, Optional.empty(), televerser, Optional.empty(), Optional.empty()));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        return ligne;
    }

    @Test
    @DisplayName("les écartés sont annoncés avec leur motif SANS être tentés, et le lot sort en 0")
    void ecartes_annonces_sans_etre_tentes() {
        when(registre.lister()).thenReturn(List.of(ligne(1, "A1"), ligne(2, "B2")));
        ActionDouble action = new ActionDouble(List.of(2L), List.of());
        StringWriter sortie = new StringWriter();

        int code = ligneDeCommande(Optional.of(action), sortie, new StringWriter())
                .execute("--action", "televerser", "--passage", "1", "--passage", "2");

        assertThat(action.executes).as("l'écarté n'est jamais tenté").containsExactly(1L);
        assertThat(sortie.toString()).contains("640380 / B2 / 2026 n°1 : écarté (déjà déposé)");
        assertThat(code)
                .as("rejouer un lot déjà traité est le cas idempotent, pas une erreur")
                .isZero();
    }

    @Test
    @DisplayName("un échec n'arrête pas les suivants, et fait sortir en 1")
    void un_echec_n_arrete_pas_les_suivants() {
        when(registre.lister()).thenReturn(List.of(ligne(1, "A1"), ligne(2, "B2")));
        ActionDouble action = new ActionDouble(List.of(), List.of(1L));
        StringWriter sortie = new StringWriter();

        int code = ligneDeCommande(Optional.of(action), sortie, new StringWriter())
                .execute("--action", "televerser", "--passage", "1", "--passage", "2");

        assertThat(action.executes).containsExactly(1L, 2L);
        assertThat(sortie.toString()).contains("échec : disque plein").contains("640380 / B2 / 2026 n°1 : fait");
        assertThat(code).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR 2635 : un refus d'environnement porte la COMMANDE à taper, jamais un chemin de menu")
    void refus_d_environnement_porte_la_commande() {
        when(registre.lister()).thenReturn(List.of(ligne(1, "A1")));
        ActionDouble action = new ActionDouble(List.of(), List.of(1L));
        action.leve = cible ->
                new RegleMetierException("L'application n'est pas connectée à Vigie-Chiro.", new Besoin.Connexion());
        StringWriter sortie = new StringWriter();

        ligneDeCommande(Optional.of(action), sortie, new StringWriter())
                .execute("--action", "televerser", "--passage", "1");

        assertThat(sortie.toString()).contains("vigiechiro connexion --token").doesNotContain("☰");
    }

    @Test
    @DisplayName("action indisponible : refus en 2, qui dit quelle fonctionnalité réactiver")
    void action_indisponible_sort_en_deux() {
        StringWriter erreur = new StringWriter();

        int code = ligneDeCommande(Optional.empty(), new StringWriter(), erreur)
                .execute("--action", "televerser", "--passage", "1");

        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("Préparation du dépôt");
    }

    @Test
    @DisplayName("un identifiant inconnu arrête tout : traiter le reste en silence serait pire")
    void identifiant_inconnu_arrete_tout() {
        when(registre.lister()).thenReturn(List.of(ligne(1, "A1")));
        ActionDouble action = new ActionDouble(List.of(), List.of());
        StringWriter erreur = new StringWriter();

        int code = ligneDeCommande(Optional.of(action), new StringWriter(), erreur)
                .execute("--action", "televerser", "--passage", "1", "--passage", "77");

        assertThat(action.executes).isEmpty();
        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("77");
    }

    @Test
    @DisplayName("--json : un tableau analysable, sans le journal qui le rendrait illisible")
    void json_analysable() {
        when(registre.lister()).thenReturn(List.of(ligne(1, "A1"), ligne(2, "B2")));
        ActionDouble action = new ActionDouble(List.of(2L), List.of());
        StringWriter sortie = new StringWriter();

        ligneDeCommande(Optional.of(action), sortie, new StringWriter())
                .execute("--action", "televerser", "--passage", "1", "--passage", "2", "--json");

        assertThat(sortie.toString().stripLeading()).startsWith("[");
        assertThat(sortie.toString())
                .contains("\"statut\": \"REUSSI\"")
                .contains("\"statut\": \"ECARTE\"")
                .contains("\"motif\": \"déjà déposé\"");
        assertThat(sortie.toString())
                .as("le journal ligne à ligne casserait l'analyse du JSON")
                .doesNotContain("Téléverser vers Vigie-Chiro…");
    }

    @Test
    @DisplayName("une action inconnue est refusée par picocli (exit 2), avec les valeurs attendues")
    void action_inconnue_refusee() {
        StringWriter erreur = new StringWriter();

        int code = ligneDeCommande(Optional.empty(), new StringWriter(), erreur)
                .execute("--action", "tout-refaire", "--passage", "1");

        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("preparer-depot");
    }

    @Test
    @DisplayName("l'ordre demandé est l'ordre traité : le compte rendu se relit à côté de la commande")
    void ordre_demande_est_ordre_traite() {
        when(registre.lister()).thenReturn(List.of(ligne(1, "A1"), ligne(2, "B2")));
        ActionDouble action = new ActionDouble(List.of(), List.of());

        ligneDeCommande(Optional.of(action), new StringWriter(), new StringWriter())
                .execute("--action", "televerser", "--passage", "2", "--passage", "1");

        assertThat(action.executes)
                .as("le registre trie par carré/point ; la commande, elle, suit l'utilisateur")
                .containsExactly(2L, 1L);
    }
}
