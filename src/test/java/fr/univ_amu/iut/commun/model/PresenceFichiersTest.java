package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.PresenceFichiers.Presence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Balayage groupé de présence sur de vrais fichiers sous `@TempDir` (le workspace). Les chemins
/// hors workspace n'ont pas besoin d'exister : c'est précisément leur absence qui est classée.
class PresenceFichiersTest {

    @TempDir
    Path workspace;

    private PresenceFichiers presence() {
        return new PresenceFichiers(new Workspace(workspace));
    }

    @Test
    @DisplayName("Fichier présent sous le workspace : PRESENTE")
    void fichier_present() throws IOException {
        Path fichier = Files.write(workspace.resolve("present.wav"), new byte[8]);

        Map<String, Presence> verdicts = presence().evaluer(List.of(fichier.toString()));

        assertThat(verdicts).containsEntry(fichier.toString(), Presence.PRESENTE);
    }

    @Test
    @DisplayName("Fichier absent sous le workspace : ABSENTE")
    void fichier_absent_sous_workspace() {
        String chemin = workspace.resolve("disparu.wav").toString();

        assertThat(presence().evaluer(List.of(chemin))).containsEntry(chemin, Presence.ABSENTE);
    }

    @Test
    @DisplayName("Fichier hors workspace introuvable : EXTERNE_INTROUVABLE (média non monté ?)")
    void fichier_externe_introuvable() {
        String chemin = "/media/carte-sd-debranchee/nuit1/original.wav";

        assertThat(presence().evaluer(List.of(chemin))).containsEntry(chemin, Presence.EXTERNE_INTROUVABLE);
    }

    @Test
    @DisplayName("Chemins null ou blancs : ignorés, absents du résultat")
    void chemins_null_ou_blancs_ignores() {
        List<String> chemins = new ArrayList<>();
        chemins.add(null);
        chemins.add("   ");

        assertThat(presence().evaluer(chemins)).isEmpty();
    }

    @Test
    @DisplayName("Les clés du résultat sont les chaînes reçues telles quelles, même non normalisées")
    void cles_telles_quelles() throws IOException {
        Files.write(workspace.resolve("present.wav"), new byte[8]);
        String nonNormalise = workspace + "/./present.wav";

        assertThat(presence().evaluer(List.of(nonNormalise))).containsEntry(nonNormalise, Presence.PRESENTE);
    }

    @Test
    @DisplayName("Un seul balayage par dossier, quel que soit le nombre de fichiers évalués")
    void un_seul_balayage_par_dossier() throws IOException {
        Path transformes = Files.createDirectories(workspace.resolve("session").resolve("transformes"));
        List<String> chemins = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            chemins.add(Files.write(transformes.resolve("seq_" + i + ".wav"), new byte[8])
                    .toString());
        }
        chemins.add(transformes.resolve("disparu.wav").toString());
        AtomicInteger acces = new AtomicInteger();
        PresenceFichiers compte = new PresenceFichiers(new Workspace(workspace), dossier -> {
            acces.incrementAndGet();
            return nomsReels(dossier);
        });

        Map<String, Presence> verdicts = compte.evaluer(chemins);

        assertThat(acces).as("accès disque pour 51 fichiers d'un même dossier").hasValue(1);
        assertThat(verdicts).hasSize(51);
        assertThat(verdicts.values().stream()
                        .filter(v -> v == Presence.PRESENTE)
                        .count())
                .isEqualTo(50);
    }

    @Test
    @DisplayName("Deux dossiers distincts : un balayage chacun")
    void un_balayage_par_dossier_distinct() throws IOException {
        Path a = Files.createDirectories(workspace.resolve("a"));
        Path b = Files.createDirectories(workspace.resolve("b"));
        Path dansA = Files.write(a.resolve("x.wav"), new byte[8]);
        Path dansB = Files.write(b.resolve("y.wav"), new byte[8]);
        AtomicInteger acces = new AtomicInteger();
        PresenceFichiers compte = new PresenceFichiers(new Workspace(workspace), dossier -> {
            acces.incrementAndGet();
            return nomsReels(dossier);
        });

        compte.evaluer(List.of(dansA.toString(), dansB.toString()));

        assertThat(acces).hasValue(2);
    }

    /// Balayage réel réutilisé par les tests à compteur (même contrat que le balayeur par défaut).
    private static Set<String> nomsReels(Path dossier) {
        if (!Files.isDirectory(dossier)) {
            return Set.of();
        }
        try (Stream<Path> enfants = Files.list(dossier)) {
            return enfants.map(e -> e.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }
}
