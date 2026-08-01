package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/// Écrit un **instantané cohérent** de la base dans un fichier, par `VACUUM INTO`.
///
/// `VACUUM INTO` plutôt qu'une copie de fichier : il produit une base SQLite autonome et compacte
/// même si une connexion est ouverte, là où une copie brute pourrait rater le journal WAL et donner
/// un fichier en retard sur la réalité.
///
/// C'est le geste que partagent les trois usages : la sauvegarde de routine, la sauvegarde complète
/// et le **filet posé avant une montée de version** (#2729). Il vit ici, et non dans
/// [ServiceSauvegarde], parce que [MigrationSchema] en a besoin et que le service, lui, appelle déjà
/// le migrateur : une dépendance directe entre les deux fermerait un cycle.
class InstantaneBase {

    private static final String EXTENSION = ".db";

    private final SourceDeDonnees source;

    InstantaneBase(SourceDeDonnees source) {
        this.source = source;
    }

    /// Écrit l'instantané dans `cible`, dont le dossier est créé au besoin.
    void ecrire(Path cible) {
        try {
            Files.createDirectories(cible.getParent());
            try (Connection cx = source.getConnection();
                    Statement st = cx.createStatement()) {
                st.execute("VACUUM INTO " + litteralSql(cible));
            }
        } catch (IOException | SQLException echec) {
            throw new DataAccessException("Instantané de la base impossible vers " + cible, echec);
        }
    }

    /// Écrit l'instantané dans `dossier` (créé au besoin) sous `nom`, suffixé `-1`, `-2`… si ce nom
    /// est déjà pris. Renvoie le fichier écrit.
    ///
    /// Le suffixe n'est pas un détail : les noms portent un horodatage à la seconde ou un numéro de
    /// version, deux choses qui peuvent se répéter. Sans lui, le second instantané écraserait le
    /// premier, et c'est exactement celui d'avant qu'on voudrait garder.
    Path ecrireDans(Path dossier, String nom) {
        Path cible = dossier.resolve(nom + EXTENSION);
        int suffixe = 1;
        while (Files.exists(cible)) {
            cible = dossier.resolve(nom + "-" + suffixe++ + EXTENSION);
        }
        ecrire(cible);
        return cible;
    }

    /// Littéral chaîne SQL à partir d'un chemin (apostrophes doublées) pour l'ordre `VACUUM INTO`.
    private static String litteralSql(Path chemin) {
        return "'" + chemin.toString().replace("'", "''") + "'";
    }
}
