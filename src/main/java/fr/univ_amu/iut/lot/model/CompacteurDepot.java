package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// Compacte les fichiers d'un lot en **archives ZIP de dépôt** conformes aux contraintes Tadarida /
/// Vigie-Chiro (#110) :
///
/// - **plafond de taille** par archive ([#TAILLE_MAX_DEFAUT_OCTETS] par défaut, 700 Mo ; configurable) :
///   le lot est scindé en autant d'archives que nécessaire ;
/// - **nommage** : chaque archive porte le **préfixe R6** du passage suivi d'un **numéro croissant**
///   (`<préfixe>-1.zip`, `<préfixe>-2.zip`, …).
///
/// **Garantie « archive ≤ plafond ».** La répartition gloutonne (déléguée à [PlanificateurArchives]) ne se
/// fonde **pas** sur la seule taille source : DEFLATE peut très légèrement *grossir* des données peu
/// compressibles (blocs « stored » + overhead) et le format ZIP ajoute des en-têtes (local + répertoire
/// central + descripteur). Le coût réel de chaque entrée est donc majoré, avec une marge pour le répertoire
/// de fin (EOCD) ; en dernier recours, la taille réelle de l'archive est **vérifiée après écriture**
/// ([#verifierTaille]) — défense en profondeur si l'estimation était prise en défaut.
///
/// **Mémoire bornée** (#104) : chaque fichier est recopié **en flux** dans l'archive ([Files#copy]
/// vers le [ZipOutputStream]), jamais chargé entièrement en mémoire.
public final class CompacteurDepot {

    /// Plafond de taille d'une archive de dépôt **par défaut** : **700 Mo**. Base 1000 (et non 2^20) par
    /// prudence — c'est la borne basse d'une éventuelle interprétation « 700 Mio » côté plateforme, donc
    /// on reste conforme dans tous les cas. Surchargeable via le constructeur (réglage applicatif #110).
    public static final long TAILLE_MAX_DEFAUT_OCTETS = 700L * 1000 * 1000;

    /// Marge de sécurité (100 Mo) exigée **en plus** du volume estimé lors du contrôle d'espace disque :
    /// couvre les métadonnées du système de fichiers et une éventuelle sous-estimation.
    private static final long MARGE_SECURITE_OCTETS = 100L * 1000 * 1000;

    /// Ratio d'estimation de la taille des archives par rapport aux WAV source : le PCM des séquences se
    /// **compresse bien** en DEFLATE (~50 % observé, ex. une nuit de 13 Go → ~6 Go d'archives). On retient
    /// **60 %** par prudence (marge au-dessus du ratio observé) pour estimer l'espace disque nécessaire, au
    /// lieu de la taille source brute qui **surestimait ~2×** et bloquait à tort des générations qui
    /// tenaient. Estimation seulement (le pire cas incompressible reste rattrapé par le contrôle de taille
    /// par archive) ; un échec en cours d'écriture reste géré (session nettoyée).
    public static final double RATIO_COMPRESSION_ESTIME = 0.6;

    private final long tailleMaxOctets;
    private final EspaceDisque espaceDisque;
    private final PlanificateurArchives planificateur;

    public CompacteurDepot() {
        this(TAILLE_MAX_DEFAUT_OCTETS);
    }

    /// @param tailleMaxOctets plafond de taille par archive (configurable : réglage applicatif / tests)
    public CompacteurDepot(long tailleMaxOctets) {
        this(tailleMaxOctets, EspaceDisque.reel());
    }

    /// @param tailleMaxOctets plafond de taille par archive (configurable : réglage applicatif / tests)
    /// @param espaceDisque source de l'espace disque disponible dans le dossier cible (injectable pour les
    ///     tests ; par défaut l'espace réel du système de fichiers, cf. [EspaceDisque#reel()])
    public CompacteurDepot(long tailleMaxOctets, EspaceDisque espaceDisque) {
        if (tailleMaxOctets <= 0) {
            throw new IllegalArgumentException("Le plafond de taille d'archive doit être positif.");
        }
        this.tailleMaxOctets = tailleMaxOctets;
        this.espaceDisque = Objects.requireNonNull(espaceDisque, "espaceDisque");
        this.planificateur = new PlanificateurArchives(tailleMaxOctets);
    }

    /// Plafond de taille appliqué à chaque archive, en octets (exposé pour l'affichage du réglage).
    public long tailleMaxOctets() {
        return tailleMaxOctets;
    }

    /// Scinde `fichiers` en archives ZIP `<prefixe>-N.zip` (N croissant) écrites dans `dossierSortie`,
    /// **chacune garantie sous le plafond**. Renvoie la liste des archives produites, dans l'ordre.
    ///
    /// @throws RegleMetierException si un fichier dépasse à lui seul le plafond (indécoupable)
    public List<ArchiveDepot> compacter(List<Path> fichiers, String prefixe, Path dossierSortie) {
        return compacter(fichiers, prefixe, dossierSortie, progression -> {});
    }

