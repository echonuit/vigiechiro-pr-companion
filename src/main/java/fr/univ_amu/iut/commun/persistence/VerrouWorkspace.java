package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Le **verrou d'un workspace** : un seul processus à la fois y écrit (#2731).
///
/// Rien ne l'empêchait jusqu'ici. Deux instances graphiques, une IHM et une CLI, ou une restauration
/// pendant un import : la base était remplacée sous les pieds de celui qui écrivait. Toutes les
/// garanties posées par ce lot (migration atomique #2728, filet #2729, restaurations vérifiées #2727
/// et #2730) tombent si un second processus écrit pendant l'opération.
///
/// **Verrou de fichier système**, et non fichier de PID. La différence est décisive : le système
/// d'exploitation **relâche** le verrou quand le processus meurt, donc un plantage ne condamne pas le
/// workspace. Un fichier de PID demanderait de savoir si le PID 12345 est encore vivant, question qui
/// n'a pas de réponse portable, et un verrou qu'on ne sait pas relâcher est pire que pas de verrou :
/// il transforme un incident en blocage définitif.
///
/// Le PID et l'horodatage sont **écrits dans le fichier** pour le message, jamais pour la décision.
public final class VerrouWorkspace implements AutoCloseable {

    /// Nom du fichier de verrou, à la racine du workspace.
    /// Nom du fichier de verrou. **Public** parce qu'un test hors de ce paquet doit pouvoir simuler
    /// une occupation par un autre processus, ce qu'un verrou pris ici ne ferait pas : il serait
    /// reentrant (#3498).
    public static final String NOM_FICHIER = ".verrou";

    /// Ce que **ce processus** verrouille déjà. Une JVM ne peut pas prendre deux fois le même verrou
    /// de fichier, et n'a d'ailleurs pas à se protéger d'elle-même : l'IHM tient le verrou pour toute
    /// sa durée, et une restauration lancée depuis cette IHM doit passer.
    private static final Set<Path> DETENUS = ConcurrentHashMap.newKeySet();

    /// Le **seul** octet verrouillé, et le nom de l'occupant commence juste après.
    ///
    /// ⚠️ Verrouiller le fichier entier rendait la fonctionnalité de #3571 **inerte sous Windows** :
    /// là-bas un verrou est **impératif**, et le second processus - celui qu'on refuse - ne pouvait
    /// pas relire le nom qu'il devait afficher. Sous POSIX il est consultatif, la lecture passait, et
    /// aucun test ne pouvait le voir avant la matrice trois plateformes de #3525.
    ///
    /// L'exclusion ne faiblit pas : deux prises se chevauchent toujours sur cet octet. Et la
    /// cohabitation de deux versions tient dans les deux sens - une version antérieure verrouille tout
    /// le fichier, donc l'octet 0 avec, et une version nouvelle se voit refusée ; l'inverse aussi.
    private static final long OCTET_DU_VERROU = 0L;

    /// L'octet-sentinelle qui précède le nom de l'occupant : c'est lui qui porte le verrou, et il est
    /// retiré à la relecture. Un fichier qui ne le porte pas vient d'une version antérieure ou d'un
    /// outil tiers, et se lit **tel quel** (#3693).
    private static final String SENTINELLE = "#";

    /// L'horodatage tel que [#inscrireLOccupant] l'écrit, repéré pour être reformaté à l'affichage.
    private static final Pattern HORODATAGE_ISO =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?");

    private final FileChannel canal;
    private final FileLock verrou;
    private final Path fichier;

    /// `false` pour un jeton d'opération exclusive qui **réutilise** la détention du processus : le
    /// fermer ne doit pas relâcher le verrou de celui qui le tenait avant.
    private final boolean aRelacher;

    private VerrouWorkspace(FileChannel canal, FileLock verrou, Path fichier, boolean aRelacher) {
        this.canal = canal;
        this.verrou = verrou;
        this.fichier = fichier;
        this.aRelacher = aRelacher;
    }

