package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.sqlite.SQLiteDataSource;

/// Remet en place le **fichier de base** d'une sauvegarde, et sait revenir en arrière (#2730).
///
/// Pendant du [RestaurationComplete], qui s'occupe des dossiers de son : les deux gestes sont assez
/// différents pour vivre chacun chez soi, et [ServiceSauvegarde] les enchaîne.
///
/// La restauration remplaçait le fichier **puis** migrait. Une migration qui refuse laissait donc une
/// base ni restaurée ni migrée, sur laquelle l'application ne démarre plus. Le filet
/// (`vigiechiro.db.avant-restauration`) existait, mais le remettre était un geste manuel que rien
/// n'annonçait. Deux garde-fous encadrent maintenant l'échange : un refus **avant**, un retour
/// arrière **après**.
class RestaurationBase {

    private static final String SUFFIXE_FILET = ".avant-restauration";

    private final SourceDeDonnees source;

    RestaurationBase(SourceDeDonnees source) {
        this.source = source;
    }

    /// Vérifie, remplace, migre, et rétablit l'état d'avant si la migration échoue.
    ///
    /// @throws IllegalArgumentException si `sauvegarde` n'existe pas
    /// @throws DataAccessException si le fichier n'est pas une base lisible, s'il vient d'une version
    ///     plus récente, ou si la migration de la base restaurée échoue
    void executer(Path sauvegarde) {
        if (!Files.isRegularFile(sauvegarde)) {
            throw new IllegalArgumentException("Fichier de sauvegarde introuvable : " + sauvegarde);
        }
        verifierBaseLisible(sauvegarde);
        refuserSiEcriteParUneVersionPlusRecente(sauvegarde);
        try (VerrouWorkspace verrou = VerrouWorkspace.pourOperationExclusive(source.workspace(), "la restauration")) {
            remplacerPuisMigrer(sauvegarde);
        }
    }

    /// Remplace le fichier et migre, sous le verrou du workspace : personne d'autre n'écrit pendant
    /// que la base est échangée (#2731).
    private void remplacerPuisMigrer(Path sauvegarde) {
        Path base = source.workspace().cheminBaseDeDonnees();
        boolean baseExistait = Files.exists(base);
        try {
            Files.createDirectories(base.getParent());
            if (baseExistait) {
                Files.copy(base, filet(base), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(sauvegarde, base, StandardCopyOption.REPLACE_EXISTING);
            purgerAnnexes(base);
        } catch (IOException echec) {
            throw new DataAccessException("Restauration de la base impossible depuis " + sauvegarde, echec);
        }
        migrerOuRevenirEnArriere(base, baseExistait);
    }

    /// Migre la base restaurée, et **remet l'état d'avant** si la migration échoue (#2730).
    ///
    /// Sans base avant la restauration, il n'y a pas de filet à remettre : on **supprime** le fichier
    /// restauré. L'état initial était « pas de base », et c'est celui-là qu'on rend.
    private void migrerOuRevenirEnArriere(Path base, boolean baseExistait) {
        try {
            new MigrationSchema(source).migrer();
        } catch (RuntimeException echec) {
            revenirEnArriere(base, baseExistait);
            throw new DataAccessException(
                    "La sauvegarde a été remise en place mais la mise à jour de son schéma a échoué : "
                            + message(echec)
                            + (baseExistait
                                    ? " Votre base d'avant la restauration a été rétablie, rien n'est perdu."
                                    : " Le fichier restauré a été retiré : vous repartez comme avant.")
                            + " Essayez une autre sauvegarde, ou une version plus récente de l'application.",
                    echec);
        }
    }

    private static void revenirEnArriere(Path base, boolean baseExistait) {
        try {
            if (baseExistait) {
                Files.copy(filet(base), base, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(base);
            }
            purgerAnnexes(base);
        } catch (IOException impossible) {
            throw new DataAccessException(
                    "La mise à jour du schéma a échoué et la base d'avant n'a pas pu être rétablie."
                            + " Elle est encore là, sous " + filet(base) + " : remettez-la à la place de "
                            + base + ".",
                    impossible);
        }
    }

    /// Refuse une sauvegarde dont le schéma dépasse ce que cette version sait appliquer (#2730).
    ///
    /// Le refus a lieu **avant** tout remplacement : une base écrite par une version plus récente
    /// porte des tables et des colonnes inconnues ici, et la migration ne les rattrapera pas, leurs
    /// scripts n'existant pas dans ce binaire. La laisser entrer produirait le pire des états : une
    /// base en place que l'application ne sait pas lire.
    ///
    /// L'information est prise dans le fichier de sauvegarde lui-même, et non dans un manifeste :
    /// une sauvegarde de base seule n'en a pas, et bénéficie du même refus.
    private static void refuserSiEcriteParUneVersionPlusRecente(Path sauvegarde) {
        int versionSauvegarde = versionDeSchema(sauvegarde);
        int versionApplication = MigrationSchema.versionMaximale();
        if (versionSauvegarde > versionApplication) {
            throw new DataAccessException(
                    "Cette sauvegarde a été écrite par une version plus récente de l'application (schéma "
                            + versionSauvegarde + ", cette version connaît le " + versionApplication
                            + "). Rien n'a été touché. Mettez l'application à jour, puis recommencez.",
                    null);
        }
    }

    /// Version de schéma inscrite dans un fichier de sauvegarde, `0` si la table `schema_version` n'y
    /// est pas (base d'avant son introduction, ou base neuve) : dans ce cas la migration fera le
    /// travail, il n'y a rien à refuser.
    private static int versionDeSchema(Path sauvegarde) {
        SQLiteDataSource lecture = new SQLiteDataSource();
        lecture.setUrl("jdbc:sqlite:" + sauvegarde);
        try (Connection cx = lecture.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException tableAbsente) {
            return 0;
        }
    }

    /// Vérifie que `fichier` est une base SQLite **intègre** (`PRAGMA quick_check` renvoie `ok`), via
    /// une source jetable pointant dessus.
    private static void verifierBaseLisible(Path fichier) {
        SQLiteDataSource lecture = new SQLiteDataSource();
        lecture.setUrl("jdbc:sqlite:" + fichier);
        try (Connection cx = lecture.getConnection();
                Statement st = cx.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                throw new DataAccessException("Le fichier n'est pas une sauvegarde valide : " + fichier, null);
            }
        } catch (SQLException echec) {
            throw new DataAccessException("Fichier de sauvegarde illisible : " + fichier, echec);
        }
    }

    /// Supprime les fichiers annexes SQLite (`-wal`, `-shm`, `-journal`) s'ils existent, pour ne pas
    /// laisser un journal périmé masquer la base qu'on vient de mettre en place. Nécessaire dans les
    /// **deux** sens : après une restauration comme après un retour arrière, la migration avortée
    /// ayant pu en écrire.
    private static void purgerAnnexes(Path base) throws IOException {
        for (String suffixe : List.of("-wal", "-shm", "-journal")) {
            Files.deleteIfExists(base.resolveSibling(base.getFileName() + suffixe));
        }
    }

    private static Path filet(Path base) {
        return base.resolveSibling(base.getFileName() + SUFFIXE_FILET);
    }

    private static String message(RuntimeException echec) {
        return echec.getMessage() == null ? echec.toString() : echec.getMessage();
    }
}
