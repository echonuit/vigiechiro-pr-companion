package fr.univ_amu.iut.commun.persistence;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.sqlite.SQLiteDataSource;

/// **Sauvegarde et restauration** de la base SQLite (#148) : la base concentre tout le travail
/// (sites, passages, observations), sans filet natif. Ce service permet d'en écrire une copie cohérente
/// et de repartir d'une sauvegarde.
///
/// - **Sauvegarde** : `VACUUM INTO` produit un **instantané cohérent** de la base dans un fichier
///   horodaté, même si une connexion est ouverte (contrairement à une copie brute qui pourrait rater le
///   journal WAL). Le fichier obtenu est une base SQLite autonome et compacte.
/// - **Restauration** : on vérifie d'abord que le fichier est une base **lisible**, on met de côté la base
///   courante (**filet de sécurité** avant écrasement, critère #148), on la remplace, on purge les fichiers
///   annexes (`-wal`/`-shm`/`-journal`) puis on **rejoue la migration** (idempotente) pour garantir un schéma
///   à jour : un état cohérent quelle que soit l'ancienneté de la sauvegarde.
///
/// Les connexions du socle sont **de courte durée** (ouvertes/fermées par opération, cf. [SourceDeDonnees]) :
/// aucune connexion longue à fermer pour remplacer le fichier. La restauration reste une action délibérée,
/// à faire hors opération concurrente.
public class ServiceSauvegarde {

    private static final String PREFIXE = "vigiechiro-sauvegarde-";
    private static final String PREFIXE_COMPLET = "vigiechiro-sauvegarde-complete-";
    private static final String SUFFIXE_FILET = ".avant-restauration";
    private static final String SOUS_DOSSIER_BASE = "base";
    private static final String SOUS_DOSSIER_SESSIONS = "sessions";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SourceDeDonnees source;
    private final Horloge horloge;
    private final InstantaneBase instantane;

    @Inject
    public ServiceSauvegarde(SourceDeDonnees source, Horloge horloge) {
        this.source = Objects.requireNonNull(source, "source");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.instantane = new InstantaneBase(this.source);
    }

    /// Écrit une sauvegarde cohérente de la base dans `dossierDestination` (créé au besoin), nommée
    /// `vigiechiro-sauvegarde-AAAAMMJJ-HHMMSS.db`. Renvoie le fichier créé. `dossierDestination` **choisi
    /// par l'appelant** rend l'emplacement configurable (critère #148).
    public Path sauvegarder(Path dossierDestination) {
        Objects.requireNonNull(dossierDestination, "dossierDestination");
        return instantane.ecrireDans(dossierDestination, PREFIXE + HORODATAGE.format(horloge.maintenant()));
    }