    /// Ouvre le verrou le temps d'une **opération exclusive** (migration, restauration, reset).
    ///
    /// Trois cas, et un seul est un refus :
    ///
    /// - ce processus détient déjà le verrou (l'IHM) : on rend un jeton qui ne relâche rien ;
    /// - le workspace est libre : on le prend, et le jeton le rendra ;
    /// - un **autre** processus l'occupe : refus, en le nommant. C'est tout l'objet de #2731 : mieux
    ///   vaut « le workspace est utilisé par le processus 12345 » qu'un échec SQLite tardif, au
    ///   milieu d'une écriture, que personne ne sait interpréter.
    ///
    /// @throws DataAccessException si un autre processus occupe le workspace
    public static VerrouWorkspace pourOperationExclusive(Workspace workspace, String operation) {
        Path fichier = fichierDe(workspace);
        if (DETENUS.contains(fichier)) {
            return new VerrouWorkspace(null, null, fichier, false);
        }
        return prendre(workspace)
                .orElseThrow(() -> new RefusAvantEcriture(
                        "Ce dossier de travail est déjà utilisé par " + quiLOccupe(workspace) + " :"
                                + " impossible de lancer " + operation + " en même temps. Fermez l'autre"
                                + " fenêtre ou attendez la fin de l'opération en cours, puis recommencez.",
                        null));
    }

    /// Tente de prendre le verrou. [Optional] **vide** si un autre processus le tient : c'est un
    /// refus, pas une erreur, et l'appelant décide quoi en dire.
    public static Optional<VerrouWorkspace> prendre(Workspace workspace) {
        Path fichier = fichierDe(workspace);
        try {
            Files.createDirectories(fichier.getParent());
            FileChannel canal = FileChannel.open(
                    fichier, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
            FileLock verrou = canal.tryLock(OCTET_DU_VERROU, 1, false);
            if (verrou == null) {
                canal.close();
                return Optional.empty();
            }
            inscrireLOccupant(canal);
            DETENUS.add(fichier);
            return Optional.of(new VerrouWorkspace(canal, verrou, fichier, true));
        } catch (OverlappingFileLockException dejaPrisIci) {
            // Ce processus le tient déjà : la JVM refuse de le prendre deux fois. Vu d'ici c'est le
            // même cas qu'un autre processus, l'appelant n'a pas à écrire.
            return Optional.empty();
        } catch (IOException echec) {
            throw new DataAccessException("Verrou du workspace impossible à poser : " + fichier, echec);
        }
    }

    /// Qui occupe le workspace, en clair, pour un message. Chaîne **vide** si le fichier ne dit rien
    /// (verrou libre, ou fichier illisible) : cette lecture ne sert qu'à mieux expliquer un refus,
    /// elle ne doit jamais le provoquer.
    public static String occupant(Workspace workspace) {
        try {
            Path fichier = fichierDe(workspace);
            if (!Files.isRegularFile(fichier)) {
                return "";
            }
            String contenu = lireLeContenu(fichier);
            // ⚠️ Ce qui ne porte pas la sentinelle vient d'une version antérieure ou d'un outil tiers :
            // on le rend tel quel plutôt que d'en amputer le premier caractère. Même règle que #3640 -
            // ne transformer que ce qu'on reconnaît rend la compatibilité gratuite.
            return contenu.startsWith(SENTINELLE) ? contenu.substring(SENTINELLE.length()) : contenu;
        } catch (IOException illisible) {
            return "";
        }
    }

    /// Le contenu du fichier, en évitant l'octet que le détenteur verrouille.
    ///
    /// ⚠️ Deux lectures, et l'ordre compte. `Files.readString` lit **tout le fichier depuis l'octet 0**,
    /// donc il traverse la zone verrouillée : sous Windows, où un verrou est impératif, il échoue même
    /// quand un seul octet est pris. Déplacer le contenu hors de cet octet ne suffisait pas - il fallait
    /// que le **lecteur** l'évite aussi. C'est la moitié du remède que le premier correctif de #3693
    /// avait manquée, et que la matrice de #3525 a désignée.
    ///
    /// La lecture entière vient d'abord parce qu'elle est la seule qui rende un fichier **sans
    /// sentinelle** - version antérieure, outil tiers - dans son intégralité. Elle réussit toujours sous
    /// POSIX, et sous Windows dès que personne ne tient le verrou. Quand elle échoue, c'est qu'un
    /// détenteur est là : on relit alors **à partir de l'octet 1**, celui que ce détenteur a laissé
    /// libre.
    private static String lireLeContenu(Path fichier) throws IOException {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException verrouille) {
            return apresLOctetDuVerrou(fichier);
        }
    }

