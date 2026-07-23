package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/// Fournit des [Connection] vers la base SQLite du [Workspace].
///
/// C'est l'unique endroit qui connaît l'URL JDBC : les DAO, l'unité de travail et la migration de
/// schéma reçoivent une `SourceDeDonnees` et ignorent tout du driver. La source est binder en
/// **singleton** Guice (une seule base pour toute l'application).
///
/// Chaque connexion active l'intégrité référentielle (`PRAGMA foreign_keys = ON`) : SQLite
/// n'applique les clés étrangères que si on le demande explicitement (objectif qualité intégrité
/// O7). On le fait à deux niveaux par sécurité : via [SQLiteConfig#enforceForeignKeys(boolean)] et
/// via un `PRAGMA` explicite à l'ouverture.
///
/// Chaque connexion pose aussi un `busy_timeout` (#984) : le dépôt VigieChiro téléverse désormais en
/// parallèle, plusieurs threads écrivant `depot_unite` sur le même fichier SQLite (un seul writer à la
/// fois en mode journal par défaut). Sans ce délai, un writer concurrent échouerait immédiatement sur
/// `SQLITE_BUSY` ; avec, il réessaie le temps que le verrou se libère.
public class SourceDeDonnees {

    /// Délai d'attente d'un verrou d'écriture avant `SQLITE_BUSY`. 10 s couvrent largement les
    /// écritures `depot_unite` du dépôt parallèle (statuts en_cours / depose / echec), minuscules et
    /// qui se sérialisent en pratique instantanément.
    private static final int DELAI_VERROU_MS = 10_000;

    private final Workspace workspace;
    private final SQLiteDataSource dataSource;

    /// Crée une source pointant vers la base du workspace, qui est `<workspace>/vigiechiro.db` sauf
    /// emplacement choisi (#1038). En test, on passe un `Workspace` construit sur un `@TempDir` (base
    /// jetable) ; en production, c'est le workspace résolu.
    public SourceDeDonnees(Workspace workspace) {
        this.workspace = workspace;
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(DELAI_VERROU_MS);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + workspace.cheminBaseDeDonnees());
        this.dataSource = source;
    }

    /// Ouvre une nouvelle connexion (clés étrangères activées). L'appelant est responsable de la
    /// fermer (idéalement dans un `try-with-resources`).
    public Connection getConnection() {
        try {
            // Création paresseuse : la base ne peut exister sans son dossier. C'est celui de la BASE
            // qu'il faut créer, pas la racine du workspace : depuis #1038 les deux peuvent différer, et
            // créer la racine ne servirait alors à rien pour ouvrir le fichier.
            Files.createDirectories(workspace.cheminBaseDeDonnees().getParent());
            Connection connexion = dataSource.getConnection();
            try (Statement st = connexion.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = " + DELAI_VERROU_MS);
            }
            return connexion;
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Connexion SQLite impossible (" + workspace + ")", e);
        }
    }

    /// Workspace adossé à cette source (utile pour résoudre les chemins de sessions).
    public Workspace workspace() {
        return workspace;
    }
}
