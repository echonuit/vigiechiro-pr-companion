package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que le manifeste d'un paquet dit de ce que le paquet emporte (#4705, ADR 4517 et ADR 4627).
///
/// Sans les verdicts déjà posés, le relecteur jugerait à l'aveugle et l'avis qui revient n'aurait
/// rien à confronter, ce qui viderait de son sens le patron à deux colonnes de l'ADR 4517.
class ManifestePaquetTest {

    private static final List<ManifestePaquet.SequenceEmportee> SEQUENCES = List.of(
            new ManifestePaquet.SequenceEmportee("seq-1.wav", 0, VerdictFichier.BON),
            new ManifestePaquet.SequenceEmportee("seq-2.wav", 1, VerdictFichier.MAUVAIS),
            new ManifestePaquet.SequenceEmportee("seq-3.wav", 2, VerdictFichier.NON_JUGE));

    private static ManifestePaquet unManifeste() {
        return new ManifestePaquet("640380", "A1", 2026, 1, MethodeSelection.REPARTITION_TEMPORELLE, SEQUENCES);
    }

    @Test
    @DisplayName("Ce que le manifeste écrit se relit : les métadonnées de la nuit et la méthode")
    void les_metadonnees_de_la_nuit_se_relisent() {
        ManifestePaquet relu = ManifestePaquet.depuis(unManifeste().texte());

        assertThat(relu.carre()).isEqualTo("640380");
        assertThat(relu.point()).isEqualTo("A1");
        assertThat(relu.annee()).isEqualTo(2026);
        assertThat(relu.nuit()).isEqualTo(1);
        assertThat(relu.methode())
                .as("le relecteur doit pouvoir lire comment la sélection a été tirée")
                .isEqualTo(MethodeSelection.REPARTITION_TEMPORELLE);
    }

    @Test
    @DisplayName("Les verdicts déjà posés voyagent, y compris celui qui dit « pas encore jugé »")
    void les_verdicts_deja_poses_voyagent() {
        ManifestePaquet relu = ManifestePaquet.depuis(unManifeste().texte());

        assertThat(relu.sequences())
                .as("l'ordre de la sélection est un contenu, pas une commodité")
                .containsExactlyElementsOf(SEQUENCES);
        assertThat(relu.sequences().get(2).verdict())
                .as("« non jugé » est une information : l'absence ne se devine pas")
                .isEqualTo(VerdictFichier.NON_JUGE);
    }

    @Test
    @DisplayName("Un nom de fichier qui porte une virgule ou un guillemet survit au voyage")
    void un_nom_de_fichier_hostile_survit() {
        ManifestePaquet manifeste = new ManifestePaquet(
                "640380",
                "A1",
                2026,
                1,
                MethodeSelection.MANUEL,
                List.of(new ManifestePaquet.SequenceEmportee("un \"drôle\", de nom.wav", 0, VerdictFichier.BON)));

        assertThat(ManifestePaquet.depuis(manifeste.texte()).sequences())
                .containsExactlyElementsOf(manifeste.sequences());
    }

    @Test
    @DisplayName("Un nom qui porte un crochet fermant ne tronque pas le tableau qui le contient")
    void un_nom_qui_porte_un_crochet_ne_tronque_pas_le_tableau() {
        ManifestePaquet manifeste = new ManifestePaquet(
                "640380",
                "A1",
                2026,
                1,
                MethodeSelection.MANUEL,
                List.of(
                        new ManifestePaquet.SequenceEmportee("seq[1].wav", 0, VerdictFichier.BON),
                        new ManifestePaquet.SequenceEmportee("seq-2.wav", 1, VerdictFichier.MAUVAIS)));

        assertThat(ManifestePaquet.depuis(manifeste.texte()).sequences())
                .as("le crochet n'est pas un caractère échappé en JSON : il ne doit pas clore le tableau")
                .containsExactlyElementsOf(manifeste.sequences());
    }

