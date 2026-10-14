package fr.univ_amu.iut.perf.outils;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.EmpreinteContenu;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Banc de la **réactivation** d'une nuit archivée (#6174, O3) : il fabrique une nuit avec le
/// générateur de [BancImport], l'importe, efface ses séquences et ses bruts de l'espace de travail, puis
/// la réactive depuis la carte par le vrai [ServiceReactivationPassage]. Il relève le temps, les
/// temporaires de régénération au plus haut, sous `java.io.tmpdir` et sous l'espace de travail, la
/// mémoire du poste et `Shmem` lues dans `/proc/meminfo`, le heap au plus haut et après un GC, et les
/// temporaires restés.
///
/// `perf.reactivation.voie` vaut `bruts` (défaut), où un original se régénère à la fois, ou
/// `hydratation`, où les originaux réduits à des placeholders font une nuit reconstruite dont les bruts
/// se régénèrent en parallèle. Les autres paramètres reprennent ceux de [BancImport] sous le préfixe
/// `perf.reactivation` : `fichiers`, `go`, `secondes`, `frequenceHz`. `vigiechiro.workspace` est
/// réinitialisé à chaque lancement. Le mode d'emploi et le relevé sont dans `docs/benchmarks/README.md`.
public final class BancReactivation {

    private static final long MIO = 1024L * 1024L;
    private static final long GIO = 1024L * MIO;
    private static final int OCTETS_PAR_TRAME = 2;
    private static final long PERIODE_ECHANTILLON_MS = 200;
    private static final List<String> PREFIXES_TEMPORAIRES = List.of("vc-regen-", "vc-hydrate-");
    private static final Path MEMINFO = Path.of("/proc/meminfo");
    private static final String VOIE_HYDRATATION = "hydratation";

    private BancReactivation() {}

    public static void main(String[] args) throws IOException {
        Path racine = espaceDeTravail();
        double secondes = Double.parseDouble(System.getProperty("perf.reactivation.secondes", "5.0"));
        int frequenceHz = Integer.getInteger("perf.reactivation.frequenceHz", 384_000);
        String voie = System.getProperty("perf.reactivation.voie", "bruts");
        long octetsParWav = (long) (44 + (long) frequenceHz * OCTETS_PAR_TRAME * secondes);
        int nbFichiers = nombreDeFichiers(octetsParWav);
        Path temporaires = Path.of(System.getProperty("java.io.tmpdir"));

        System.setProperty("vigiechiro.workspace", racine.toString());
        BancImport.reinitialiser(racine);
        Injector injecteur = BancImport.creerInjecteur();
        injecteur.getInstance(MigrationSchema.class).migrer();
        Long idPoint = BancImport.seeder(injecteur);

        Path sd = racine.resolve("source-sd");
        BancImport.genererNuit(sd, nbFichiers, frequenceHz, secondes, LocalDate.of(2026, 4, 22));
        long tailleSource = nbFichiers * octetsParWav;
        System.out.printf(
                Locale.ROOT,
                "=== Banc de réactivation (#6174) - nuit synthétique : %d WAV de %.1f s @ %d Hz = %.2f Gio,"
                        + " voie %s ===%n",
                nbFichiers,
                secondes,
                frequenceHz,
                tailleSource / (double) GIO,
                voie);
        BancImport.imprimerMachine();
        System.out.printf(Locale.ROOT, "  java.io.tmpdir  : %s%n", temporaires);

        long debutImport = System.nanoTime();
        ResultatImport importe = injecteur
                .getInstance(ServiceImport.class)
                .importer(sd, idPoint, new Prefixe("640380", 2026, 1, "A1"), progres -> {});
        System.out.printf(
                Locale.ROOT,
                "  import          : %.2f s, %d originaux, %d séquences%n",
                (System.nanoTime() - debutImport) / 1e9,
                importe.nombreOriginaux(),
                importe.nombreSequences());

        Long idSession = importe.session().id();
        archiver(injecteur, idSession);
        if (VOIE_HYDRATATION.equals(voie)) {
            oublierLesOriginaux(injecteur.getInstance(EnregistrementOriginalDao.class), idSession);
        }

        Echantillonneur mesure = Echantillonneur.demarrer(temporaires, racine);
        long debut = System.nanoTime();
        RapportReactivation rapport = injecteur
                .getInstance(ServiceReactivationPassage.class)
                .reactiver(importe.passage().id(), sd, progres -> {});
        double totalS = (System.nanoTime() - debut) / 1e9;
        mesure.arreter();
        System.gc();
        long heapApresGc = heapUtilise();

        System.out.printf(
                Locale.ROOT,
                "  réactivation    : %.2f s, voie %s, %d réactivées, %d manquantes, %d divergentes%n",
                totalS,
                rapport.voie(),
                rapport.reactivees(),
                rapport.manquantes(),
                rapport.divergentes());
        System.out.printf(
                Locale.ROOT,
                "  temporaires     : %d Mo au plus haut sous java.io.tmpdir, %d dossier(s) resté(s)%n",
                mesure.picTemporaires.get() / MIO,
                temporairesRestants(temporaires));
        System.out.printf(
                Locale.ROOT,
                "                    %d Mo au plus haut sous l'espace de travail, %d dossier(s) resté(s)%n",
                mesure.picEspaceDeTravail.get() / MIO,
                temporairesRestants(racine));
        System.out.printf(
                Locale.ROOT,
                "  poste           : %d Mo utilisés au plus haut (%d au départ), Shmem %d Mo au plus haut"
                        + " (%d au départ)%n",
                mesure.picPoste.get() / MIO,
                mesure.posteDepart / MIO,
                mesure.picShmem.get() / MIO,
                mesure.shmemDepart / MIO);
        System.out.printf(
                Locale.ROOT,
                "  heap            : %d Mo au plus haut, %d Mo juste après un GC%n",
                mesure.picHeap.get() / MIO,
                heapApresGc / MIO);
    }

