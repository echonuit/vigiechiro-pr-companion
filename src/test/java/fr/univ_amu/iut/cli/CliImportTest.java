package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'intégration de bout en bout de la [Cli] sur le flux lourd d'import (P2) : on prépare
/// une fausse carte SD (journal LogPR + WAV synthétiques), on invoque `importer`, puis on
/// vérifie les effets persistés (passage au statut `Transformé`) et le code de sortie. On
/// enchaîne sur `lister-passages` pour confirmer que le passage importé est restitué avec son
/// contexte site/point — scénario « importer un dossier de fixtures puis lister ».
///
/// Un seul test cible le flux lourd (la mécanique fine de l'import est déjà couverte par
/// `ServiceImportTest`) ; ici on valide l'**orchestration CLI** (résolution Guice, dérivation du
/// préfixe R6 depuis le point, codes de sortie).
class CliImportTest {

    private static final String ID_USER = "u-cli";
    private static final int FREQUENCE_WAV = 384_000; // Hz, multiple de 10
    private static final int TRAMES = 576_000; // 1,5 s -> 3 séquences par original

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH"
                    + " 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%\n";

    @TempDir
    Path racine;

    private Injector injecteur;
    private Cli cli;
    private Long idPoint;
    private Path sd;

    @BeforeEach
    void preparer() throws IOException {
        System.setProperty("vigiechiro.workspace", racine.resolve("ws").toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);

        // Schéma + parents FK (utilisateur -> site 640380 -> point Z1) sur la base de la CLI.
        injecteur.getInstance(MigrationSchema.class).migrer();
        injecteur.getInstance(UtilisateurDao.class).insert(new Utilisateur(ID_USER, "Testeur CLI"));
        Site site = injecteur
                .getInstance(SiteDao.class)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        PointDEcoute point =
                injecteur.getInstance(PointDao.class).insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()));
        idPoint = point.id();

        sd = preparerCarteSD(racine.resolve("sd"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#2064 : sans option, `importer` suit le réglage — il ne conserve plus en dur")
    void importer_suit_le_reglage() {
        // Avant #2064, la variante courte du service passait `true` en dur : la CLI conservait toujours
        // les originaux, quel que soit le réglage, alors que l'IHM ne les conserve plus par défaut. Le
        // même geste ne faisait pas la même chose des deux côtés (ADR 0014).
        assertThat(importerAvec()).isEqualTo(Cli.CODE_SUCCES);

        assertThat(dossierBrutsDuPassage())
                .as("le défaut du réglage : pas de copie")
                .doesNotExist();
    }

    @Test
    @DisplayName("#2064 : --conserver-originaux force la copie, quel que soit le réglage")
    void option_conserver_force_la_copie() {
        assertThat(importerAvec("--conserver-originaux")).isEqualTo(Cli.CODE_SUCCES);

        assertThat(dossierBrutsDuPassage()).as("l'option prime sur le réglage").isDirectory();
    }

    @Test
    @DisplayName("#2064 : les deux options s'excluent, et le disent")
    void options_incompatibles() {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        int code = cli.executer(
                argsImport("--conserver-originaux", "--sans-originaux"),
                new PrintStream(sortie, true, StandardCharsets.UTF_8),
                new PrintStream(sortie, true, StandardCharsets.UTF_8));

        assertThat(code).isNotEqualTo(Cli.CODE_SUCCES);
        assertThat(sortie.toString(StandardCharsets.UTF_8)).contains("s'excluent");
    }

    private int importerAvec(String... options) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        return cli.executer(
                argsImport(options),
                new PrintStream(sortie, true, StandardCharsets.UTF_8),
                new PrintStream(sortie, true, StandardCharsets.UTF_8));
    }

    private String[] argsImport(String... options) {
        List<String> args = new java.util.ArrayList<>(List.of(
                "importer",
                "--source",
                sd.toString(),
                "--point",
                String.valueOf(idPoint),
                "--annee",
                "2026",
                "--passage",
                "3"));
        args.addAll(List.of(options));
        return args.toArray(String[]::new);
    }

    /// Le dossier `bruts/` de la session du passage importé (existe seulement si la copie a eu lieu).
    private Path dossierBrutsDuPassage() {
        Long idPassage = injecteur
                .getInstance(PassageDao.class)
                .findByPoint(idPoint)
                .getFirst()
                .id();
        return Path.of(injecteur
                        .getInstance(fr.univ_amu.iut.passage.model.dao.SessionDao.class)
                        .trouverParPassage(idPassage)
                        .orElseThrow()
                        .cheminRacine())
                .resolve("bruts");
    }

    @Test
    @DisplayName("importer puis lister-passages : passage Transformé persisté, codes de sortie 0")
    void importer_puis_lister() {
        ByteArrayOutputStream sortieImport = new ByteArrayOutputStream();
        int codeImport = cli.executer(
                new String[] {
                    "importer",
                    "--source",
                    sd.toString(),
                    "--point",
                    String.valueOf(idPoint),
                    "--annee",
                    "2026",
                    "--passage",
                    "2"
                },
                new PrintStream(sortieImport, true, StandardCharsets.UTF_8),
                new PrintStream(sortieImport, true, StandardCharsets.UTF_8));

        assertThat(codeImport).isEqualTo(Cli.CODE_SUCCES);
        assertThat(sortieImport.toString(StandardCharsets.UTF_8))
                .contains("Import réussi")
                .contains("Z1")
                .contains("640380");

        // Effet persisté : un passage au statut Transformé est rattaché au point.
        List<Passage> passages = injecteur.getInstance(PassageDao.class).findByPoint(idPoint);
        assertThat(passages).hasSize(1);
        assertThat(passages.get(0).statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(passages.get(0).numeroPassage()).isEqualTo(2);
        assertThat(passages.get(0).annee()).isEqualTo(2026);

        // lister-passages restitue le passage importé avec son contexte site/point.
        ByteArrayOutputStream sortieListe = new ByteArrayOutputStream();
        int codeListe = cli.executer(
                new String[] {"lister-passages"},
                new PrintStream(sortieListe, true, StandardCharsets.UTF_8),
                new PrintStream(sortieListe, true, StandardCharsets.UTF_8));

        assertThat(codeListe).isEqualTo(Cli.CODE_SUCCES);
        assertThat(sortieListe.toString(StandardCharsets.UTF_8))
                .contains("1 passage(s)")
                .contains("640380")
                .contains("Z1")
                .contains("Transformé");
    }

    // --- Fixture carte SD (autonome, calquée sur ServiceImportTest) -------------

    private Path preparerCarteSD(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(dossier.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_203922.wav"));
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_204326.wav"));
        return dossier;
    }

    private static void ecrireWav(Path fichier) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
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
        buf.putInt(FREQUENCE_WAV);
        buf.putInt(FREQUENCE_WAV * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }
}
