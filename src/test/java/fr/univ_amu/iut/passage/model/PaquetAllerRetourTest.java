package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'aller-retour complet d'un paquet : planifié, écrit, rouvert, relu (#4705, tâche 3.3).
///
/// La nuit porte cinq séquences et la sélection en tire deux. Ce que le paquet **ne porte pas** est
/// aussi important que ce qu'il porte : la sélection est figée (ADR 4627), donc emporter la nuit
/// entière rouvrirait la régénération que cette décision ferme.
class PaquetAllerRetourTest {

    private static final ProfilVigieChiro RELECTEUR =
            new ProfilVigieChiro("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur");

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Le paquet emporte la sélection et ses verdicts, et rien de la nuit qui reste")
    void le_paquet_emporte_la_selection_ses_verdicts_et_rien_d_autre() throws IOException {
        Path nuit = Files.createDirectory(dossier.resolve("nuit"));
        for (int i = 1; i <= 5; i++) {
            Files.writeString(nuit.resolve("seq-" + i + ".wav"), "contenu " + i);
        }
        Files.writeString(nuit.resolve("brut.wav"), "un enregistrement brut, qui reste au poste");

        List<Path> selection = List.of(nuit.resolve("seq-2.wav"), nuit.resolve("seq-4.wav"));
        ManifestePaquet manifeste = new ManifestePaquet(
                "640380",
                "A1",
                2026,
                1,
                MethodeSelection.REPARTITION_TEMPORELLE,
                List.of(
                        new ManifestePaquet.SequenceEmportee("seq-2.wav", 0, VerdictFichier.BON),
                        new ManifestePaquet.SequenceEmportee("seq-4.wav", 1, VerdictFichier.NON_JUGE)));

        Path paquet = dossier.resolve("nuit.zip");
        PlanDePaquet plan = PlanDePaquet.pour(paquet, manifeste.texte(), selection);
        assertThat(plan.avertissements())
                .as("rien d'illisible : le plan peut être exécuté")
                .isEmpty();
        EcrivainPaquet.ecrire(paquet, plan, manifeste.texte(), selection);

        PaquetOuvert ouvert = OuvertureDePaquet.ouvrir(paquet, Optional.of(RELECTEUR));

        assertThat(ouvert.sequences())
                .as("les deux séquences de la sélection, et pas les trois autres ni le brut")
                .containsExactly("sequences/seq-2.wav", "sequences/seq-4.wav");

        ManifestePaquet relu = ManifestePaquet.depuis(ouvert.manifeste());
        assertThat(relu.carre()).isEqualTo("640380");
        assertThat(relu.nuit()).isEqualTo(1);
        assertThat(relu.sequences())
                .as("les verdicts déjà posés voyagent, sans quoi le relecteur jugerait à l'aveugle")
                .containsExactlyElementsOf(manifeste.sequences());
    }

    @Test
    @DisplayName("Le plan annonce le manifeste enrichi à son vrai poids, sans être modifié pour cela")
    void le_plan_annonce_le_manifeste_enrichi_a_son_vrai_poids() throws IOException {
        Path sequence = Files.writeString(dossier.resolve("seq-1.wav"), "x".repeat(1_500));
        ManifestePaquet manifeste = new ManifestePaquet(
                "640380",
                "A1",
                2026,
                1,
                MethodeSelection.MANUEL,
                List.of(new ManifestePaquet.SequenceEmportee("seq-1.wav", 0, VerdictFichier.BON)));

        PlanDePaquet plan = PlanDePaquet.pour(dossier.resolve("p.zip"), manifeste.texte(), List.of(sequence));

        assertThat(plan.octetsParNature().get(NatureDEntree.METADONNEES))
                .as("le plan pèse le manifeste tel qu'il est, donc l'enrichir ne l'a pas rendu menteur")
                .isEqualTo(manifeste.texte().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
}
