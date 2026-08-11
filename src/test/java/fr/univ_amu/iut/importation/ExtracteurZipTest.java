package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

    /// Taille de l'entrée volumineuse des tests d'annulation en cours de copie : au-delà de plusieurs
    /// paliers de progression intra-entrée, pour que l'arrêt puisse se produire au milieu du fichier.
    private static final int TAILLE_GROSSE_ENTREE = 12 * 1024 * 1024;

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
            ArborescenceFichiers.effacerAuMieux(extrait);
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
            ArborescenceFichiers.effacerAuMieux(extrait);
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
                .isInstanceOf(OperationAnnuleeException.class);

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
                .isInstanceOf(OperationAnnuleeException.class);

        try (Stream<Path> entrees = Files.list(base)) {
            assertThat(entrees.filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().startsWith("import-zip-")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Une entrée volumineuse donne signe de vie : le volume écrit s'affiche par paliers (#2733)")
    void progression_a_l_interieur_d_une_entree_volumineuse() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrireGros(zos, "gros.wav", TAILLE_GROSSE_ENTREE);
        }

        List<Progression> points = new ArrayList<>();
        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(zip, base, points::add);

        try {
            // Le dernier point est celui de fin de fichier ; tous les autres sont tombés PENDANT la
            // copie, sans quoi l'écran resterait figé sur « 0 / 1 » du début à la fin.
            List<Progression> pendantLEntree = points.subList(0, points.size() - 1);
            Progression fin = points.get(points.size() - 1);

            // Le nombre exact de paliers ne s'assure pas : le décompresseur rend des blocs de taille
            // irrégulière, et le cumul dérive de quelques kilooctets d'un palier à l'autre. Ce qui se
            // vérifie, c'est qu'une entrée de 12 Mio en produit plusieurs.
            assertThat(pendantLEntree).hasSizeGreaterThanOrEqualTo(2);
            assertThat(pendantLEntree.get(0).libelle()).contains("gros.wav").contains("4,2 Mo");
            // La barre ne bouge pas pendant l'entrée : la taille décompressée n'est pas connue au fil de
            // l'eau, et un avancement inventé vaudrait moins qu'un compteur honnête.
            assertThat(pendantLEntree).allSatisfy(p -> assertThat(p.fraction()).isZero());
            assertThat(fin.fraction()).isEqualTo(1.0);
            assertThat(fin.libelle()).contains("1 / 1");
            assertThat(extrait.resolve("gros.wav")).hasSize(TAILLE_GROSSE_ENTREE);
        } finally {
            ArborescenceFichiers.effacerAuMieux(extrait);
        }
    }

    @Test
    @DisplayName("Annulation pendant une entrée volumineuse : l'arrêt n'attend pas la fin du fichier (#2733)")
    void annulation_pendant_une_entree_volumineuse() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrireGros(zos, "gros.wav", TAILLE_GROSSE_ENTREE);
        }
        JetonAnnulation jeton = new JetonAnnulation();
        // On annule à la première notification, et on relève au même instant la taille du fichier en
        // train d'être écrit. Si la copie consulte le jeton pendant l'entrée, cette notification tombe à
        // un palier intra-entrée et le fichier n'est écrit qu'en partie ; sinon la seule notification
        // possible arrive l'entrée close, et le relevé vaut sa taille entière.
        List<Long> releve = new ArrayList<>();
        Consumer<Progression> annulerALaPremiereNotification = p -> {
            if (releve.isEmpty()) {
                releve.add(tailleDuFichierEnCours());
                jeton.annuler();
            }
        };

        assertThatThrownBy(() ->
                        ExtracteurZip.extraireVersDossierTemporaire(zip, base, annulerALaPremiereNotification, jeton))
                .isInstanceOf(OperationAnnuleeException.class);

        assertThat(releve)
                .as("une notification doit tomber pendant la copie, sans quoi rien ne peut interrompre l'entrée")
                .hasSize(1);
        assertThat(releve.get(0))
                .as(
                        "la copie doit s'arrêter en cours d'entrée, pas une fois les %d octets écrits",
                        TAILLE_GROSSE_ENTREE)
                .isPositive()
                .isLessThan(TAILLE_GROSSE_ENTREE);

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
            ArborescenceFichiers.effacerAuMieux(extrait);
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
            ArborescenceFichiers.effacerAuMieux(extrait);
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

        ArborescenceFichiers.effacerAuMieux(racine.resolve("a"));

        assertThat(racine.resolve("a")).doesNotExist();
        // Idempotent / tolérant : un second appel (dossier absent) ne lève pas.
        ArborescenceFichiers.effacerAuMieux(racine.resolve("a"));
    }

    private static void ecrire(ZipOutputStream zos, String nom, String contenu) throws IOException {
        zos.putNextEntry(new ZipEntry(nom));
        zos.write(contenu.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /// Une entrée de `octets` octets **incompressibles**, assez grosse pour franchir plusieurs paliers de
    /// progression.
    ///
    /// Le remplissage est pseudo-aléatoire à graine fixe, et non des octets identiques : ceux-ci se
    /// compressent au millième, ce qui fait de l'archive une **bombe ZIP** au regard du garde de
    /// ratio (#2732) et la fait refuser avant toute extraction. Les vraies données d'une carte SD sont
    /// de l'audio, qui se compresse peu : la fixture doit leur ressembler sur ce point.
    private static void ecrireGros(ZipOutputStream zos, String nom, int octets) throws IOException {
        byte[] contenu = new byte[octets];
        new Random(1).nextBytes(contenu);
        zos.putNextEntry(new ZipEntry(nom));
        zos.write(contenu);
        zos.closeEntry();
    }

    /// Taille du plus gros fichier présent sous la base, relevée **pendant** l'extraction : le
    /// temporaire `import-zip-*` est supprimé dès l'annulation, et après coup il n'y a plus rien à
    /// mesurer.
    private long tailleDuFichierEnCours() {
        try (Stream<Path> arborescence = Files.walk(base)) {
            return arborescence
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .max()
                    .orElse(0L);
        } catch (IOException e) {
            throw new UncheckedIOException("Relevé de la taille du fichier en cours impossible", e);
        }
    }
}
