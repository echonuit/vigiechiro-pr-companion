package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes.Nature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Inventaire de `sauvegardes/` (#3197) : ce que l'application y a écrit, ce que ça pèse, et depuis
/// quand.
///
/// Le dossier contient **deux natures d'objets** : les sauvegardes de base et les filets de migration
/// sont des fichiers `.db`, les sauvegardes complètes sont des **dossiers**. Un inventaire qui n'en
/// verrait qu'une mentirait sur le total - et c'est précisément le total qui motive #3197.
class InventaireSauvegardesTest {

    @TempDir
    Path racine;

    private Path sauvegardes;

    @BeforeEach
    void preparer() throws IOException {
        sauvegardes = Files.createDirectories(racine.resolve("sauvegardes"));
    }

    @Test
    @DisplayName("Un dossier absent n'est pas une anomalie : l'inventaire est vide")
    void dossier_absent() {
        assertThat(InventaireSauvegardes.lire(racine.resolve("jamais-cree"))).isEmpty();
    }

    @Test
    @DisplayName("Chaque nature est reconnue : base, filet de migration, sauvegarde complète")
    void trois_natures() throws IOException {
        ecrireFichier("vigiechiro-sauvegarde-20260801-101500.db", 100);
        ecrireFichier("vigiechiro-avant-migration-V39.db", 200);
        ecrireDossierComplet("vigiechiro-sauvegarde-complete-20260802-090000", 300);

        List<InventaireSauvegardes.Entree> entrees = InventaireSauvegardes.lire(sauvegardes);

        assertThat(entrees)
                .extracting(InventaireSauvegardes.Entree::nom, InventaireSauvegardes.Entree::nature)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("vigiechiro-sauvegarde-20260801-101500.db", Nature.BASE),
                        org.assertj.core.groups.Tuple.tuple(
                                "vigiechiro-avant-migration-V39.db", Nature.FILET_MIGRATION),
                        org.assertj.core.groups.Tuple.tuple(
                                "vigiechiro-sauvegarde-complete-20260802-090000", Nature.COMPLETE));
    }

    @Test
    @DisplayName("La taille d'une sauvegarde complète est celle de son CONTENU, pas celle du dossier")
    void taille_dune_complete() throws IOException {
        ecrireDossierComplet("vigiechiro-sauvegarde-complete-20260802-090000", 4096);

        List<InventaireSauvegardes.Entree> entrees = InventaireSauvegardes.lire(sauvegardes);

        assertThat(entrees).hasSize(1);
        assertThat(entrees.getFirst().octets())
                .as("un dossier pèse ce que pèse ce qu'il contient : c'est là que sont les gigaoctets")
                .isEqualTo(2L * 4096);
    }

    @Test
    @DisplayName("Le total additionne les deux natures : sans les dossiers, il mentirait")
    void total_toutes_natures() throws IOException {
        ecrireFichier("vigiechiro-sauvegarde-20260801-101500.db", 100);
        ecrireDossierComplet("vigiechiro-sauvegarde-complete-20260802-090000", 1000);

        assertThat(InventaireSauvegardes.total(InventaireSauvegardes.lire(sauvegardes)))
                .isEqualTo(100 + 2L * 1000);
    }

    @Test
    @DisplayName("Les entrées viennent de la plus récente à la plus ancienne")
    void ordre_du_plus_recent() throws IOException {
        Path ancienne = ecrireFichier("vigiechiro-sauvegarde-20260101-101500.db", 10);
        Path recente = ecrireFichier("vigiechiro-sauvegarde-20260801-101500.db", 10);
        Files.setLastModifiedTime(ancienne, FileTime.from(Instant.parse("2026-01-01T10:15:00Z")));
        Files.setLastModifiedTime(recente, FileTime.from(Instant.parse("2026-08-01T10:15:00Z")));

        assertThat(InventaireSauvegardes.lire(sauvegardes))
                .extracting(InventaireSauvegardes.Entree::nom)
                .containsExactly(
                        "vigiechiro-sauvegarde-20260801-101500.db", "vigiechiro-sauvegarde-20260101-101500.db");
    }

    @Test
    @DisplayName("Ce que l'application n'a pas écrit là n'est pas inventorié")
    void intrus_ignore() throws IOException {
        ecrireFichier("notes-perso.txt", 50);
        ecrireFichier("vigiechiro-sauvegarde-20260801-101500.db", 100);

        assertThat(InventaireSauvegardes.lire(sauvegardes))
                .extracting(InventaireSauvegardes.Entree::nom)
                .containsExactly("vigiechiro-sauvegarde-20260801-101500.db");
    }

    private Path ecrireFichier(String nom, int octets) throws IOException {
        Path fichier = sauvegardes.resolve(nom);
        Files.write(fichier, new byte[octets]);
        return fichier;
    }

    /// Un dossier de sauvegarde complète tel que `ServiceSauvegarde.sauvegarderComplet` le produit :
    /// `base/vigiechiro.db` et un dossier de session, chacun de `octets`.
    private void ecrireDossierComplet(String nom, int octets) throws IOException {
        Path racineBackup = sauvegardes.resolve(nom);
        Files.createDirectories(racineBackup.resolve("base"));
        Files.write(racineBackup.resolve("base").resolve("vigiechiro.db"), new byte[octets]);
        Path session = Files.createDirectories(racineBackup.resolve("sessions").resolve("Car640380-2026-Pass1-Z1"));
        Files.writeString(session.resolve("note.txt"), "x".repeat(octets), StandardCharsets.UTF_8);
    }
}
