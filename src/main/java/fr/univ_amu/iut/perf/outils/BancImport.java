package fr.univ_amu.iut.perf.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.di.ImportationModule;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/// OUTIL FOURNI : conservé dans la version étudiante (capture/mesure, utilisable tel quel).
///
/// Banc de mesure de l'**import** (#29, objectif O3) : génère une **nuit synthétique** de vrais WAV
/// (en-tête RIFF canonique, fréquence multiple de 10 — R10) + un journal `LogPR`, puis lance le
/// **vrai** [ServiceImport#importer] dessus et mesure le **temps total** (copie R9 vs transformation
/// R10/R11 parallélisée #12), le **débit** (fichiers/s, Mo/s) et la **mémoire crête** (échantillonnée).
/// À exécuter **sur une machine représentative** (IUT, JIT froid) pour fixer les ordres de grandeur.
///
/// Paramètres système :
///  - `vigiechiro.workspace` : dossier de travail, **entièrement réinitialisé** à chaque lancement
///    (dossier de benchmark jetable ; défaut : `<tmp>/vigiechiro-bench-import`) ;
///  - `perf.import.fichiers` : nombre de WAV de la nuit (défaut 100) ;
///  - `perf.import.go` : si fourni, **prime** sur `fichiers` et fixe la taille source cible en Gio ;
///  - `perf.import.secondes` : durée d'un WAV (défaut 5.0) ;
///  - `perf.import.frequenceHz` : fréquence d'échantillonnage, multiple de 10 (défaut 384000).
///
/// ⚠️ L'import recopie puis transforme les WAV : prévoir ~2 à 3× la taille source en espace disque.
public final class BancImport {

    private static final String SERIE = "1925492";
    private static final String ID_UTILISATEUR = "bench-enseignant";
    private static final int OCTETS_PAR_TRAME = 2; // mono, 16 bits
    private static final long MIO = 1024L * 1024L;
    private static final long GIO = 1024L * MIO;

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde temperature/hygrometrie presente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Parametres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
                    + " 8-120kHz\n";

    private BancImport() {}

    public static void main(String[] args) throws IOException {
        Path racine = Path.of(System.getProperty(
                "vigiechiro.workspace",
                Path.of(System.getProperty("java.io.tmpdir"), "vigiechiro-bench-import")
                        .toString()));
        double secondes = Double.parseDouble(System.getProperty("perf.import.secondes", "5.0"));
        int frequenceHz = Integer.getInteger("perf.import.frequenceHz", 384_000);
        long octetsParWav = (long) (44 + (long) frequenceHz * OCTETS_PAR_TRAME * secondes);
        int nbFichiers = nombreDeFichiers(octetsParWav);

        System.setProperty("vigiechiro.workspace", racine.toString());
        // Workspace entièrement réinitialisé : sinon, en relançant avec un autre nombre de fichiers
        // (calibrer puis monter en charge), les WAV de `source-sd` et les sessions copiées/transformées
        // de la passe précédente subsisteraient → mesure faussée, voire échec du renommage (R7) sur des
        // originaux déjà préfixés. C'est un dossier de **benchmark** jetable (cf. doc).
        reinitialiser(racine);

        Injector injecteur = creerInjecteur();
        injecteur.getInstance(MigrationSchema.class).migrer();
        Long idPoint = seeder(injecteur);

        // Génération de la nuit (hors mesure d'import : c'est de l'écriture disque préparatoire).
        Path sd = racine.resolve("source-sd");
        long debutGen = System.nanoTime();
        genererNuit(sd, nbFichiers, frequenceHz, secondes);
        double genS = (System.nanoTime() - debutGen) / 1e9;
        long tailleSource = (long) nbFichiers * octetsParWav;

        System.out.printf(
                Locale.ROOT,
                "=== Banc d'import (#29c) — nuit synthétique : %d WAV de %.1f s @ %d Hz = %.2f Gio (%.0f Mo) ===%n",
                nbFichiers,
                secondes,
                frequenceHz,
                tailleSource / (double) GIO,
                tailleSource / (double) MIO);
        System.out.printf(Locale.ROOT, "  génération nuit : %.2f s%n", genS);

        // Mesure de l'import : chrono total + bornes de phase + mémoire crête. La progression émet
        // « Copie X/N » puis « Transformation X/N » : le dernier événement de chaque type borne la fin
        // de la copie puis de la transformation ; le reliquat jusqu'à la fin est la persistance (O7).
        AtomicLong finCopieNs = new AtomicLong(0);
        AtomicLong finTransfoNs = new AtomicLong(0);
        EchantillonneurMemoire memoire = EchantillonneurMemoire.demarrer();
        ServiceImport service = injecteur.getInstance(ServiceImport.class);
        long debut = System.nanoTime();
        Consumer<Progression> sonde = p -> {
            long maintenant = System.nanoTime();
            if (p.libelle().startsWith("Copie")) {
                finCopieNs.accumulateAndGet(maintenant, Math::max);
            } else if (p.libelle().startsWith("Transformation")) {
                finTransfoNs.accumulateAndGet(maintenant, Math::max);
            }
        };
        ResultatImport resultat = service.importer(sd, idPoint, new Prefixe("640380", 2026, 1, "A1"), sonde);
        long finNs = System.nanoTime();
        long picMemoire = memoire.arreter();

        double totalS = (finNs - debut) / 1e9;
        double copieS = (finCopieNs.get() - debut) / 1e9;
        double transfoS = (finTransfoNs.get() - finCopieNs.get()) / 1e9;
        double persistanceS = (finNs - finTransfoNs.get()) / 1e9;
        System.out.printf(Locale.ROOT, "  temps total     : %.2f s%n", totalS);
        System.out.printf(Locale.ROOT, "    dont copie       : %.2f s%n", copieS);
        System.out.printf(Locale.ROOT, "    dont transfo (#12): %.2f s%n", transfoS);
        System.out.printf(Locale.ROOT, "    dont persistance : %.2f s%n", persistanceS);
        System.out.printf(
                Locale.ROOT,
                "  débit           : %.1f fichiers/s   %.1f Mo/s%n",
                nbFichiers / totalS,
                tailleSource / (double) MIO / totalS);
        System.out.printf(
                Locale.ROOT,
                "  produit         : %d originaux → %d séquences d'écoute%n",
                resultat.nombreOriginaux(),
                resultat.nombreSequences());
        System.out.printf(Locale.ROOT, "  mémoire crête   : %d Mo%n", picMemoire / MIO);
    }

