package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.importation.model.AnnulationImportException;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.JetonAnnulation;
import fr.univ_amu.iut.importation.model.Progression;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de l'[ExtracteurZip] (#139) : détection `.zip`, décompression vers un temporaire (contenu +
/// sous-dossiers préservés), garde anti zip-slip, nettoyage récursif.
class ExtracteurZipTest {

    @TempDir
    Path racine;

    /// Volume d'accueil de l'extraction (simule le workspace disque) : distinct de `racine` pour
    /// vérifier que l'extraction atterrit bien **sous le workspace fourni**, jamais dans `/tmp`.
    @TempDir
    Path base;

    @Test
    @DisplayName("estZip reconnaît l'extension .zip (insensible à la casse), sinon non")
    void detection_zip() {
        assertThat(ExtracteurZip.estZip(Path.of("nuit.zip"))).isTrue();
        assertThat(ExtracteurZip.estZip(Path.of("NUIT.ZIP"))).isTrue();
        assertThat(ExtracteurZip.estZip(Path.of("dossier"))).isFalse();
        assertThat(ExtracteurZip.estZip(Path.of("PaRecPR1_x.wav"))).isFalse();
    }

    @Test
    @DisplayName("Extraction : fichiers et sous-dossiers restitués dans un temporaire neuf")
    void extraction_restitue_le_contenu() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "LogPR1925492.txt", "journal");
            ecrire(zos, "bruts/PaRecPR1925492_20260422_203922.wav", "wav");
        }

        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(zip, base);

        try {
            assertThat(extrait).isDirectory();
            assertThat(extrait).startsWith(base); // extraction sous le workspace fourni, pas dans /tmp
            assertThat(Files.readString(extrait.resolve("LogPR1925492.txt"))).isEqualTo("journal");
            assertThat(Files.readString(extrait.resolve("bruts/PaRecPR1925492_20260422_203922.wav")))
                    .isEqualTo("wav");
            assertThat(extrait).isNotEqualTo(racine); // dossier temporaire distinct, source intacte
            assertThat(zip).exists(); // R9 : l'archive source n'est pas modifiée
        } finally {
            ExtracteurZip.supprimerRecursivement(extrait);
        }
    }

    @Test
    @DisplayName("Extraction : la progression est notifiée fichier par fichier jusqu'à 100% (#146)")
    void extraction_notifie_la_progression() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "a.txt", "1");
            ecrire(zos, "sous/b.txt", "2");
            ecrire(zos, "sous/c.txt", "3");
        }

        List<Progression> points = new ArrayList<>();
        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(zip, base, points::add);

        try {
            // Un point de progression par fichier (les dossiers ne comptent pas), avancement croissant
            // jusqu'à 1.0 sur le dernier, libellé « X / N ».
            assertThat(points).hasSize(3);
            assertThat(points.get(0).libelle()).contains("1 / 3");
            assertThat(points.get(2).fraction()).isEqualTo(1.0);
            assertThat(points.get(2).libelle()).contains("3 / 3");
        } finally {
            ExtracteurZip.supprimerRecursivement(extrait);
        }
    }

    @Test
    @DisplayName("Annulation : la décompression s'arrête et ne laisse aucun temporaire partiel (#146)")
    void extraction_annulee_nettoie_le_temporaire() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "a.txt", "1");
            ecrire(zos, "b.txt", "2");
        }
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler(); // annulation immédiate : la 1re entrée déclenche l'arrêt

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(zip, base, p -> {}, jeton))
                .isInstanceOf(AnnulationImportException.class);

        // Le temporaire partiel a été supprimé : aucun « import-zip-* » ne subsiste sous la base.
        try (Stream<Path> entrees = Files.list(base)) {
            assertThat(entrees.filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().startsWith("import-zip-")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Annulation à la dernière entrée : l'extraction n'aboutit pas (re-vérification finale, #146)")
    void extraction_annulee_a_la_derniere_entree() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "a.txt", "1");
            ecrire(zos, "b.txt", "2");
        }
        JetonAnnulation jeton = new JetonAnnulation();
        // On n'annule qu'au dernier point (après la 2e entrée) : aucune vérification « avant entrée » ne
        // le voit → seule la re-vérification finale doit empêcher l'extraction d'« aboutir ».
        Consumer<Progression> annulerAuDernier = p -> {
            if (p.libelle().contains("2 / 2")) {
                jeton.annuler();
            }
        };

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(zip, base, annulerAuDernier, jeton))
                .isInstanceOf(AnnulationImportException.class);

        try (Stream<Path> entrees = Files.list(base)) {
            assertThat(entrees.filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().startsWith("import-zip-")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Garde zip-slip : une entrée qui s'évade du dossier est refusée (RegleMetierException)")
    void garde_zip_slip() throws IOException {
        Path zip = racine.resolve("malveillant.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "../evasion.txt", "boom");
        }

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(zip, base))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("zip");
    }

    @Test
    @DisplayName("racineEffective : un unique dossier racine est déplié (zip « compresser ce dossier »)")
    void racine_effective_deplie_un_dossier_racine_unique() throws IOException {
        // Archive typique d'un clic droit « Compresser » sur un dossier : tout est sous « MaNuit/ ».
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "MaNuit/LogPR1925492.txt", "journal");
            ecrire(zos, "MaNuit/bruts/PaRecPR1925492_20260422_203922.wav", "wav");
        }
        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(zip, base);

        try {
            Path source = ExtracteurZip.racineEffective(extrait);
            // On pointe le dossier interne, où journal et WAV sont à leur place attendue par l'inspection.
            assertThat(source.getFileName()).hasToString("MaNuit");
            assertThat(source.resolve("LogPR1925492.txt")).exists();
            assertThat(source.resolve("bruts/PaRecPR1925492_20260422_203922.wav"))
                    .exists();
        } finally {
            ExtracteurZip.supprimerRecursivement(extrait);
        }
    }

    @Test
    @DisplayName("racineEffective : une archive déjà « à plat » est renvoyée inchangée")
    void racine_effective_archive_a_plat_inchangee() throws IOException {
        Path zip = racine.resolve("plat.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrire(zos, "LogPR1925492.txt", "journal");
            ecrire(zos, "PaRecPR1925492_20260422_203922.wav", "wav");
        }
        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(zip, base);

        try {
            assertThat(ExtracteurZip.racineEffective(extrait)).isEqualTo(extrait);
        } finally {
            ExtracteurZip.supprimerRecursivement(extrait);
        }
    }

    @Test
    @DisplayName("nettoyerTemporairesResiduels supprime les import-zip-* abandonnés, épargne les sessions")
    void nettoyage_des_temporaires_residuels() throws IOException {
        // Un temporaire d'extraction laissé par un écran d'import abandonné, et une vraie session d'import.
        Files.createDirectories(base.resolve("import-zip-ancien/bruts"));
        Files.writeString(base.resolve("import-zip-ancien/bruts/x.wav"), "wav");
        Path session = Files.createDirectories(base.resolve("Car640380-2026-Pass1-A1"));

        ExtracteurZip.nettoyerTemporairesResiduels(base);

        assertThat(base.resolve("import-zip-ancien")).doesNotExist();
        assertThat(session).as("les sessions d'import ne sont pas balayées").exists();
        // Tolérant : une base inexistante ne lève pas.
        ExtracteurZip.nettoyerTemporairesResiduels(base.resolve("absent"));
    }

    @Test
    @DisplayName("supprimerRecursivement nettoie le dossier (et tolère un dossier absent)")
    void nettoyage_recursif() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("a/b/c"));
        Files.writeString(dossier.resolve("f.txt"), "x");

        ExtracteurZip.supprimerRecursivement(racine.resolve("a"));

        assertThat(racine.resolve("a")).doesNotExist();
        // Idempotent / tolérant : un second appel (dossier absent) ne lève pas.
        ExtracteurZip.supprimerRecursivement(racine.resolve("a"));
    }

    private static void ecrire(ZipOutputStream zos, String nom, String contenu) throws IOException {
        zos.putNextEntry(new ZipEntry(nom));
        zos.write(contenu.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