    /// Variante avec **suivi de progression** (#769) : `progres` est notifié après chaque fichier compressé
    /// (« Compression X/N · fichier », fraction globale 0→1), pour qu'une barre déterminée + une estimation
    /// de durée s'affichent côté IHM. Appelé sur le fil d'exécution de la génération — la couche IHM relaie
    /// au fil JavaFX. Même contrat (plafond, atomicité, garde-fou disque) que la variante sans callback.
    ///
    /// @throws RegleMetierException si un fichier dépasse à lui seul le plafond, ou si l'espace disque est
    ///     insuffisant pour les archives estimées (garde-fou #769, avant toute écriture)
    public List<ArchiveDepot> compacter(
            List<Path> fichiers, String prefixe, Path dossierSortie, Consumer<Progression> progres) {
        return compacter(fichiers, prefixe, dossierSortie, progres, SuiviArchives.inerte());
    }

    /// Variante avec **suivi de progression global** ET **suivi par archive** (#820) : en plus de la barre
    /// globale (`progres`), `suivi` reçoit le cycle de vie de chaque ZIP (plan établi, démarrée, progresse,
    /// terminée, échec) pour afficher une ligne + une barre par archive. Comme la compression est parallèle
    /// (#814), les événements de `suivi` arrivent **dans le désordre** (ciblés par numéro) et depuis
    /// plusieurs fils : la couche IHM relaie au fil JavaFX. Même contrat que les autres variantes.
    ///
    /// @throws RegleMetierException si un fichier dépasse à lui seul le plafond, ou si l'espace disque est
    ///     insuffisant pour les archives estimées (garde-fou #769, avant toute écriture)
    public List<ArchiveDepot> compacter(
            List<Path> fichiers,
            String prefixe,
            Path dossierSortie,
            Consumer<Progression> progres,
            SuiviArchives suivi) {
        Objects.requireNonNull(fichiers, "fichiers");
        Objects.requireNonNull(prefixe, "prefixe");
        Objects.requireNonNull(dossierSortie, "dossierSortie");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(suivi, "suivi");
        try {
            Files.createDirectories(dossierSortie);
            verifierEspaceDisque(fichiers, dossierSortie);
            // Deux phases (#814) : on **planifie d'abord** tous les lots (partition par taille source
            // majorée, garantie « archive ≤ plafond », déléguée à PlanificateurArchives), puis on
            // **compresse les archives en parallèle**. Chaque archive étant indépendante, on exploite tous
            // les cœurs : la génération d'une nuit passe de ~dizaines de minutes à quelques minutes.
            List<List<Path>> lots = planificateur.partitionner(fichiers);
            suivi.planEtabli(planificateur.decrire(lots)); // pré-remplit la table de lignes « en attente » (#820)
            int total = fichiers.size();
            AtomicInteger faits = new AtomicInteger(0);
            return compresserEnParallele(lots, prefixe, dossierSortie, progres, suivi, total, faits);
        } catch (IOException e) {
            throw new UncheckedIOException("Génération des archives de dépôt impossible dans " + dossierSortie, e);
        }
    }

    /// Compresse les `lots` planifiés **en parallèle** (au plus un fil par cœur) : chaque archive étant
    /// indépendante, on écrit `<préfixe>-N.zip` sur plusieurs fils, la progression étant agrégée via le
    /// compteur atomique partagé `faits`. Les archives sont renvoyées **dans l'ordre des lots** (numéros
    /// 1..N croissants), quel que soit l'ordre d'achèvement. Un échec sur une archive interrompt la
    /// génération (cause remontée fidèlement).
    private List<ArchiveDepot> compresserEnParallele(
            List<List<Path>> lots,
            String prefixe,
            Path dossierSortie,
            Consumer<Progression> progres,
            SuiviArchives suivi,
            int total,
            AtomicInteger faits)
            throws IOException {
        int parallelisme =
                Math.max(1, Math.min(lots.size(), Runtime.getRuntime().availableProcessors()));
        try (ExecutorService executor = Executors.newFixedThreadPool(parallelisme)) {
            List<Future<ArchiveDepot>> enCours = new ArrayList<>(lots.size());
            for (int i = 0; i < lots.size(); i++) {
                int numero = i + 1;
                List<Path> lot = lots.get(i);
                enCours.add(executor.submit(
                        () -> ecrireArchive(lot, prefixe, dossierSortie, numero, progres, suivi, total, faits)));
            }
            List<ArchiveDepot> archives = new ArrayList<>(lots.size());
            for (Future<ArchiveDepot> future : enCours) {
                archives.add(recupererArchive(future));
            }
            return archives;
        }
    }