    /// Nombre de WAV à générer : `perf.import.go` (taille cible Gio) prime s'il est fourni, sinon
    /// `perf.import.fichiers`. Au moins un fichier.
    private static int nombreDeFichiers(long octetsParWav) {
        String go = System.getProperty("perf.import.go");
        if (go != null) {
            long cibleOctets = (long) (Double.parseDouble(go) * GIO);
            return (int) Math.max(1, Math.ceil(cibleOctets / (double) octetsParWav));
        }
        return Math.max(1, Integer.getInteger("perf.import.fichiers", 100));
    }

    /// Vide complètement le dossier de benchmark (base + `source-sd` + sessions) puis le recrée, pour
    /// une mesure reproductible quel que soit le contenu d'une passe précédente.
    private static void reinitialiser(Path racine) throws IOException {
        if (Files.exists(racine)) {
            try (var chemins = Files.walk(racine)) {
                chemins.sorted(Comparator.reverseOrder()).forEach(chemin -> {
                    try {
                        Files.delete(chemin);
                    } catch (IOException echec) {
                        throw new UncheckedIOException("Nettoyage du workspace impossible : " + chemin, echec);
                    }
                });
            }
        }
        Files.createDirectories(racine);
    }

    /// Écrit le journal `LogPR`, un relevé `THLog` vide et `nbFichiers` WAV valides nommés
    /// `PaRecPR<série>_<date>_<heure>.wav` (dates/heures distinctes). Le PCM est identique d'un fichier
    /// à l'autre (seul le volume importe pour la mesure), écrit une fois puis réutilisé.
    private static void genererNuit(Path sd, int nbFichiers, int frequenceHz, double secondes) throws IOException {
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR" + SERIE + ".txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR" + SERIE + "_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        byte[] wav = construireWav(frequenceHz, secondes);
        DateTimeFormatter heure = DateTimeFormatter.ofPattern("HHmmss", Locale.ROOT);
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
        LocalDate base = LocalDate.of(2026, 4, 22);
        for (int i = 0; i < nbFichiers; i++) {
            LocalDate jour = base.plusDays(i / 86_400);
            LocalTime instant = LocalTime.ofSecondOfDay(i % 86_400);
            String nom = "PaRecPR" + SERIE + "_" + date.format(jour) + "_" + heure.format(instant) + ".wav";
            Files.write(sd.resolve(nom), wav);
        }
    }

    /// Un WAV PCM mono 16 bits de `secondes` à `frequenceHz` (en-tête RIFF canonique de 44 octets +
    /// signal en dents de scie déterministe). La fréquence doit être un **multiple de 10** (R10).
    private static byte[] construireWav(int frequenceHz, double secondes) {
        int trames = (int) (frequenceHz * secondes);
        byte[] pcm = new byte[trames * OCTETS_PAR_TRAME];
        for (int i = 0; i < trames; i++) {
            short echantillon = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1); // PCM
        buf.putShort((short) 1); // mono
        buf.putInt(frequenceHz);
        buf.putInt(frequenceHz * OCTETS_PAR_TRAME);
        buf.putShort((short) OCTETS_PAR_TRAME);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        return buf.array();
    }

    private static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(
                        new CommunModule(),
                        new PersistenceModule(),
                        new SitesModule(),
                        new PassageModule(),
                        new ImportationModule())
                .with(liaison -> liaison.bind(Horloge.class).toInstance(new HorlogeFigee(LocalDate.of(2026, 9, 20)))));
    }

    private static Long seeder(Injector injecteur) {
        new UtilisateurDao(injecteur.getInstance(SourceDeDonnees.class))
                .insert(new Utilisateur(ID_UTILISATEUR, "Banc de mesure (demo)"));
        ServiceSites service = injecteur.getInstance(ServiceSites.class);
        Site site = service.creerSite("640380", "Carré du banc", Protocole.STANDARD, "Aix-en-Provence", ID_UTILISATEUR);
        PointDEcoute point = service.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, "Pres du grand chene");
        return point.id();
    }

    /// Échantillonne la mémoire heap utilisée toutes les 100 ms sur un thread démon et retient le pic.
    private static final class EchantillonneurMemoire {
        private final AtomicLong pic = new AtomicLong(0);
        private final AtomicBoolean actif = new AtomicBoolean(true);

        private static EchantillonneurMemoire demarrer() {
            EchantillonneurMemoire e = new EchantillonneurMemoire();
            Thread.ofPlatform().daemon(true).name("bench-memoire").start(e::boucle);
            return e;
        }

        private void boucle() {
            Runtime rt = Runtime.getRuntime();
            while (actif.get()) {
                pic.accumulateAndGet(rt.totalMemory() - rt.freeMemory(), Math::max);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private long arreter() {
            actif.set(false);
            return pic.get();
        }
    }
}