    /// L'espace de travail du banc : celui qu'on lui impose, ou un dossier temporaire à nous seuls.
    private static Path espaceDeTravail() throws IOException {
        String impose = System.getProperty("vigiechiro.workspace");
        return impose != null ? Path.of(impose) : Files.createTempDirectory("vigiechiro-bench-reactivation-");
    }

    private static int nombreDeFichiers(long octetsParWav) {
        String go = System.getProperty("perf.reactivation.go");
        if (go != null) {
            long cibleOctets = (long) (Double.parseDouble(go) * GIO);
            return (int) Math.max(1, Math.ceil(cibleOctets / (double) octetsParWav));
        }
        return Math.max(1, Integer.getInteger("perf.reactivation.fichiers", 100));
    }

    /// L'archivage tel que la réactivation le trouve : les séquences et les bruts de l'espace de travail
    /// ont quitté le disque, la base les connaît toujours. Il ne reste que la carte d'origine.
    /// Retire du disque les séquences et les originaux de la session, comme un archivage. Partagé avec le
    /// banc de l'IHM, qui réactive la même nuit par la modale (#6256).
    static void archiver(Injector injecteur, Long idSession) {
        for (SequenceDEcoute sequence : injecteur.getInstance(SequenceDao.class).findBySession(idSession)) {
            effacer(Path.of(sequence.cheminFichier()));
        }
        for (EnregistrementOriginal original :
                injecteur.getInstance(EnregistrementOriginalDao.class).findBySession(idSession)) {
            effacer(Path.of(original.cheminFichier()));
        }
    }

    /// Fait de la nuit une nuit **reconstruite** aux yeux de la réactivation. Ses originaux deviennent
    /// des placeholders comme ceux que pose la reconstruction depuis la plateforme : un nom qu'aucun brut
    /// ne porte, ni chemin, ni durée, ni fréquence d'acquisition. Plus rien en base ne relie alors la
    /// carte à la nuit, et c'est l'hydratation, qui relit la fréquence dans le journal de la carte, qui
    /// prend la main.
    /// Réduit les originaux à des placeholders, comme une nuit reconstruite : la réactivation prend alors
    /// la voie d'hydratation, qui régénère ses bruts en parallèle. Partagé avec le banc de l'IHM (#6256).
    static void oublierLesOriginaux(EnregistrementOriginalDao dao, Long idSession) {
        for (EnregistrementOriginal original : dao.findBySession(idSession)) {
            dao.update(new EnregistrementOriginal(
                    original.id(),
                    "reconstruit-" + original.id() + ".wav",
                    "",
                    null,
                    null,
                    original.idSession(),
                    EmpreinteContenu.ABSENTE));
        }
    }