    /// Restaure la base depuis `sauvegarde`. Vérifie que le fichier est une base lisible, **met de côté**
    /// la base courante (`vigiechiro.db.avant-restauration`), la remplace, purge les fichiers annexes puis
    /// **migre** pour garantir un schéma à jour.
    ///
    /// @throws IllegalArgumentException si `sauvegarde` n'existe pas
    /// @throws DataAccessException si le fichier n'est pas une base SQLite lisible, ou en cas d'échec d'E/S
    public void restaurer(Path sauvegarde) {
        Objects.requireNonNull(sauvegarde, "sauvegarde");
        if (!Files.isRegularFile(sauvegarde)) {
            throw new IllegalArgumentException("Fichier de sauvegarde introuvable : " + sauvegarde);
        }
        verifierBaseLisible(sauvegarde);
        Path base = source.workspace().cheminBaseDeDonnees();
        try {
            Files.createDirectories(base.getParent());
            if (Files.exists(base)) {
                Files.copy(
                        base,
                        base.resolveSibling(base.getFileName() + SUFFIXE_FILET),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(sauvegarde, base, StandardCopyOption.REPLACE_EXISTING);
            purgerAnnexe(base, "-wal");
            purgerAnnexe(base, "-shm");
            purgerAnnexe(base, "-journal");
        } catch (IOException echec) {
            throw new DataAccessException("Restauration de la base impossible depuis " + sauvegarde, echec);
        }
        new MigrationSchema(source).migrer();
    }

    /// Dossier de sauvegarde **par défaut** (`<workspace>/sauvegardes`) : proposé quand l'utilisateur ne
    /// choisit pas d'emplacement. L'emplacement reste configurable (paramètre de [#sauvegarder]).
    public Path dossierParDefaut() {
        return source.workspace().racine().resolve("sauvegardes");
    }

    /// Vérifie que `fichier` est une base SQLite **intègre** (`PRAGMA quick_check` renvoie `ok`), via une
    /// source jetable pointant dessus. Lève [DataAccessException] sinon.
    private static void verifierBaseLisible(Path fichier) {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + fichier);
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                throw new DataAccessException("Le fichier n'est pas une sauvegarde valide : " + fichier, null);
            }
        } catch (SQLException echec) {
            throw new DataAccessException("Fichier de sauvegarde illisible : " + fichier, echec);
        }
    }

    /// Supprime un fichier annexe SQLite (`base-wal`, `base-shm`, `base-journal`) s'il existe, pour ne pas
    /// laisser un journal périmé masquer la base restaurée.
    private static void purgerAnnexe(Path base, String suffixe) throws IOException {
        Files.deleteIfExists(base.resolveSibling(base.getFileName() + suffixe));
    }

    /// **Sauvegarde complète** : base **et** dossiers de session (audio brut/transformé), prérequis d'un
    /// reset sûr (#1142). Contrairement à [#sauvegarder] (base seule, routine), produit un **dossier**
    /// `vigiechiro-sauvegarde-complete-AAAAMMJJ-HHMMSS/` contenant `base/vigiechiro.db` (instantané cohérent
    /// via `VACUUM INTO`) et `sessions/<dossier>/` (copie de chaque `recording_session.root_path` présent).
    /// Action **délibérée** (l'audio peut peser plusieurs Go) : à lancer avant un reset, hors opération
    /// concurrente.
    ///
    /// @return le **bilan** de la sauvegarde : le dossier créé, les sessions copiées, et **celles qui ne
    ///     l'ont pas été** (#1346)
    public BilanSauvegarde sauvegarderComplet(Path dossierDestination) {
        Objects.requireNonNull(dossierDestination, "dossierDestination");
        try {
            Path racineBackup = dossierLibreComplet(dossierDestination);
            instantane.ecrire(racineBackup.resolve(SOUS_DOSSIER_BASE).resolve(Workspace.FICHIER_BASE));
            Path dossierSessions = Files.createDirectories(racineBackup.resolve(SOUS_DOSSIER_SESSIONS));
            int copiees = 0;
            List<String> inaccessibles = new ArrayList<>();
            for (Path racineSession : racinesSessions()) {
                // Une racine absente n'est PAS une erreur (carte SD non montée, disque débranché) : la
                // sauvegarde doit aboutir. Mais la sauter en silence laissait croire à une copie complète
                // (#1346) : c'est la seule chose qu'on ne peut pas se permettre avant un reset (#1151).
                if (Files.isDirectory(racineSession)) {
                    copierRecursif(
                            racineSession,
                            dossierSessions.resolve(racineSession.getFileName().toString()));
                    copiees++;
                } else {
                    inaccessibles.add(racineSession.toString());
                }
            }
            return new BilanSauvegarde(racineBackup, copiees, inaccessibles);
        } catch (IOException | SQLException echec) {
            throw new DataAccessException("Sauvegarde complète impossible vers " + dossierDestination, echec);
        }
    }

    /// Restaure une **sauvegarde complète** produite par [#sauvegarderComplet] : remet la base (via
    /// [#restaurer] : vérification, filet de sécurité, migration) **puis** recopie les dossiers de session
    /// sauvegardés à la racine du workspace (écrasement). Action délibérée, hors opération concurrente.
    ///
    /// @throws IllegalArgumentException si le dossier ou sa base sont introuvables
    public void restaurerComplet(Path dossierBackup) {
        Objects.requireNonNull(dossierBackup, "dossierBackup");
        restaurer(dossierBackup.resolve(SOUS_DOSSIER_BASE).resolve(Workspace.FICHIER_BASE));
        Path dossierSessions = dossierBackup.resolve(SOUS_DOSSIER_SESSIONS);
        if (!Files.isDirectory(dossierSessions)) {
            return;
        }
        Path racineWorkspace = source.workspace().racine();
        try (Stream<Path> sessions = Files.list(dossierSessions)) {
            for (Path sessionSauvegardee : (Iterable<Path>) sessions::iterator) {
                if (Files.isDirectory(sessionSauvegardee)) {
                    copierRecursif(
                            sessionSauvegardee,
                            racineWorkspace.resolve(
                                    sessionSauvegardee.getFileName().toString()));
                }
            }
        } catch (IOException echec) {
            throw new DataAccessException(
                    "Restauration des dossiers de session impossible depuis " + dossierBackup, echec);
        }
    }

    /// Racines des sessions d'enregistrement (`recording_session.root_path`), lues directement : ce service
    /// socle sauvegarde la base dans son ensemble, connaître ses tables lui revient.
    private List<Path> racinesSessions() throws SQLException {
        List<Path> racines = new ArrayList<>();
        try (Connection cx = source.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT DISTINCT root_path FROM recording_session WHERE root_path IS NOT NULL")) {
            while (rs.next()) {
                racines.add(Path.of(rs.getString(1)));
            }
        }
        return racines;
    }

    /// Premier dossier de sauvegarde complète libre (horodaté, suffixé `-1`, `-2`… en cas de collision).
    private Path dossierLibreComplet(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        String base = PREFIXE_COMPLET + HORODATAGE.format(horloge.maintenant());
        Path candidat = dossier.resolve(base);
        int suffixe = 1;
        while (Files.exists(candidat)) {
            candidat = dossier.resolve(base + "-" + suffixe++);
        }
        return Files.createDirectories(candidat);
    }

    /// Copie récursive d'une arborescence (`origine` → `cible`), en écrasant les fichiers existants.
    private static void copierRecursif(Path origine, Path cible) throws IOException {
        try (Stream<Path> arbre = Files.walk(origine)) {
            for (Path chemin : (Iterable<Path>) arbre::iterator) {
                Path destination = cible.resolve(origine.relativize(chemin).toString());
                if (Files.isDirectory(chemin)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(chemin, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
