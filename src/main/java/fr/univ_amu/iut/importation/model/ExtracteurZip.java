package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.CopieInterruptible;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/// Décompresse une **archive `.zip`** de carte SD vers un dossier temporaire, pour que l'import
/// (#139) accepte un zip aussi bien qu'un dossier déjà décompressé. La source n'est jamais modifiée.
///
/// **Sur disque, pas en RAM** : le dossier d'extraction naît **sous le workspace** et non dans
/// `java.io.tmpdir`, souvent un *tmpfs* borné à quelques Go qu'une nuit de terrain (~10 Go) ferait
/// déborder en `ENOSPC`. Extraction et import partagent ainsi le même volume. **Mémoire bornée**
/// (#104) : chaque entrée est recopiée en flux ([CopieInterruptible]), jamais chargée entière. Une
/// entrée dont le chemin s'évaderait du dossier (`../…`) est **refusée** - garde « zip-slip ».
public final class ExtracteurZip {

    private static final Logger LOG = Logger.getLogger(ExtracteurZip.class.getName());

    private ExtracteurZip() {}

    /// `true` si `chemin` désigne une archive `.zip` (par l'extension, insensible à la casse).
    public static boolean estZip(Path chemin) {
        return chemin != null && chemin.getFileName().toString().toLowerCase().endsWith(".zip");
    }

    /// Variante sans suivi de progression (extraction silencieuse), pour les appels qui n'affichent rien.
    public static Path extraireVersDossierTemporaire(Path archiveZip, Path dossierBase) {
        return extraireVersDossierTemporaire(archiveZip, dossierBase, p -> {}, JetonAnnulation.neutre());
    }

    /// Variante avec progression mais **sans annulation** (jeton neutre).
    public static Path extraireVersDossierTemporaire(
            Path archiveZip, Path dossierBase, Consumer<Progression> surProgression) {
        return extraireVersDossierTemporaire(archiveZip, dossierBase, surProgression, JetonAnnulation.neutre());
    }

    /// Extrait `archiveZip` vers un **dossier neuf créé sous `dossierBase`** et rend ce dossier. En
    /// cas d'échec ou d'annulation, le dossier partiellement extrait est nettoyé.
    ///
    /// **Progression déterminée** (#146) : le total est lu d'abord dans l'index du zip (`ZipFile`,
    /// instantané - seul le répertoire central est lu), puis `surProgression` est notifié après chaque
    /// fichier, et **par paliers à l'intérieur** d'une grosse entrée (#2733), sans quoi le compteur ne
    /// bougerait pas de toute sa durée. Le callback peut être invoqué **hors du fil JavaFX**.
    /// **Annulation** : `jeton` est vérifié avant chaque entrée et **pendant** la copie de chacune.
    ///
    /// @param dossierBase volume d'accueil de l'extraction (workspace disque), créé s'il manque
    /// @param surProgression notifié à chaque fichier extrait (avancement déterminé)
    /// @param jeton jeton d'annulation coopérative (utiliser [JetonAnnulation#neutre()] pour ne pas annuler)
    /// @throws RegleMetierException si une entrée tente de s'évader du dossier (zip-slip)
    public static Path extraireVersDossierTemporaire(
            Path archiveZip, Path dossierBase, Consumer<Progression> surProgression, JetonAnnulation jeton) {
        return extraireVersDossierTemporaire(
                archiveZip, dossierBase, surProgression, jeton, BornesExtraction.parDefaut());
    }

