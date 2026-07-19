package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.lot.model.CompacteurDepot;
import fr.univ_amu.iut.lot.model.SourceArchivesRegenerables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// [SourceArchivesRegenerables] (#1994) : les identifiants viennent de la **partition**, pas du contenu
/// du dossier, et une archive absente est **reproduite** au lieu de faire basculer le dépôt en WAV.
///
/// Le plafond est volontairement minuscule (600 o) pour obtenir plusieurs archives sans écrire des
/// centaines de mégaoctets.
class SourceArchivesRegenerablesTest {

    private static final long PLAFOND = 600;
    private static final String PREFIXE = "Car640380-2026-Pass1-Z1";

    @Test
    @DisplayName("les identifiants décrivent tout le plan, même si aucune archive n'est sur le disque")
    void identifiants_sans_aucune_archive(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 3);

        // Rien n'a encore été écrit : les noms viennent de la partition (#1993).
        assertThat(source.identifiants())
                .hasSize(source.nombreArchives())
                .allSatisfy(identifiant ->
                        assertThat(identifiant).startsWith(PREFIXE + "-").endsWith(".zip"));
        assertThat(Files.list(depot(dossier)).toList()).isEmpty();
    }

    @Test
    @DisplayName("résoudre une archive absente la génère")
    void archive_absente_est_regeneree(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 3);
        String premier = source.identifiants().getFirst();

        Path archive = source.resoudre(premier).orElseThrow();

        assertThat(archive).exists();
        assertThat(archive.getFileName()).hasToString(premier);
    }

    @Test
    @DisplayName("une archive présente est réutilisée telle quelle, pas réécrite")
    void archive_presente_est_reutilisee(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 3);
        String premier = source.identifiants().getFirst();
        Path archive = source.resoudre(premier).orElseThrow();
        long ecritureInitiale = Files.getLastModifiedTime(archive).toMillis();

        assertThat(source.resoudre(premier)).contains(archive);
        assertThat(Files.getLastModifiedTime(archive).toMillis()).isEqualTo(ecritureInitiale);
    }

    @Test
    @DisplayName("l'archive régénérée contient exactement ce que contenait la première (déterminisme)")
    void regeneration_a_l_identique(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 3);
        String premier = source.identifiants().getFirst();
        Path archive = source.resoudre(premier).orElseThrow();
        List<String> avant = entrees(archive);

        // On libère l'archive, comme le fera la fenêtre bornée de #1995, puis on la redemande.
        Files.delete(archive);
        Path regeneree = source.resoudre(premier).orElseThrow();

        assertThat(entrees(regeneree)).isEqualTo(avant);
    }

    @Test
    @DisplayName("un identifiant hors plan ne se résout pas (le moteur en fait un échec d'unité)")
    void identifiant_hors_plan(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 2);

        assertThat(source.resoudre(PREFIXE + "-999.zip")).isEmpty();
        assertThat(source.resoudre("autre-chose.zip")).isEmpty();
        assertThat(source.resoudre(PREFIXE + "-pas-un-rang.zip")).isEmpty();
    }

    @Test
    @DisplayName("l'empreinte porte sur les séquences source, qui survivent à la libération des archives")
    void empreinte_sur_les_sequences(@TempDir Path dossier) throws IOException {
        SourceArchivesRegenerables source = source(dossier, 2);
        String empreinte = source.empreinte();

        // Libérer toutes les archives ne change pas l'empreinte : elle ne dépend que des sources.
        for (String identifiant : source.identifiants()) {
            Files.deleteIfExists(depot(dossier).resolve(identifiant));
        }

        assertThat(source.empreinte()).isEqualTo(empreinte);
    }

    private static List<String> entrees(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.stream().map(entree -> entree.getName()).sorted().toList();
        }
    }

    private static Path depot(Path dossier) throws IOException {
        Path depot = dossier.resolve("depot");
        Files.createDirectories(depot);
        return depot;
    }

    /// Une source sur `nombre` séquences de 250 o : avec un plafond de 600 o, la partition en produit
    /// plusieurs.
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
