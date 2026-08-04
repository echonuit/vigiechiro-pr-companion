package fr.univ_amu.iut.commun.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/// Déplace **tous les chemins persistés** d'une session vers une nouvelle racine (#2727).
///
/// `recording_session.root_path` n'est pas le seul chemin en base : chaque original, chaque séquence
/// d'écoute, le journal du capteur, le relevé climatique et le CSV Tadarida portent le leur, en
/// **absolu**. Ne réécrire que la racine donne une base qui a l'air correcte et une application qui
/// ne trouve plus un seul fichier : c'est exactement ce qu'un E2E a montré, là où un test qui relit
/// `root_path` concluait au succès.
///
/// L'inventaire des six tables a été confronté au schéma (`grep` des colonnes `*_path` sur les 38
/// migrations) : aucune autre n'en porte. Une septième qui apparaîtrait devrait être ajoutée **ici**
/// et dans `RattachementDao.reprefixerChemins`, qui applique la même règle pour un autre besoin
/// (renommer une session rattachée) sans pouvoir partager ce code, le socle ne pouvant pas dépendre
/// d'une feature.
final class ReecritureRacineSession {

    private ReecritureRacineSession() {}

    /// Réécrit la racine de la session et tous les chemins qui en dépendent. Un chemin situé **hors**
    /// de l'ancienne racine est laissé tel quel : un original resté sur la carte SD (import sans
    /// copie) n'a pas bougé, et le rebaser pointerait vers un fichier qui n'existe pas.
    static void reenraciner(Connection cx, SessionARelocaliser session, Path ancienne, Path nouvelle)
            throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement("UPDATE recording_session SET root_path = ? WHERE id = ?")) {
            ps.setString(1, nouvelle.toString());
            ps.setLong(2, session.id());
            ps.executeUpdate();
        }
        for (TablesAChemin.TableAChemin colonne : TablesAChemin.toutes()) {
            Long cle = colonne.surLePassage() ? session.idPassage() : session.id();
            if (cle != null) {
                reenracinerTable(cx, colonne, cle, ancienne, nouvelle);
            }
        }
    }

    private static void reenracinerTable(
            Connection cx, TablesAChemin.TableAChemin colonne, long cle, Path ancienne, Path nouvelle)
            throws SQLException {
        Map<Long, String> chemins = new LinkedHashMap<>();
        try (PreparedStatement ps = cx.prepareStatement(
                "SELECT id, file_path FROM " + colonne.table() + " WHERE " + colonne.cle() + " = ?")) {
            ps.setLong(1, cle);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chemins.put(rs.getLong(1), rs.getString(2));
                }
            }
        }
        try (PreparedStatement ps =
                cx.prepareStatement("UPDATE " + colonne.table() + " SET file_path = ? WHERE id = ?")) {
            for (Map.Entry<Long, String> ligne : chemins.entrySet()) {
                String apres = reenracine(ligne.getValue(), ancienne, nouvelle);
                if (!apres.equals(ligne.getValue())) {
                    ps.setString(1, apres);
                    ps.setLong(2, ligne.getKey());
                    ps.executeUpdate();
                }
            }
        }
    }

    private static String reenracine(String stocke, Path ancienne, Path nouvelle) {
        Path chemin = Path.of(stocke);
        return chemin.startsWith(ancienne)
                ? nouvelle.resolve(ancienne.relativize(chemin)).toString()
                : stocke;
    }
}
