package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.TablesAChemin;
import fr.univ_amu.iut.passage.model.ReprefixeurSession;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/// Écritures **connection-aware** de la modification rétroactive du rattachement d'un passage
/// (E2.S8) : nouveau quadruplet (année + n° de passage) et re-préfixage des chemins persistés.
///
/// Comme [fr.univ_amu.iut.importation.model.dao.AgregatImportDao], et pour la même raison
/// (SERVICE-CONVENTIONS §2.5), les méthodes prennent une [Connection] et sont conçues pour être
/// appelées **dans** un bloc `UniteDeTravail.executer(cx -> …)` : la mise à jour des sept tables
/// (passage, session, originaux, séquences, journal, relevé, résultats Tadarida) est ainsi **tout
/// ou rien**.
///
/// Chaque chemin est recalculé par [ReprefixeurSession#cheminApres] (relocalisation sous la
/// nouvelle racine, à l'identique du disque), jamais par un `replace` textuel : un parent qui
/// contiendrait par coïncidence le nom de dossier, ou un CSV Tadarida importé d'un chemin externe,
/// ne sont pas corrompus. Le SQL reste dans `model.dao` et ne référence aucune classe d'une autre
/// feature (la table `identification_results` est touchée par requête, sans cycle).
public class RattachementDao {

    /// Met à jour le quadruplet du passage (année + n° de passage). Connection-aware.
    public void majQuadruplet(Connection cx, long idPassage, int annee, int numeroPassage) throws SQLException {
        try (PreparedStatement ps =
                cx.prepareStatement("UPDATE passage SET year = ?, passage_number = ? WHERE id = ?")) {
            ps.setInt(1, annee);
            ps.setInt(2, numeroPassage);
            ps.setLong(3, idPassage);
            ps.executeUpdate();
        }
    }

    /// Réécrit les chemins persistés sous le dossier de la session : `root_path` (fixé à la nouvelle
    /// racine), `file_path`/`file_name` des originaux et séquences, `file_path` du journal, du relevé
    /// et du CSV Tadarida. Connection-aware. Un chemin hors de l'ancienne racine reste inchangé (cf.
    /// `cheminApres`).
    public void reprefixerChemins(
            Connection cx,
            long idPassage,
            long idSession,
            Path ancienneRacine,
            Path nouvelleRacine,
            String ancienPrefixe,
            String nouveauPrefixe)
            throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement("UPDATE recording_session SET root_path = ? WHERE id = ?")) {
            ps.setString(1, nouvelleRacine.toString());
            ps.setLong(2, idSession);
            ps.executeUpdate();
        }
        // Les tables à chemin viennent du socle (#3133) : elles étaient énumérées ici ET dans
        // `ReecritureRacineSession`, et la septième aurait été ajoutée à un endroit sur deux. Ce que
        // le rattachement en fait lui reste propre : lui seul renomme les noms logiques.
        for (TablesAChemin.TableAChemin table : TablesAChemin.toutes()) {
            reprefixerTable(
                    cx,
                    table,
                    table.surLePassage() ? idPassage : idSession,
                    ancienneRacine,
                    nouvelleRacine,
                    ancienPrefixe,
                    nouveauPrefixe);
        }
    }

    /// Recalcule, pour chaque ligne sélectionnée par `selectSql` (paramètre = `cle`), le `file_path` **physique**
    /// via [ReprefixeurSession#cheminApres] et, si `avecNom`, le `file_name` **logique** via [#reprefixerNom].
    /// Les deux sont indépendants : un fichier référencé **hors session** (import sans copie, brut sur la carte
    /// SD, jamais déplacé) garde son `file_path`, mais son **nom logique** doit tout de même suivre le nouveau
    /// préfixe (#1118). La ligne n'est mise à jour que si le chemin **ou** le nom change (`selectSql`
    /// doit alors lire `id, file_path[, file_name]`).
    private static void reprefixerTable(
            Connection cx,
            TablesAChemin.TableAChemin table,
            long cle,
            Path ancienneRacine,
            Path nouvelleRacine,
            String ancienPrefixe,
            String nouveauPrefixe)
            throws SQLException {
        boolean avecNom = table.avecNomDeFichier();
        String selectSql = "SELECT id, file_path" + (avecNom ? ", file_name" : "") + " FROM " + table.table()
                + " WHERE " + table.cle() + " = ?";
        String updateSql =
                "UPDATE " + table.table() + " SET file_path = ?" + (avecNom ? ", file_name = ?" : "") + " WHERE id = ?";
        Map<Long, String[]> lignes = new LinkedHashMap<>();
        try (PreparedStatement ps = cx.prepareStatement(selectSql)) {
            ps.setLong(1, cle);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lignes.put(rs.getLong(1), new String[] {rs.getString(2), avecNom ? rs.getString(3) : null});
                }
            }
        }
        try (PreparedStatement ps = cx.prepareStatement(updateSql)) {
            for (Map.Entry<Long, String[]> ligne : lignes.entrySet()) {
                String ancienChemin = ligne.getValue()[0];
                String nouveauChemin = ReprefixeurSession.cheminApres(
                        ancienChemin, ancienneRacine, nouvelleRacine, ancienPrefixe, nouveauPrefixe);
                String ancienNom = ligne.getValue()[1];
                String nouveauNom = avecNom ? reprefixerNom(ancienNom, ancienPrefixe, nouveauPrefixe) : null;
                boolean cheminChange = !nouveauChemin.equals(ancienChemin);
                boolean nomChange = avecNom && !nouveauNom.equals(ancienNom);
                if (!cheminChange && !nomChange) {
                    continue; // rien à réconcilier (chemin et nom déjà à jour)
                }
                ps.setString(1, nouveauChemin);
                if (avecNom) {
                    ps.setString(2, nouveauNom);
                    ps.setLong(3, ligne.getKey());
                } else {
                    ps.setLong(2, ligne.getKey());
                }
                ps.executeUpdate();
            }
        }
    }

    /// Re-préfixage **logique** d'un nom de fichier : remplace le préfixe de tête `ancienPrefixe` par
    /// `nouveauPrefixe`. Indépendant du chemin physique : nécessaire pour les fichiers **hors session** (bruts
    /// référencés sur la carte SD, jamais renommés) dont le `file_name` est un nom logique préfixé. Sans effet
    /// si le nom ne porte pas l'ancien préfixe (déjà réconcilié, ou nom non conforme laissé tel quel).
    private static String reprefixerNom(String nom, String ancienPrefixe, String nouveauPrefixe) {
        if (nom == null || !nom.startsWith(ancienPrefixe)) {
            return nom;
        }
        return nouveauPrefixe + nom.substring(ancienPrefixe.length());
    }
}
