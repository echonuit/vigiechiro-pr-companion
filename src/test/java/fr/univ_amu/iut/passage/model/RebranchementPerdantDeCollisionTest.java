package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Un perdant de collision absent du dossier n'est pas un fichier manquant** (#5720).
///
/// Kaleidoscope ne produit que des `_000` : un dossier qu'il a découpé ne contient aucun des `_001` que
/// l'import a écrits pour les tranches ayant perdu une collision. Les annoncer « aucun fichier de ce nom
/// dans le dossier » envoie l'utilisateur chercher un fichier qui n'a jamais été sur son disque.
class RebranchementPerdantDeCollisionTest {

    private static final String BASE = "Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_";
    private static final String PERDANT = BASE + "205342_001.wav";
    private static final String INTROUVABLE = BASE + "205332_000.wav";
    private static final String INDEX_SANS_HORODATAGE = "Car640380-2026-Pass2-Z1-essai_001.wav";
    private static final String AUCUN_FICHIER = "aucun fichier de ce nom dans le dossier";

    @Test
    @DisplayName("#5720 : un perdant de collision absent du dossier n'est pas compté introuvable")
    void un_perdant_absent_n_est_pas_introuvable(@TempDir Path tmp) throws IOException {
        BilanReactivation bilan = rebrancherDepuisUnDossierVide(tmp);

        assertThat(bilan.absences)
                .as("le perdant ne doit plus sortir « %s »", AUCUN_FICHIER)
                .extracting(AbsenceReactivation::nomFichier)
                .doesNotContain(PERDANT);
        assertThat(bilan.perdants).as("il est compté à part, sous son nom").containsExactly(PERDANT);
        assertThat(bilan.manquantes)
                .as("et reste dans le total des séquences non revenues, perdant compris")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("#5720 : les autres absences gardent leur motif, nom sans horodatage compris")
    void les_autres_absences_gardent_leur_motif(@TempDir Path tmp) throws IOException {
        BilanReactivation bilan = rebrancherDepuisUnDossierVide(tmp);

        assertThat(bilan.absences)
                .extracting(AbsenceReactivation::nomFichier, AbsenceReactivation::motif)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(INTROUVABLE, AUCUN_FICHIER),
                        org.assertj.core.groups.Tuple.tuple(INDEX_SANS_HORODATAGE, AUCUN_FICHIER));
    }

    /// Un dossier désigné qui ne contient aucune des trois séquences : c'est l'absence qui se juge ici,
    /// pas la vérification d'un fichier présent.
    private static BilanReactivation rebrancherDepuisUnDossierVide(Path tmp) throws IOException {
        Path dossier = Files.createDirectories(tmp.resolve("dossier"));
        Path destination = Files.createDirectories(tmp.resolve("transformes"));
        List<SequenceDEcoute> sequences = List.of(
                attendue(1L, PERDANT, destination),
                attendue(2L, INTROUVABLE, destination),
                attendue(3L, INDEX_SANS_HORODATAGE, destination));
        return new RebranchementSequences(new VerificationIdentiteAudio(), Optional.empty())
                .rebrancher(
                        sequences,
                        CandidatsReactivation.dans(dossier),
                        RebranchementSequences.OrigineCandidats.DOSSIER,
                        RebranchementSequences.COPIE,
                        progres -> {});
    }

    private static SequenceDEcoute attendue(long id, String nom, Path destination) {
        return new SequenceDEcoute(
                id,
                nom,
                99L,
                null,
                null,
                null,
                destination.resolve(nom).toString(),
                false,
                7L,
                null,
                EmpreinteContenu.ABSENTE);
    }
}