    /// Le repli : relire en **sautant** l'octet que le détenteur verrouille.
    ///
    /// ⚠️ Extrait du `catch` ci-dessus, et **visible du paquet**, pour une raison mesurée : ce chemin ne
    /// s'exécute que **sous Windows**, seul système où le verrou est impératif. Sous POSIX,
    /// `Files.readString` réussit toujours, donc le `catch` est inatteignable - et PIT, qui tourne sous
    /// Linux, rendait ici **quatre mutants sans couverture**, dont la borne `<= 0` et la soustraction.
    /// Le remède de #3714 était livré sans qu'aucune mesure locale puisse le juger (#3561, passe 6).
    ///
    /// Le passage hebdomadaire sous Windows exerce le **câblage** - que la lecture emprunte bien ce
    /// repli quand le verrou est tenu. Cette couture-ci rend la **borne** éprouvable partout, et les
    /// deux sont nécessaires : l'une sans l'autre laisse la moitié du remède non jugée.
    ///
    /// ## Deux survivants PIT, **assumés** et de la même famille
    ///
    /// L'arithmétique ci-dessous est **défensive**, pas sémantique : le contrat observable est « rendre
    /// tout ce qui suit l'octet 0 ». Deux mutants y survivent donc, et aucun test ne peut les tuer.
    ///
    /// - `restant <= 0` en `< 0` : pour un fichier d'un seul octet, `restant` vaut 0, et
    ///   `ByteBuffer.allocate(0)` suivi d'une lecture à l'offset 1 rend `""` - exactement ce que la
    ///   garde rendait. La garde ne sert vraiment qu'au fichier **vide**, où `restant` vaut -1 et où
    ///   `allocate(-1)` lèverait ;
    /// - `size() - 1` en `size() + 1` : sur-allouer ne change rien, `read` s'arrête à EOF et `flip()`
    ///   borne le tampon à ce qui a été lu.
    ///
    /// Comme pour `EcritureAtomique`, ce sont des **équivalents par construction**, pas une couverture
    /// manquante. Les écrire ainsi reste juste : `allocate` refuse une taille négative, et un lecteur
    /// comprend `<= 0` sans avoir à raisonner sur le cas 0.
    static String apresLOctetDuVerrou(Path fichier) throws IOException {
        try (FileChannel canal = FileChannel.open(fichier, StandardOpenOption.READ)) {
            long restant = canal.size() - 1;
            if (restant <= 0) {
                return "";
            }
            ByteBuffer tampon = ByteBuffer.allocate((int) restant);
            canal.read(tampon, OCTET_DU_VERROU + 1);
            return StandardCharsets.UTF_8.decode(tampon.flip()).toString();
        }
    }

