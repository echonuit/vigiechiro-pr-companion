package fr.univ_amu.iut.commun.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/// Pose une ligne dont les valeurs passent par des **paramètres**, jamais par la concaténation.
///
/// Le risque pratique d'une concaténation en test est nul, l'entrée venant du harnais. Ce qui reste
/// vrai est qu'un échappement écrit à la main est précisément ce qu'une requête préparée existe pour
/// éviter, et que l'alerte `java/concatenated-sql-query` ne se ferme pas autrement (#4509).
///
/// Cette classe existe parce que les mêmes gestes vivaient dans trois classes de test de sauvegarde,
/// et que les y laisser aurait fait de la plus grosse une [GodClass] au sens du portail.
final class InsertionParametree {

    private InsertionParametree() {}

    /// Un `Path` se pose par son chemin, un `Long` par sa valeur, le reste par sa représentation.
    /// Trois formes suffisent : les tables à chemin de ce paquet ne portent rien d'autre.
    static void poser(Connection cx, String sql, Object... valeurs) throws SQLException {
        try (PreparedStatement ordre = cx.prepareStatement(sql)) {
            for (int i = 0; i < valeurs.length; i++) {
                Object valeur = valeurs[i];
                if (valeur instanceof Path chemin) {
                    ordre.setString(i + 1, chemin.toString());
                } else if (valeur instanceof Long entier) {
                    ordre.setLong(i + 1, entier);
                } else {
                    ordre.setString(i + 1, String.valueOf(valeur));
                }
            }
            ordre.executeUpdate();
        }
    }
}
