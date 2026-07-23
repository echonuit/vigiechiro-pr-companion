package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'intégration de bout en bout de la [Cli] pour `importer-transformes` (#2433) : on prépare un
/// dossier **externe** de séquences déjà transformées, on invoque la commande, et on vérifie l'**orchestration
/// CLI** (résolution Guice de `ServiceImportReference`, dérivation du préfixe R6 depuis le point, code de
/// sortie #2294). La mécanique fine du modèle est couverte par `ServiceImportReferenceTest` ; ici on valide
/// le câblage et le contrat de sortie.
class CliImportTransformesTest {

    private static final String ID_USER = "u-cli-tr";
    private static final String SERIE = "1925492";
    private static final int FREQUENCE_ENTETE = 38_400; // Fe/10 : en-tête d'une séquence déjà transformée
    private static final int TRAMES = 3_840;

    @TempDir
    Path racine;

    private Injector injecteur;
    private Cli cli;
    private Long idPoint;
    private Workspace workspace;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", racine.resolve("ws").toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        workspace = injecteur.getInstance(Workspace.class);

        injecteur.getInstance(MigrationSchema.class).migrer();
        injecteur.getInstance(UtilisateurDao.class).insert(new Utilisateur(ID_USER, "Testeur CLI"));
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        PointDEcoute point =
                injecteur.getInstance(PointDao.class).insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()));
        idPoint = point.id();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("--referencer : passage créé, séquences externes, aucun WAV dans le workspace, sortie 0")
    void referencer_reussit_et_ne_copie_rien() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("nas"));
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();

        int code = cli.executer(
                args(externe, "--referencer"),
                new PrintStream(sortie, true, StandardCharsets.UTF_8),
                new PrintStream(sortie, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(sortie.toString(StandardCharsets.UTF_8))
                .contains("référence")
                .contains("aucun octet audio");

        List<SequenceDEcoute> sequences = sequencesDuPassage();
        assertThat(sequences)
                .hasSize(3)
                .allSatisfy(s -> assertThat(s.cheminFichier())
                        .as("mode référence : le WAV reste sur le support externe")
                        .startsWith(externe.toString()));
        assertThat(wavSousLaRacine(workspace.racine()))
                .as("aucun octet audio recopié dans l'espace de travail")
                .isEmpty();
    }

    @Test
    @DisplayName("Refus métier : point inconnu → rien de créé, sortie 2 (état intact, #2294)")
    void point_inconnu_sort_en_deux() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("nas"));
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();

        int code = cli.executer(
                new String[] {"importer-transformes", "--dossier", externe.toString(), "--point", "9999", "--referencer"
                },
                new PrintStream(sortie, true, StandardCharsets.UTF_8),
                new PrintStream(sortie, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(2);
        assertThat(sequencesDuPassage()).isEmpty();
    }

    private String[] args(Path dossier, String... options) {
        List<String> args = new java.util.ArrayList<>(
                List.of("importer-transformes", "--dossier", dossier.toString(), "--point", String.valueOf(idPoint)));
        args.addAll(List.of(options));
        return args.toArray(String[]::new);
    }

    private List<SequenceDEcoute> sequencesDuPassage() {
        SessionDao sessionDao = injecteur.getInstance(SessionDao.class);
        SequenceDao sequenceDao = injecteur.getInstance(SequenceDao.class);
        return sessionDao
                .trouverParPassage(1L)
                .map(session -> sequenceDao.findBySession(session.id()))
                .orElse(List.of());
    }

    private static List<String> wavSousLaRacine(Path racine) throws IOException {
        if (!Files.isDirectory(racine)) {
            return List.of();
        }
        try (Stream<Path> flux = Files.walk(racine)) {
            return flux.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(nom -> nom.toLowerCase(java.util.Locale.ROOT).endsWith(".wav"))
                    .toList();
        }
    }

    /// Dossier externe de séquences déjà transformées : deux originaux (2 tranches + 1), noms R6 horodatés
    /// portant la série, pour que la série et la date se déduisent des noms.
    private static Path preparerDossierTransforme(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        String base = "Car640380-2026-Pass1-Z1-PaRecPR" + SERIE + "_20260422_";
        ecrireWav(dossier.resolve(base + "203922_000.wav"), 1);
        ecrireWav(dossier.resolve(base + "203922_001.wav"), 2);
        ecrireWav(dossier.resolve(base + "204326_000.wav"), 3);
        return dossier;
    }

    private static void ecrireWav(Path fichier, int germe) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41 + germe * 7) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_ENTETE);
        buf.putInt(FREQUENCE_ENTETE * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }
}