    @Test
    @DisplayName("Un nom porteur d'un saut de ligne ou d'un caractère de contrôle revient intact")
    void un_nom_porteur_de_caracteres_echappes_revient_intact() {
        String hostile = "sauté\nà la ligne\ttabulé\u0001pilote\\barre";
        ManifestePaquet manifeste = new ManifestePaquet(
                "640380",
                "A1",
                2026,
                1,
                MethodeSelection.MANUEL,
                List.of(new ManifestePaquet.SequenceEmportee(hostile, 0, VerdictFichier.BON)));

        assertThat(ManifestePaquet.depuis(manifeste.texte()).sequences().get(0).nomFichier())
                .as("les cinq échappements de JsonSimple et la forme \\uXXXX doivent tous s'inverser")
                .isEqualTo(hostile);
    }

    @Test
    @DisplayName("Un manifeste coupé en plein tableau est refusé, et le refus dit qu'il n'est pas fermé")
    void un_manifeste_coupe_en_plein_tableau_est_refuse() {
        String complet = unManifeste().texte();
        String coupe = complet.substring(0, complet.indexOf("\"positions\"") + 20);

        assertThatThrownBy(() -> ManifestePaquet.depuis(coupe))
                .as("un fichier tronqué se dit, il ne se complète pas")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fermé");
    }

    @Test
    @DisplayName("Le manifeste dit qui a jugé, sans quoi l'avis revient anonyme")
    void le_manifeste_dit_qui_a_juge() {
        ManifestePaquet signe =
                new ManifestePaquet("640380", "A1", 2026, 1, MethodeSelection.MANUEL, "chiro-pierre", SEQUENCES);

        ManifestePaquet relu = ManifestePaquet.depuis(signe.texte());

        assertThat(relu.pseudoJugeur())
                .as("l'expéditeur qui ouvre un retour lirait son propre nom, sinon : le pseudo doit voyager")
                .isEqualTo("chiro-pierre");
        assertThat(relu.sequences()).containsExactlyElementsOf(SEQUENCES);
    }

    @Test
    @DisplayName("Un manifeste que personne n'a jugé le dit, plutôt que de nommer quelqu'un au hasard")
    void un_manifeste_sans_jugeur_le_dit() {
        ManifestePaquet relu = ManifestePaquet.depuis(unManifeste().texte());

        assertThat(relu.pseudoJugeur())
                .as("une nuit emportée avant tout jugement n'a pas de jugeur, et ce n'est pas une anomalie")
                .isNull();
    }

    @Test
    @DisplayName("Un manifeste de retour ne porte aucune séquence, et se relit quand même")
    void un_manifeste_de_retour_sans_sequence_se_relit() {
        ManifestePaquet retour = new ManifestePaquet(
                "640380", "A1", 2026, 1, MethodeSelection.RECUE_D_UN_PAQUET, "chiro-pierre", List.of());

        ManifestePaquet relu = ManifestePaquet.depuis(retour.texte());

        assertThat(relu.sequences())
                .as("le retour porte un avis, pas une nuit : l'expéditeur a déjà les séquences")
                .isEmpty();
        assertThat(relu.pseudoJugeur()).isEqualTo("chiro-pierre");
    }

    @Test
    @DisplayName("Un manifeste illisible est refusé en nommant sa cause, jamais rendu vide")
    void un_manifeste_illisible_est_refuse() {
        assertThatThrownBy(() -> ManifestePaquet.depuis("ceci n'est pas un manifeste"))
                .as("rendre un manifeste vide ferait passer un paquet corrompu pour un paquet sans séquence")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifeste");
    }

    @Test
    @DisplayName("Un manifeste dont les colonnes se désaccordent est refusé plutôt que deviné")
    void un_manifeste_desaccorde_est_refuse() {
        String ampute = unManifeste().texte().replace("\"positions\":[\"0\",\"1\",\"2\"]", "\"positions\":[\"0\"]");

        assertThatThrownBy(() -> ManifestePaquet.depuis(ampute))
                .as("trois séquences et une position : deviner laquelle serait inventer")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