    /// Récupère le résultat d'une archive compressée hors-fil en **remontant fidèlement** la cause d'un
    /// échec (règle métier, plafond dépassé, I/O) plutôt que l'[ExecutionException] d'emballage.
    private ArchiveDepot recupererArchive(Future<ArchiveDepot> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Génération des archives de dépôt interrompue.", e));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new UncheckedIOException(new IOException("Génération d'une archive de dépôt échouée.", cause));
        }
    }

    /// Pré-contrôle d'espace disque **avant toute écriture** (#769) : on refuse tôt et proprement si le
    /// disque de destination n'a manifestement pas la place, plutôt que d'échouer à mi-génération en
    /// laissant des archives partielles. Le volume requis est **estimé compression comprise**
    /// ([#estimationTailleDepot] : les WAV compressent ~50 %), pour ne pas bloquer à tort une génération qui
    /// tiendrait.
    ///
    /// @throws RegleMetierException si l'espace disponible est inférieur au volume requis estimé
    private void verifierEspaceDisque(List<Path> fichiers, Path dossierSortie) throws IOException {
        long requis = estimationTailleDepot(planificateur.volumeSource(fichiers));
        long disponible = espaceDisque.disponibleOctets(dossierSortie);
        if (disponible < requis) {
            throw new RegleMetierException("Espace disque insuffisant pour générer les archives de dépôt :"
                    + " besoin d'environ "
                    + enGigaoctets(requis)
                    + " Go, seulement "
                    + enGigaoctets(disponible)
                    + " Go disponibles sur le disque de destination. Libérez de l'espace puis relancez la génération.");
        }
    }

    /// Estimation de l'espace disque nécessaire pour les archives d'un volume source `volumeSourceOctets`
    /// de séquences WAV : `volume × [#RATIO_COMPRESSION_ESTIME] + [#MARGE_SECURITE_OCTETS]`. Exposée pour
    /// que l'IHM anticipe (bouton/alerte) avec **le même calcul** que le garde-fou avant écriture (#…).
    public static long estimationTailleDepot(long volumeSourceOctets) {
        return (long) (volumeSourceOctets * RATIO_COMPRESSION_ESTIME) + MARGE_SECURITE_OCTETS;
    }

    /// Formate un nombre d'octets en gigaoctets (base 1000, une décimale) pour les messages utilisateur.
    private static String enGigaoctets(long octets) {
        return String.format(Locale.FRENCH, "%.1f", octets / 1_000_000_000.0);
    }

    private ArchiveDepot ecrireArchive(
            List<Path> fichiers,
            String prefixe,
            Path dossierSortie,
            int numero,
            Consumer<Progression> progres,
            SuiviArchives suivi,
            int total,
            AtomicInteger faits)
            throws IOException {
        Path archive = dossierSortie.resolve(prefixe + "-" + numero + ".zip");
        int dansArchive = fichiers.size();
        try {
            suivi.archiveDemarree(numero); // la ligne passe « en cours » (#820)
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
                int faitsArchive = 0;
                for (Path fichier : fichiers) {
                    zos.putNextEntry(new ZipEntry(fichier.getFileName().toString()));
                    Files.copy(fichier, zos); // recopie en flux : mémoire bornée (#104)
                    zos.closeEntry();
                    int n = faits.incrementAndGet();
                    progres.accept(new Progression(
                            "Compression " + n + "/" + total + " · " + fichier.getFileName(), n / (double) total));
                    suivi.archiveProgresse(numero, ++faitsArchive, dansArchive); // barre de CETTE archive (#820)
                }
            }
            long tailleReelle = Files.size(archive);
            verifierTaille(archive, tailleReelle);
            ArchiveDepot produite = new ArchiveDepot(archive, numero, tailleReelle, dansArchive);
            suivi.archiveTerminee(produite); // la ligne passe « terminée » + taille réelle (#820)
            return produite;
        } catch (IOException | RuntimeException e) {
            suivi.archiveEchouee(numero, e.getMessage()); // la ligne passe « échec » (#820)
            throw e;
        }
    }

    /// Défense en profondeur : si malgré la majoration une archive dépassait le plafond, on échoue
    /// explicitement plutôt que de livrer une archive non conforme à la plateforme.
    private void verifierTaille(Path archive, long tailleReelle) {
        if (tailleReelle > tailleMaxOctets) {
            throw new IllegalStateException("Archive de dépôt "
                    + archive.getFileName()
                    + " de "
                    + tailleReelle
                    + " o au-delà du plafond de "
                    + tailleMaxOctets
                    + " o (estimation de remplissage prise en défaut).");
        }
    }

    /// Source de l'**espace disque disponible** (en octets) dans un dossier cible, isolée en interface pour
    /// rendre le garde-fou de [#compacter] **testable** : les tests injectent une valeur basse (disque
    /// presque plein) sans dépendre de l'état réel de la machine. Par défaut [#reel()].
    @FunctionalInterface
    public interface EspaceDisque {

        /// Octets disponibles sur le système de fichiers hébergeant `dossier` (qui doit exister).
        long disponibleOctets(Path dossier) throws IOException;

        /// Espace réellement disponible sur le système de fichiers du dossier cible.
        static EspaceDisque reel() {
            return dossier -> Files.getFileStore(dossier).getUsableSpace();
        }
    }
}
