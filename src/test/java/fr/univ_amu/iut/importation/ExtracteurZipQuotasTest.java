package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import fr.univ_amu.iut.importation.model.BornesExtraction;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.InventaireArchive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Bornes de ressources de la décompression (#2732) : ce qui empêche une archive pathologique de
/// remplir le disque du poste, sur lequel vit aussi la base du workspace.
///
/// Deux familles de refus, et c'est le fond du sujet : ceux qui lisent ce que l'archive **annonce**
/// (instantanés, avant le premier octet écrit) et celui qui constate ce qu'elle **écrit**. Une bombe
/// ZIP ment précisément sur ce que la première famille lit, d'où la seconde.
class ExtracteurZipQuotasTest {

    /// Un disque immense : neutralise le contrôle d'espace quand ce n'est pas lui qu'on éprouve.
    private static final long DISQUE_VASTE = 1_000_000_000_000L;

    @TempDir
    Path racine;

    @TempDir
    Path base;

    @Test
    @DisplayName("Espace disque insuffisant : refus avant d'écrire le premier octet (#2732)")
    void refus_si_espace_insuffisant() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        ecrireArchive(zip, "bruts/PaRecPR1925492_20260422_203922.wav", 12 * 1024 * 1024);
        // Un disque qui n'a plus qu'un mégaoctet, là où l'archive en annonce douze.
        BornesExtraction bornes = bornesLarges(dossier -> 1_000_000L);

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(
                        zip, base, p -> {}, JetonAnnulation.neutre(), bornes))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Espace disque insuffisant")
                // Les deux chiffres, sans quoi le refus n'est pas actionnable : ce qu'il faut, ce qu'il y a.
                .hasMessageContaining("13 Mo")
                .hasMessageContaining("1,0 Mo");

        assertThat(dossiersDExtraction())
                .as("le refus doit précéder la création du temporaire, pas la nettoyer après coup")
                .isEmpty();
    }

    @Test
    @DisplayName("Un contenu très compressible n'est PAS suspect en soi (#2732)")
    void un_contenu_tres_compressible_passe() throws IOException {
        Path zip = racine.resolve("silencieuse.zip");
        // Des octets identiques se compressent au millième. C'est le profil d'une bombe ZIP... et aussi
        // celui d'un enregistrement silencieux : ce sont les mêmes octets. Un plafond de taux de
        // décompression a existé ici et a été retiré, parce qu'il refusait les fixtures de recette (137
        // fois) sans rien protéger que le total annoncé et le disque ne bornent déjà.
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("bruts/nuit_silencieuse.wav"));
            zos.write(new byte[12 * 1024 * 1024]);
            zos.closeEntry();
        }

        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(
                zip, base, p -> {}, JetonAnnulation.neutre(), bornesLarges(dossier -> DISQUE_VASTE));

        try {
            assertThat(extrait.resolve("bruts/nuit_silencieuse.wav")).hasSize(12 * 1024 * 1024);
        } finally {
            ArborescenceFichiers.effacerAuMieux(extrait);
        }
    }

    @Test
    @DisplayName("L'inventaire ne compte que les fichiers : un dossier n'est pas une entrée (#2732)")
    void les_dossiers_ne_comptent_pas_dans_l_inventaire() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            // Une archive produite par « compresser ce dossier » porte des entrées de DOSSIER explicites.
            zos.putNextEntry(new ZipEntry("bruts/"));
            zos.closeEntry();
            ecrireEntree(zos, "bruts/a.wav", 1024);
        }

        InventaireArchive inventaire = InventaireArchive.lire(zip);

        // Les compter ferait mentir le dénominateur de la progression (« X / N fichiers ») et gonflerait
        // le nombre d'entrées confronté à la borne.
        assertThat(inventaire.nbFichiers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Trop d'entrées : refus qui nomme la limite et comment la lever (#2732)")
    void refus_trop_d_entrees() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < 5; i++) {
                zos.putNextEntry(new ZipEntry("f" + i + ".txt"));
                zos.write(("contenu " + i).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        BornesExtraction bornes = new BornesExtraction(3, Long.MAX_VALUE, Long.MAX_VALUE, 0, d -> DISQUE_VASTE);

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(
                        zip, base, p -> {}, JetonAnnulation.neutre(), bornes))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("5 fichiers")
                .hasMessageContaining("3 admis")
                // Sans échappatoire nommée, la seule issue serait de renoncer à une archive légitime.
                // ⚠️ Et l'échappatoire doit être ATTEIGNABLE : le refus nommait `-Dvigiechiro.…`, geste
                // qu'un produit installé ne permet pas (#4075). Il nomme désormais l'option de la ligne
                // de commande, qui existe là où l'utilisateur se trouve.
                .hasMessageContaining("--reglage import.zip.max-entrees=")
                .hasMessageNotContaining("-D");

        assertThat(dossiersDExtraction()).isEmpty();
    }

    @Test
    @DisplayName("Une entrée démesurée est refusée, et le refus la désigne par son nom (#2732)")
    void refus_entree_trop_grosse() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        ecrireArchive(zip, "bruts/enorme.wav", 2 * 1024 * 1024);
        BornesExtraction bornes = new BornesExtraction(100, 1024 * 1024, Long.MAX_VALUE, 0, d -> DISQUE_VASTE);

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(
                        zip, base, p -> {}, JetonAnnulation.neutre(), bornes))
                .isInstanceOf(RegleMetierException.class)
                // Le nom, et RIEN du chemin interne : un chemin d'archive à rallonge ferait tronquer le
                // refus. Les deux assertions comptent - la première seule laisserait passer un « nom court »
                // qui garde un bout de dossier.
                .hasMessageContaining("« enorme.wav »")
                .hasMessageNotContaining("bruts");

        assertThat(dossiersDExtraction()).isEmpty();
    }

    @Test
    @DisplayName("Total décompressé au-delà de la borne : refus (#2732)")
    void refus_total_trop_gros() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        ecrireArchive(zip, "bruts/a.wav", 2 * 1024 * 1024);
        BornesExtraction bornes = new BornesExtraction(100, Long.MAX_VALUE, 1024 * 1024, 0, d -> DISQUE_VASTE);

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(
                        zip, base, p -> {}, JetonAnnulation.neutre(), bornes))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("décompressés");

        assertThat(dossiersDExtraction()).isEmpty();
    }

    @Test
    @DisplayName("Une archive qui MENT sur sa taille est arrêtée pendant la copie (#2732)")
    void archive_menteuse_arretee_pendant_la_copie() throws IOException {
        Path zip = racine.resolve("menteuse.zip");
        ecrireArchive(zip, "bruts/gros.wav", 12 * 1024 * 1024);
        mentirSurLaTailleAnnoncee(zip, 1024 * 1024);

        // Le garde préalable ne peut rien voir : il lit exactement le mensonge, et un mégaoctet passe
        // toutes les bornes. C'est la raison d'être du second garde.
        InventaireArchive inventaire = InventaireArchive.lire(zip);
        assertThat(inventaire.octetsAnnonces())
                .as("le répertoire central doit bien annoncer la taille falsifiée")
                .isEqualTo(1024 * 1024);

        assertThatThrownBy(() -> ExtracteurZip.extraireVersDossierTemporaire(
                        zip, base, p -> {}, JetonAnnulation.neutre(), bornesLarges(dossier -> DISQUE_VASTE)))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("annonçait");

        assertThat(dossiersDExtraction())
                .as("le temporaire partiel doit être nettoyé comme sur toute erreur")
                .isEmpty();
    }

    @Test
    @DisplayName("Une nuit ordinaire passe sans le moindre réglage (#2732)")
    void une_nuit_ordinaire_passe_avec_les_defauts() throws IOException {
        Path zip = racine.resolve("nuit.zip");
        // Un journal, deux enregistrements incompressibles : la forme d'une vraie carte SD, en miniature.
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("LogPR1925492.txt"));
            zos.write("journal du capteur".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            ecrireEntree(zos, "bruts/PaRecPR1925492_20260422_203922.wav", 3 * 1024 * 1024);
            ecrireEntree(zos, "bruts/PaRecPR1925492_20260422_204512.wav", 3 * 1024 * 1024);
        }

        // parDefaut() : les bornes de production, disque réel compris. C'est le test qui empêche de
        // resserrer les défauts au point de refuser ce que l'application existe pour lire.
        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(
                zip, base, p -> {}, JetonAnnulation.neutre(), BornesExtraction.parDefaut());

        try {
            assertThat(extrait.resolve("LogPR1925492.txt")).exists();
            assertThat(extrait.resolve("bruts/PaRecPR1925492_20260422_203922.wav"))
                    .hasSize(3 * 1024 * 1024);
        } finally {
            ArborescenceFichiers.effacerAuMieux(extrait);
        }
    }

    /// Des bornes que rien ne fait franchir, sauf l'espace disque fourni : pour éprouver un refus à la
    /// fois.
    private static BornesExtraction bornesLarges(fr.univ_amu.iut.commun.model.EspaceDisque espaceDisque) {
        return new BornesExtraction(100_000, Long.MAX_VALUE, Long.MAX_VALUE, 0, espaceDisque);
    }

    private static void ecrireArchive(Path zip, String nom, int octets) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            ecrireEntree(zos, nom, octets);
        }
    }

    /// Une entrée **incompressible** (graine fixe, donc reproductible) : c'est ce qu'est l'audio d'une
    /// carte SD, et c'est ce qui distingue une archive légitime d'une bombe ZIP.
    private static void ecrireEntree(ZipOutputStream zos, String nom, int octets) throws IOException {
        byte[] contenu = new byte[octets];
        new Random(1).nextBytes(contenu);
        zos.putNextEntry(new ZipEntry(nom));
        zos.write(contenu);
        zos.closeEntry();
    }

    /// Falsifie la taille décompressée inscrite au **répertoire central**, sans toucher aux données : ce
    /// que fait une bombe ZIP, et que `ZipOutputStream` ne sait pas produire puisqu'il écrit la vérité.
    ///
    /// Disposition d'une entrée du répertoire central : signature `PK\1\2`, puis la taille décompressée
    /// sur quatre octets en petit-boutiste au vingt-quatrième octet.
    private static void mentirSurLaTailleAnnoncee(Path zip, int tailleAnnoncee) throws IOException {
        byte[] octets = Files.readAllBytes(zip);
        int cen = indexDeSignature(octets, new byte[] {0x50, 0x4b, 0x01, 0x02});
        assertThat(cen)
                .as("répertoire central introuvable dans l'archive de test")
                .isNotNegative();
        for (int i = 0; i < 4; i++) {
            octets[cen + 24 + i] = (byte) (tailleAnnoncee >>> (8 * i));
        }
        Files.write(zip, octets);
    }

    private static int indexDeSignature(byte[] octets, byte[] signature) {
        for (int i = 0; i <= octets.length - signature.length; i++) {
            boolean trouve = true;
            for (int j = 0; j < signature.length && trouve; j++) {
                trouve = octets[i + j] == signature[j];
            }
            if (trouve) {
                return i;
            }
        }
        return -1;
    }

    private List<Path> dossiersDExtraction() throws IOException {
        try (Stream<Path> entrees = Files.list(base)) {
            return entrees.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("import-zip-"))
                    .toList();
        }
    }
}
