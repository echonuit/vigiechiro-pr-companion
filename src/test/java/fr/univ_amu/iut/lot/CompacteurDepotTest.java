package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.CompacteurDepot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests du [CompacteurDepot] (#110) : scission d'un lot en archives ZIP `<préfixe>-N.zip` sous un
/// plafond de taille (700 Mo en production, injecté petit ici pour exercer la scission sans écrire des
/// gigaoctets). Selon le cas, le contenu est **compressible** (octets répétés, pour la répartition) ou
/// **incompressible** (pseudo-aléatoire, pour vérifier la garantie « archive ≤ plafond » au pire cas).
class CompacteurDepotTest {

    private static final String PREFIXE = "Car640380-2026-Pass1-A1";

    @TempDir
    Path dossier;

    @Test
    @DisplayName("#110 : un lot dépassant le plafond produit N archives numérotées, chacune ≤ plafond")
    void scinde_en_archives_numerotees() throws IOException {
        // 7 fichiers de 30 Ko, plafond 100 Ko → au plus 3 par archive → 3 archives (3 + 3 + 1).
        Path src = Files.createDirectories(dossier.resolve("src"));
        List<Path> fichiers = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            fichiers.add(Files.write(src.resolve("seq_" + i + ".wav"), octets(30_000, (byte) i)));
        }
        Path sortie = dossier.resolve("depot");
        long plafond = 100_000;

        List<ArchiveDepot> archives = new CompacteurDepot(plafond).compacter(fichiers, PREFIXE, sortie);

        // 3 archives nommées d'après le préfixe + numéro croissant.
        assertThat(archives)
                .extracting(a -> a.chemin().getFileName().toString())
                .containsExactly(PREFIXE + "-1.zip", PREFIXE + "-2.zip", PREFIXE + "-3.zip");
        assertThat(archives).extracting(ArchiveDepot::numero).containsExactly(1, 2, 3);
        // Répartition gloutonne 3 + 3 + 1.
        assertThat(archives).extracting(ArchiveDepot::nombreFichiers).containsExactly(3, 3, 1);
        // Chaque archive reste sous le plafond.
        for (ArchiveDepot archive : archives) {
            assertThat(Files.size(archive.chemin()))
                    .as("taille de %s", archive.chemin().getFileName())
                    .isLessThanOrEqualTo(plafond);
        }
        // Tous les fichiers du lot sont présents, exactement une fois, à travers les archives.
        assertThat(toutesLesEntrees(archives))
                .containsExactlyInAnyOrderElementsOf(
                        fichiers.stream().map(p -> p.getFileName().toString()).toList());
    }

    @Test
    @DisplayName("#110 : données INCOMPRESSIBLES — chaque archive ZIP réelle reste ≤ plafond (en-têtes + DEFLATE)")
    void garantit_le_plafond_sur_donnees_incompressibles() throws IOException {
        // 5 fichiers de 33 300 o, plafond 100 000 o : un découpage naïf (somme des tailles) en mettrait 3
        // par archive (99 900 o) mais le ZIP réel (données non compressibles + en-têtes) dépasserait alors
        // le plafond. La majoration du coût doit donc n'en mettre que 2 et garantir l'archive ≤ plafond.
        Path src = Files.createDirectories(dossier.resolve("src"));
        List<Path> fichiers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fichiers.add(Files.write(src.resolve("alea_" + i + ".wav"), aleatoire(33_300, i)));
        }
        long plafond = 100_000;

        List<ArchiveDepot> archives =
                new CompacteurDepot(plafond).compacter(fichiers, PREFIXE, dossier.resolve("depot"));

        for (ArchiveDepot archive : archives) {
            assertThat(Files.size(archive.chemin()))
                    .as("taille réelle de %s", archive.chemin().getFileName())
                    .isLessThanOrEqualTo(plafond);
        }
        assertThat(toutesLesEntrees(archives))
                .containsExactlyInAnyOrderElementsOf(
                        fichiers.stream().map(p -> p.getFileName().toString()).toList());
    }

    @Test
    @DisplayName("#110 : un lot sous le plafond tient dans une seule archive préfixe-1.zip")
    void une_seule_archive_sous_le_plafond() throws IOException {
        Path f1 = Files.write(dossier.resolve("a.wav"), octets(10, (byte) 1));
        Path f2 = Files.write(dossier.resolve("b.wav"), octets(10, (byte) 2));

        List<ArchiveDepot> archives =
                new CompacteurDepot().compacter(List.of(f1, f2), PREFIXE, dossier.resolve("depot"));

        assertThat(archives).singleElement().satisfies(a -> {
            assertThat(a.chemin().getFileName()).hasToString(PREFIXE + "-1.zip");
            assertThat(a.numero()).isEqualTo(1);
            assertThat(a.nombreFichiers()).isEqualTo(2);
        });
        assertThat(toutesLesEntrees(archives)).containsExactlyInAnyOrder("a.wav", "b.wav");
    }

    @Test
    @DisplayName("#110 : un fichier plus gros que le plafond est refusé (indécoupable)")
    void fichier_trop_gros_refuse() throws IOException {
        Path gros = Files.write(dossier.resolve("gros.wav"), octets(2000, (byte) 0));

        assertThatThrownBy(() -> new CompacteurDepot(1000).compacter(List.of(gros), PREFIXE, dossier.resolve("depot")))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("plafond");
    }

    @Test
    @DisplayName("#769 : espace disque insuffisant → refus AVANT écriture, aucune archive laissée")
    void espace_disque_insuffisant_refuse_avant_ecriture() throws IOException {
        Path f1 = Files.write(dossier.resolve("a.wav"), octets(50_000, (byte) 1));
        Path f2 = Files.write(dossier.resolve("b.wav"), octets(50_000, (byte) 2));
        Path sortie = dossier.resolve("depot");
        // Disque simulé presque plein (10 o) : bien en dessous du volume estimé + marge de sécurité.
        CompacteurDepot compacteur = new CompacteurDepot(CompacteurDepot.TAILLE_MAX_DEFAUT_OCTETS, d -> 10L);

        assertThatThrownBy(() -> compacteur.compacter(List.of(f1, f2), PREFIXE, sortie))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Espace disque insuffisant");
        // Refus AVANT toute écriture : le dossier de sortie a été créé mais reste vide (pas de .zip partiel).
        try (var flux = Files.list(sortie)) {
            assertThat(flux).isEmpty();
        }
    }

    @Test
    @DisplayName("#769 : espace disque suffisant → la génération se déroule normalement")
    void espace_disque_suffisant_genere_les_archives() throws IOException {
        Path f1 = Files.write(dossier.resolve("a.wav"), octets(10, (byte) 1));
        Path f2 = Files.write(dossier.resolve("b.wav"), octets(10, (byte) 2));
        CompacteurDepot compacteur = new CompacteurDepot(CompacteurDepot.TAILLE_MAX_DEFAUT_OCTETS, d -> Long.MAX_VALUE);

        List<ArchiveDepot> archives = compacteur.compacter(List.of(f1, f2), PREFIXE, dossier.resolve("depot"));

        assertThat(archives)
                .singleElement()
                .satisfies(a -> assertThat(a.nombreFichiers()).isEqualTo(2));
        assertThat(toutesLesEntrees(archives)).containsExactlyInAnyOrder("a.wav", "b.wav");
    }

    private static byte[] octets(int taille, byte valeur) {
        byte[] b = new byte[taille];
        Arrays.fill(b, valeur); // compressible : DEFLATE réduit fortement
        return b;
    }

    /// Octets **pseudo-aléatoires déterministes** (LCG par graine) : incompressibles, donc DEFLATE
    /// n'aide pas — on exerce ainsi le pire cas pour la garantie « archive ≤ plafond ».
    private static byte[] aleatoire(int taille, int graine) {
        byte[] b = new byte[taille];
        long etat = graine * 0x9E3779B97F4A7C15L + 1;
        for (int j = 0; j < taille; j++) {
            etat = etat * 6364136223846793005L + 1442695040888963407L;
            b[j] = (byte) (etat >>> 56);
        }
        return b;
    }

    private static List<String> toutesLesEntrees(List<ArchiveDepot> archives) throws IOException {
        List<String> noms = new ArrayList<>();
        for (ArchiveDepot archive : archives) {
            try (ZipFile zip = new ZipFile(archive.chemin().toFile())) {
                zip.stream().forEach(e -> noms.add(e.getName()));
            }
        }
        return noms;
    }
}