    /// Ce qu'on écrit dans le refus : le nom quand on l'a, une formule **honnête** quand on ne l'a pas.
    ///
    /// ⚠️ Le message affichait « déjà utilisé **()** » dès que le verrou venait d'ailleurs que d'un
    /// `VerrouWorkspace` - un processus tiers, un fichier tronqué, une tentative morte. Des parenthèses
    /// vides promettent un nom et n'en donnent aucun, ce qui est pire que de ne rien promettre : le
    /// lecteur cherche l'information manquante au lieu d'agir (#3571).
    ///
    /// Les deux formes disent la même chose à l'utilisateur - **quelqu'un d'autre est dans ce dossier,
    /// fermez-le** - et l'une lui donne en plus de quoi retrouver le coupable.
    /// Le **complément** qui nomme l'occupant, ou rien du tout : ` (processus 4821, depuis …)`, ou la
    /// chaîne vide. À coller derrière la phrase de chaque surface, qui garde son propre sujet.
    ///
    /// Pure et publique parce que **deux** messages la portent : le refus d'une opération, ici, et
    /// l'alerte de démarrage de l'application ([Amorcage#messageDossierOccupe]). Les deux affichaient
    /// des parenthèses vides dès que le verrou venait d'ailleurs qu'un `VerrouWorkspace` - un processus
    /// tiers, un fichier tronqué, une tentative morte (#3571).
    public static String complementOccupant(String inscrit) {
        String propre = inscrit == null ? "" : inscrit.strip();
        return propre.isEmpty() ? "" : " (" + enFrancais(propre) + ")";
    }

    /// Rend lisible l'horodatage **reconnu** dans la chaîne inscrite, et lui seul.
    ///
    /// L'instant est **écrit en ISO** et **formaté ici** : le fichier est relu par un autre processus,
    /// parfois d'une version différente, et un format localisé y serait un mauvais support - un instant
    /// qu'on ne peut ni comparer ni trier. Mais il finit dans une phrase française, deux écrans après
    /// une table qui écrit `01/08/2026 12:15` : l'y laisser en `2026-08-03T21:14:07` donnait à lire un
    /// format de machine au milieu du texte (#3640).
    ///
    /// ⚠️ **Ne remplace que ce qu'elle reconnaît**, et c'est ce qui rend le repli gratuit : un verrou
    /// écrit par une version antérieure, posé par un outil tiers ou tronqué ne porte aucun horodatage
    /// ISO valide, donc il ressort **verbatim** - exactement le comportement d'avant, sans une ligne de
    /// code de compatibilité. Une date impossible ne lève pas : elle n'est simplement pas reconnue.
    private static String enFrancais(String inscrit) {
        Matcher trouve = HORODATAGE_ISO.matcher(inscrit);
        if (!trouve.find()) {
            return inscrit;
        }
        try {
            LocalDateTime instant = LocalDateTime.parse(trouve.group());
            return new StringBuilder(inscrit)
                    .replace(trouve.start(), trouve.end(), Horodatage.dansUnTableau(instant))
                    .toString();
        } catch (DateTimeParseException pasUneDate) {
            return inscrit;
        }
    }

    private static String quiLOccupe(Workspace workspace) {
        return "une autre instance" + complementOccupant(occupant(workspace));
    }

    /// `true` tant que ce verrou est tenu par ce processus.
    public boolean detenu() {
        return DETENUS.contains(fichier);
    }

    /// Relâche le verrou, **sauf** pour un jeton qui réutilisait la détention d'un autre : fermer une
    /// opération exclusive lancée depuis l'IHM ne doit pas rendre le workspace que l'IHM tient.
    @Override
    public void close() {
        if (!aRelacher) {
            return;
        }
        try (FileChannel aFermer = canal) {
            verrou.release();
            DETENUS.remove(fichier);
        } catch (IOException echec) {
            throw new DataAccessException("Verrou du workspace impossible à relâcher", echec);
        }
    }

    private static Path fichierDe(Workspace workspace) {
        return workspace.racine().resolve(NOM_FICHIER);
    }

    /// Écrit qui tient le verrou. Le contenu est **informatif** : un second processus le lira pour
    /// nommer l'occupant, mais c'est le verrou système qui décide.
    private static void inscrireLOccupant(FileChannel canal) throws IOException {
        String occupant = "processus " + ProcessHandle.current().pid() + ", depuis " + LocalDateTime.now();
        canal.truncate(0);
        canal.position(0);
        // La sentinelle occupe l'octet verrouillé ; le nom vient derrière, donc hors de la zone que
        // Windows refuse de laisser lire (#3693).
        canal.write(StandardCharsets.UTF_8.encode(SENTINELLE + occupant));
        canal.force(true);
    }
}
