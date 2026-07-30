package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
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
                    + " 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%\n"
                    // Un « Wakeup » sans « ALARM » est un réveil non programmé pour
                    // AnalyseurLogPR : de quoi éprouver la restitution des anomalies.
                    + "22/04/26 - 03:12:00 PR1925492 Wakeup\n";

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
        SortieCapturee sortie = new SortieCapturee();
        int code =
                cli.executer(argsImport("--conserver-originaux", "--sans-originaux"), sortie.sortie(), sortie.erreur());

        assertThat(code).isNotEqualTo(Cli.CODE_SUCCES);
        assertThat(sortie.tout()).contains("s'excluent");
    }

    private int importerAvec(String... options) {
        SortieCapturee sortie = new SortieCapturee();
        return cli.executer(argsImport(options), sortie.sortie(), sortie.erreur());
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
        SortieCapturee sortieImport = new SortieCapturee();
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
                sortieImport.sortie(),
                sortieImport.erreur());

        assertThat(codeImport).isEqualTo(Cli.CODE_SUCCES);
        assertThat(sortieImport.tout())
                .contains("Import réussi")
                .contains("Z1")
                .contains("640380")
                // Parité avec l'IHM (clôture #2350) : depuis #2358 l'écran compare le volume lu au volume
                // écrit. La CLI ne dessine pas de barres, mais elle dit les mêmes chiffres - c'est une
                // donnée, pas une mise en forme, et une capacité livrée d'un seul côté est à moitié livrée.
                .contains("Lu / écrit")
                .contains("lus sur la source")
                .contains("écrits")
                // Cet import ne conserve pas les originaux : la part « bruts conservés » n'existe pas, et
                // la ligne ne doit pas l'annoncer à zéro. L'écran omet le segment nul ; la CLI omet la
                // mention. Les trois assertions ci-dessus ne regardaient que la présence de la ligne :
                // elles restaient vertes sur un « dont 0 Ko de bruts conservés », que seule la recette
                // dorée a vu (clôture #2350).
                .doesNotContain("0 Ko de bruts conservés");

        // Effet persisté : un passage au statut Transformé est rattaché au point.
        List<Passage> passages = injecteur.getInstance(PassageDao.class).findByPoint(idPoint);
        assertThat(passages).hasSize(1);
        assertThat(passages.get(0).statutWorkflow()).isEqualTo(StatutWorkflow.TRANSFORME);
        assertThat(passages.get(0).numeroPassage()).isEqualTo(2);
        assertThat(passages.get(0).annee()).isEqualTo(2026);

        // lister-passages restitue le passage importé avec son contexte site/point.
        SortieCapturee sortieListe = new SortieCapturee();
        int codeListe = cli.executer(new String[] {"lister-passages"}, sortieListe.sortie(), sortieListe.erreur());

        assertThat(codeListe).isEqualTo(Cli.CODE_SUCCES);
        assertThat(sortieListe.tout())
                .contains("1 passage(s)")
                .contains("640380")
                .contains("Z1")
                .contains("Transformé");
    }

    @Test
    @DisplayName("#2278 : une collision de numéro sans --ecraser chiffre la perte et n'importe RIEN (2)")
    void collision_sans_ecraser_ne_touche_a_rien() {
        assertThat(importerAvec()).isEqualTo(Cli.CODE_SUCCES);
        Long idAvant = injecteur
                .getInstance(PassageDao.class)
                .findByPoint(idPoint)
                .getFirst()
                .id();

        SortieCapturee capture = new SortieCapturee();
        int code = cli.executer(argsImport(), capture.sortie(), capture.erreur());

        assertThat(code)
                .as("2 arrête un script qui enchaînerait, sans le confondre avec un échec (1)")
                .isEqualTo(2);
        assertThat(capture.texte())
                .as("la perte se chiffre AVANT d'agir, comme la double confirmation de l'IHM")
                .contains("déjà utilisé")
                .contains("Suppression DÉFINITIVE");
        assertThat(capture.texteErreur()).contains("--ecraser");
        assertThat(injecteur.getInstance(PassageDao.class).findByPoint(idPoint))
                .as("un refus qui aurait déjà détruit serait pire que pas de refus du tout")
                .singleElement()
                .extracting(Passage::id)
                .isEqualTo(idAvant);
    }

    @Test
    @DisplayName("#2278 : avec --ecraser, la nuit existante est remplacée par la nouvelle")
    void collision_avec_ecraser_remplace_la_nuit() {
        assertThat(importerAvec()).isEqualTo(Cli.CODE_SUCCES);
        Long idAvant = injecteur
                .getInstance(PassageDao.class)
                .findByPoint(idPoint)
                .getFirst()
                .id();

        assertThat(importerAvec("--ecraser")).isEqualTo(Cli.CODE_SUCCES);

        assertThat(injecteur.getInstance(PassageDao.class).findByPoint(idPoint))
                .as("le quadruplet reste unique : l'ancien passage a bien été remplacé, pas doublé")
                .singleElement()
                .extracting(Passage::id)
                .isNotEqualTo(idAvant);
    }

    @Test
    @DisplayName("#2278 : sans collision, la sortie nominale ne dit RIEN d'un écrasement")
    void sans_collision_aucune_ligne_ecrasement() {
        SortieCapturee capture = new SortieCapturee();
        int code = cli.executer(argsImport(), capture.sortie(), capture.erreur());

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.tout())
                .as("le chemin nominal est inchangé — un golden compare cette sortie mot pour mot")
                .doesNotContain("déjà utilisé")
                .doesNotContain("Suppression DÉFINITIVE");
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
        // Writer de production (#2864) : mêmes octets que l'en-tête écrit ici à la main, et
        // c'est le format que l'application saura relire - un test qui compose le sien teste un
        // format que le produit n'utilise pas.
        FichierWav.ecrire(fichier, 1, FREQUENCE_WAV, 16, pcm, 0, pcm.length);
    }

    @Test
    @DisplayName("#2004 : la sortie rapporte les anomalies du journal, comme l'écran depuis #2044")
    void importer_rapporte_les_anomalies_du_journal() {
        SortieCapturee sortie = new SortieCapturee();
        int code = cli.executer(
                new String[] {"importer", "--source", sd.toString(), "--point", String.valueOf(idPoint)},
                sortie.sortie(),
                sortie.erreur());

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        // Ces anomalies étaient transportées jusqu'au ViewModel et affichées NULLE PART. #2044 les a
        // rendues visibles à l'écran ; sans cette ligne, la commande resterait la moitié muette.
        assertThat(sortie.tout()).contains("Anomalie").contains("Réveil non programmé");
    }
}