    private static void effacer(Path fichier) {
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException e) {
            throw new UncheckedIOException("Archivage du banc impossible : " + fichier, e);
        }
    }

    private static long heapUtilise() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /// Les dossiers de régénération présents sous `temporaires`.
    private static List<Path> dossiersDeRegeneration(Path temporaires) {
        try (Stream<Path> entrees = Files.list(temporaires)) {
            return entrees.filter(entree -> PREFIXES_TEMPORAIRES.stream()
                            .anyMatch(prefixe -> entree.getFileName().toString().startsWith(prefixe)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible de " + temporaires, e);
        }
    }

    private static int temporairesRestants(Path temporaires) {
        return dossiersDeRegeneration(temporaires).size();
    }

    /// Octets des dossiers de régénération à cet instant. Un dossier peut s'effacer pendant qu'on le
    /// parcourt : ce qui a disparu ne compte plus, et ce n'est pas une erreur.
    private static long octetsDeRegeneration(Path temporaires) {
        return dossiersDeRegeneration(temporaires).stream()
                .mapToLong(BancReactivation::octetsDuDossier)
                .sum();
    }

    /// Octets d'un dossier, `0` s'il s'est effacé pendant le parcours.
    private static long octetsDuDossier(Path dossier) {
        try (Stream<Path> fichiers = Files.walk(dossier)) {
            return fichiers.mapToLong(BancReactivation::tailleOuZero).sum();
        } catch (UncheckedIOException _) {
            return 0;
        } catch (IOException _) {
            return 0;
        }
    }

    private static long tailleOuZero(Path fichier) {
        try {
            return Files.isRegularFile(fichier) ? Files.size(fichier) : 0;
        } catch (NoSuchFileException _) {
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Une valeur de `/proc/meminfo`, en octets ; `-1` hors de Linux.
    private static long meminfo(String cle) {
        try (Stream<String> lignes = Files.lines(MEMINFO)) {
            return lignes.filter(ligne -> ligne.startsWith(cle + ":"))
                    .findFirst()
                    .map(ligne -> Long.parseLong(ligne.substring(ligne.indexOf(':') + 1)
                                    .replace("kB", "")
                                    .trim())
                            * 1024L)
                    .orElse(-1L);
        } catch (IOException | NumberFormatException _) {
            return -1L;
        }
    }

    private static long posteUtilise() {
        long total = meminfo("MemTotal");
        long disponible = meminfo("MemAvailable");
        return total < 0 || disponible < 0 ? -1L : total - disponible;
    }

    /// Relève, sur un fil démon, les pics du heap, des temporaires et de la mémoire du poste.
    private static final class Echantillonneur {
        private final Path temporaires;
        private final Path espaceDeTravail;
        private final AtomicLong picHeap = new AtomicLong(0);
        private final AtomicLong picTemporaires = new AtomicLong(0);
        private final AtomicLong picEspaceDeTravail = new AtomicLong(0);
        private final AtomicLong picPoste = new AtomicLong(0);
        private final AtomicLong picShmem = new AtomicLong(0);
        private final AtomicBoolean actif = new AtomicBoolean(true);
        private final long posteDepart = posteUtilise();
        private final long shmemDepart = meminfo("Shmem");
        private Thread fil;

        private Echantillonneur(Path temporaires, Path espaceDeTravail) {
            this.temporaires = temporaires;
            this.espaceDeTravail = espaceDeTravail;
        }

        private static Echantillonneur demarrer(Path temporaires, Path espaceDeTravail) {
            Echantillonneur e = new Echantillonneur(temporaires, espaceDeTravail);
            e.fil = Thread.ofPlatform().daemon(true).name("banc-reactivation").start(e::boucle);
            return e;
        }

        private void boucle() {
            while (actif.get()) {
                relever();
                try {
                    Thread.sleep(PERIODE_ECHANTILLON_MS);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void relever() {
            picHeap.accumulateAndGet(heapUtilise(), Math::max);
            picTemporaires.accumulateAndGet(octetsDeRegeneration(temporaires), Math::max);
            picEspaceDeTravail.accumulateAndGet(octetsDeRegeneration(espaceDeTravail), Math::max);
            picPoste.accumulateAndGet(posteUtilise(), Math::max);
            picShmem.accumulateAndGet(meminfo("Shmem"), Math::max);
        }

        private void arreter() {
            actif.set(false);
            try {
                fil.join();
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
            relever();
        }
    }
}