    /// Variante à **bornes explicites** (#2732) : `bornes` refuse l'archive qui s'annonce hors limites
    /// avant que le premier octet soit écrit, puis arrête celle qui écrit plus qu'elle n'annonçait. La
    /// production passe par [BornesExtraction#parDefaut()] ; ce point d'entrée sert à éprouver les refus
    /// sans dépendre de l'état réel du disque, et à toute utilisation qui aurait besoin d'autres bornes.
    ///
    /// @throws RegleMetierException si une borne de ressources est franchie (le temporaire éventuel est
    ///     nettoyé, et le refus préalable n'en crée aucun)
    public static Path extraireVersDossierTemporaire(
            Path archiveZip,
            Path dossierBase,
            Consumer<Progression> surProgression,
            JetonAnnulation jeton,
            BornesExtraction bornes) {
        InventaireArchive inventaire = inventorier(archiveZip, dossierBase, bornes);
        Path racine = creerDossierExtraction(dossierBase);
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archiveZip)))) {
            int total = inventaire.nbFichiers();
            int faits = 0;
            long cumul = 0;
            ZipEntry entree;
            while ((entree = zis.getNextEntry()) != null) {
                jeton.leverSiAnnule(); // arrêt au plus tôt, entre deux entrées
                ZipEntry courante = entree;
                int rang = faits;
                long dejaEcrit = cumul;
                cumul += extraireUneEntree(zis, racine, courante, jeton, octets -> {
                    // Le garde des octets RÉELS : une archive qui ment sur sa taille se trahit ici, et
                    // nulle part avant, puisque le garde préalable lit justement ce mensonge.
                    bornes.exigerCumulSousLePlafond(dejaEcrit + octets, inventaire);
                    surProgression.accept(progressionEnCours(rang, total, nomFichier(courante), octets));
                });
                zis.closeEntry();
                if (!courante.isDirectory()) {
                    surProgression.accept(progression(++faits, total, nomFichier(courante)));
                }
            }
            // Re-vérification finale : une annulation pendant la dernière entrée ne doit pas laisser
            // l'extraction « aboutir » et la source être inspectée (le catch ci-dessous nettoie).
            jeton.leverSiAnnule();
        } catch (IOException e) {
            ArborescenceFichiers.effacerAuMieux(racine);
            // On expose la cause (ex. « Aucun espace disponible sur le périphérique ») : sans elle,
            // l'utilisateur ne saurait pas qu'il s'agit d'un manque de place disque.
            throw new UncheckedIOException(
                    "Décompression du zip impossible : " + archiveZip + " (" + e.getMessage() + ")", e);
        } catch (RuntimeException e) {
            ArborescenceFichiers.effacerAuMieux(racine);
            throw e;
        }
        return racine;
    }

    private static Path creerDossierExtraction(Path dossierBase) {
        try {
            Files.createDirectories(dossierBase);
            return Files.createTempDirectory(dossierBase, "import-zip-");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Création du dossier d'extraction impossible sous " + dossierBase + " (" + e.getMessage() + ")", e);
        }
    }

    /// Inventorie l'archive et **la refuse si elle s'annonce hors bornes** (#2732), avant qu'un seul
    /// octet ne soit écrit et avant même que le dossier temporaire n'existe.
    ///
    /// L'inventaire sert deux fois : il donne le dénominateur de la progression « X / N » (ce que faisait
    /// l'ancien `compterFichiers`, à la même lecture du répertoire central), et le plafond auquel les
    /// octets réellement écrits seront confrontés.
    ///
    /// Le dossier d'accueil est créé avant la mesure : l'espace disponible se lit sur un chemin qui
    /// existe.
    private static InventaireArchive inventorier(Path archiveZip, Path dossierBase, BornesExtraction bornes) {
        try {
            Files.createDirectories(dossierBase);
            InventaireArchive inventaire = InventaireArchive.lire(archiveZip);
            bornes.verifierAvantExtraction(inventaire, dossierBase);
            return inventaire;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Lecture de l'archive impossible : " + archiveZip + " (" + e.getMessage() + ")", e);
        }
    }

    private static Progression progression(int faits, int total, String nomFichier) {
        String compteur =
                total <= 0 ? "Décompression : " + faits + " fichier(s)" : "Décompression : " + faits + " / " + total;
        double fraction = total <= 0 ? 1.0 : (double) faits / total;
        return new Progression(compteur + " · " + nomFichier, fraction);
    }

    /// Point de progression **à l'intérieur** d'une entrée (#2733) : le compteur de fichiers n'a pas
    /// bougé, mais le volume écrit dit que la copie avance.
    ///
    /// La fraction reste celle des fichiers **achevés**. La taille décompressée d'une entrée n'est pas
    /// connue de façon fiable en lecture au fil de l'eau (`ZipEntry#getSize` vaut souvent `-1` tant que
    /// l'entrée n'est pas close) : gonfler la barre annoncerait un avancement inventé. C'est le libellé
    /// qui porte le signe de vie.
    private static Progression progressionEnCours(int faits, int total, String nomFichier, long octetsEcrits) {
        Progression achevee = progression(faits, total, nomFichier);
        return new Progression(achevee.libelle() + " · " + Formats.octetsLisibles(octetsEcrits), achevee.fraction());
    }

    /// Nom de fichier (dernier segment) d'une entrée zip, pour afficher le **fichier courant** (#146)
    /// sans le chemin interne complet de l'archive.
    private static String nomFichier(ZipEntry entree) {
        String nom = entree.getName();
        int slash = nom.lastIndexOf('/');
        return slash < 0 ? nom : nom.substring(slash + 1);
    }

    /// @return les octets écrits par cette entrée, que l'appelant cumule pour confronter l'archive à sa
    ///     propre déclaration (#2732)
    private static long extraireUneEntree(
            ZipInputStream zis, Path racine, ZipEntry entree, JetonAnnulation jeton, LongConsumer surPalier)
            throws IOException {
        Path cible = racine.resolve(entree.getName()).normalize();
        if (!cible.startsWith(racine)) {
            throw new RegleMetierException(
                    "Archive zip invalide : l'entrée « " + entree.getName() + " » sort du dossier d'extraction.");
        }
        if (entree.isDirectory()) {
            Files.createDirectories(cible);
            return 0;
        }
        Files.createDirectories(cible.getParent());
        try (OutputStream os = Files.newOutputStream(cible)) {
            return CopieInterruptible.copier(zis, os, jeton, surPalier);
        }
    }

    /// Renvoie le **dossier à inspecter** dans `extrait`. Une archive créée par « compresser ce dossier »
    /// contient un **unique dossier racine** (`MaNuit/LogPR…`, `MaNuit/bruts/…`) : l'inspection, qui
    /// cherche le journal et les WAV à la racine du dossier source (et dans `bruts/`), ne verrait alors
    /// rien. On déplie donc en cascade tout dossier racine unique pour pointer sur le vrai contenu de la
    /// nuit ; une archive déjà « à plat » est renvoyée inchangée. **Ne supprime rien** : le temporaire à
    /// nettoyer reste la racine renvoyée par [#extraireVersDossierTemporaire].
    public static Path racineEffective(Path extrait) {
        Path courant = extrait;
        try {
            while (true) {
                List<Path> enfants;
                try (Stream<Path> contenu = Files.list(courant)) {
                    enfants = contenu.toList();
                }
                if (enfants.size() == 1 && Files.isDirectory(enfants.get(0))) {
                    courant = enfants.get(0);
                } else {
                    return courant;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du dossier extrait impossible : " + extrait, e);
        }
    }

    /// Supprime les dossiers d'extraction `import-zip-*` **résiduels** sous `dossierBase` : filet
    /// anti-fuite pour les temporaires laissés par un écran d'import abandonné (le ViewModel d'import,
    /// non-singleton, ne garde pas la référence à nettoyer). Appelé avant une nouvelle extraction.
    /// Best-effort.
    public static void nettoyerTemporairesResiduels(Path dossierBase) {
        if (dossierBase == null || !Files.isDirectory(dossierBase)) {
            return;
        }
        try (Stream<Path> entrees = Files.list(dossierBase)) {
            entrees.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("import-zip-"))
                    .forEach(ArborescenceFichiers::effacerAuMieux);
        } catch (IOException echec) {
            // Best-effort : un balayage incomplet n'est pas une erreur métier.
            LOG.log(Level.FINE, echec, () -> "Balayage des dossiers d'import incomplet sous " + dossierBase);
        }
    }
}
