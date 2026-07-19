package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.SourceArchivesRegenerables;
import fr.univ_amu.iut.lot.model.SourceDepot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La **fenêtre bornée** du dépôt (#1995) : générer, téléverser, libérer, sans jamais laisser plus de
/// `FENETRE` archives sur le disque.
class FenetreDepotTest {

    private static final long PLAFOND = 600;
    private static final String PREFIXE = "Car640380-2026-Pass1-Z1";

    @Test
    @DisplayName("libérer une archive produite la retire du disque")
    void liberer_retire_l_archive_produite(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 3);
        String premier = source.identifiants().getFirst();
        Path archive = source.resoudre(premier).orElseThrow();
        assertThat(archive).exists();

        source.liberer(premier);

        assertThat(archive).doesNotExist();
    }

    @Test
    @DisplayName("une archive préexistante n'est PAS libérée : elle appartient à l'utilisateur")
    void archive_preexistante_est_preservee(@TempDir Path dossier) throws IOException {
        // L'utilisateur a généré ses archives à l'étape ② : elles servent au dépôt manuel. Ce n'est pas
        // au téléversement de les effacer dans son dos ; l'écran a une action dédiée pour ça.
        SourceArchivesRegenerables premiere = source(dossier, 3);
        String identifiant = premiere.identifiants().getFirst();
        Path archive = premiere.resoudre(identifiant).orElseThrow();

        SourceArchivesRegenerables seconde = source(dossier, 3); // nouvelle source : l'archive préexiste
        seconde.liberer(identifiant);

        assertThat(archive).exists();
    }

    @Test
    @DisplayName("libérer deux fois, ou libérer un identifiant inconnu, ne casse rien")
    void liberer_est_idempotent(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 2);
        String premier = source.identifiants().getFirst();
        source.resoudre(premier);

        source.liberer(premier);
        source.liberer(premier);
        source.liberer("inconnue.zip");

        assertThat(depot(dossier).resolve(premier)).doesNotExist();
    }

    @Test
    @DisplayName("le mode WAV ne libère rien : effacer les séquences détruirait la nuit")
    void mode_wav_ne_libere_rien(@TempDir Path dossier) throws IOException {
        Path sequence = dossier.resolve("seq_0.wav");
        Files.write(sequence, new byte[10]);
        SourceDepot wav = SourceDepot.desFichiers(List.of(sequence));

        wav.liberer("seq_0.wav");

        assertThat(sequence).as("la séquence d'origine doit survivre").exists();
    }

    @Test
    @DisplayName("la source ZIP borne le parallélisme à sa fenêtre ; le mode WAV ne borne pas")
    void parallelisme_borne_par_la_fenetre(@TempDir Path dossier) throws IOException {
        // Une archive en vol occupe le disque jusqu'à sa libération : on ne peut pas en téléverser cinq
        // de front si on n'en tolère que deux à la fois.
        assertThat(source(dossier, 5).parallelismeMax()).isEqualTo(2);
        assertThat(SourceDepot.desFichiers(List.of()).parallelismeMax()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("générer, libérer, régénérer : l'occupation ne dépasse jamais la fenêtre")
    void occupation_ne_depasse_pas_la_fenetre(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 6);
        int maximumObserve = 0;

        // Déroulé du pipeline : on matérialise, on compte, on libère — comme le feront les workers.
        for (String identifiant : source.identifiants()) {
            source.resoudre(identifiant);
            maximumObserve = Math.max(maximumObserve, archivesSurDisque(dossier));
            source.liberer(identifiant);
        }

        assertThat(source.nombreArchives())
                .as("le lot doit produire plus d'archives que la fenêtre")
                .isGreaterThan(2);
        assertThat(maximumObserve).isLessThanOrEqualTo(2);
        assertThat(archivesSurDisque(dossier)).isZero();
    }

    private static int archivesSurDisque(Path dossier) throws IOException {
        try (var contenu = Files.list(depot(dossier))) {
            return (int) contenu.filter(f -> f.toString().endsWith(".zip")).count();
        }
    }

    private static Path depot(Path dossier) throws IOException {
        Path depot = dossier.resolve("depot");
        Files.createDirectories(depot);
        return depot;
    }

    private static SourceArchivesRegenerables source(Path dossier, int nombre) throws IOException {
        List<Path> sequences = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            Path sequence = dossier.resolve("seq_" + i + ".wav");
            Files.write(sequence, new byte[250]);
            sequences.add(sequence);
        }
        return new SourceArchivesRegenerables(sequences, PREFIXE, depot(dossier), new CompacteurDepot(PLAFOND));
    }
}
