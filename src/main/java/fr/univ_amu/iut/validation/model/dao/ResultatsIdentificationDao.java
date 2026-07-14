package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// DAO de l'entité [ResultatsIdentification] (table `identification_results`, clé
/// auto-incrémentée).
///
/// Chaque jeu de résultats annote un seul passage (`passage_id` unique) :
/// [#findByPassage(Long)] renvoie donc un [Optional]. La suppression du passage supprime ses
/// résultats en cascade (`ON DELETE CASCADE`).
public class ResultatsIdentificationDao extends DaoGenerique<ResultatsIdentification, Long> {

    private static final RowMapper<ResultatsIdentification> MAPPER = rs -> new ResultatsIdentification(
            rs.getLong("id"),
            rs.getString("file_path"),
            rs.getString("detected_format"),
            rs.getString("imported_at"),
            rs.getLong("passage_id"));

    public ResultatsIdentificationDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "identification_results";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<ResultatsIdentification> mapper() {
        return MAPPER;
    }

    /// Résultats annotant un passage donné (au plus un, `passage_id` unique).
    public Optional<ResultatsIdentification> findByPassage(Long idPassage) {
        return queryUnique("SELECT * FROM identification_results WHERE passage_id = ?", MAPPER, idPassage);
    }

    /// Passages dont les résultats sont **déjà importés**, en une requête (#1338).
    ///
    /// « Analyse terminée » et « observations déjà en base » sont deux questions distinctes : la vue
    /// « résultats à importer » doit les croiser, sans quoi elle proposerait indéfiniment des nuits déjà
    /// traitées. Lecture en masse : le tableau multi-sites pose la question pour des milliers de lignes, et
    /// [#findByPassage] une par une ferait autant de requêtes.
    public Set<Long> passagesAvecResultats() {
        return findAll().stream().map(ResultatsIdentification::idPassage).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ResultatsIdentification insert(ResultatsIdentification resultats) {
        long id = insererEtRecupererCle(
                "INSERT INTO identification_results (file_path, detected_format, imported_at,"
                        + " passage_id) VALUES (?, ?, ?, ?)",
                resultats.cheminFichier(),
                resultats.formatDetecte(),
                resultats.dateImport(),
                resultats.idPassage());
        return new ResultatsIdentification(
                id,
                resultats.cheminFichier(),
                resultats.formatDetecte(),
                resultats.dateImport(),
                resultats.idPassage());
    }

    /// Variante transactionnelle : insère sur la `connexion` fournie (sans commit) pour grouper la
    /// création du jeu de résultats avec l'insertion de ses observations dans une seule unité de
    /// travail (import atomique). Renvoie le jeu avec sa clé générée.
    public ResultatsIdentification insert(Connection connexion, ResultatsIdentification resultats) throws SQLException {
        long id = insererEtRecupererCle(
                connexion,
                "INSERT INTO identification_results (file_path, detected_format, imported_at,"
                        + " passage_id) VALUES (?, ?, ?, ?)",
                resultats.cheminFichier(),
                resultats.formatDetecte(),
                resultats.dateImport(),
                resultats.idPassage());
        return new ResultatsIdentification(
                id,
                resultats.cheminFichier(),
                resultats.formatDetecte(),
                resultats.dateImport(),
                resultats.idPassage());
    }

    /// Variante transactionnelle : supprime le jeu de résultats d'un passage (et ses observations,
    /// cascade `ON DELETE CASCADE`) sur la `connexion` fournie, sans commit, pour grouper la
    /// suppression de l'ancien jeu avec l'insertion du nouveau dans une seule unité de travail
    /// (réimport atomique : si le nouveau CSV échoue, l'ancien n'est jamais perdu). Sans effet si
    /// aucun jeu n'existe pour ce passage.
    public void deleteParPassage(Connection connexion, Long idPassage) throws SQLException {
        executerMaj(connexion, "DELETE FROM identification_results WHERE passage_id = ?", idPassage);
    }

    @Override
    public void update(ResultatsIdentification resultats) {
        executerMaj(
                "UPDATE identification_results SET file_path = ?, detected_format = ?, imported_at = ?,"
                        + " passage_id = ? WHERE id = ?",
                resultats.cheminFichier(),
                resultats.formatDetecte(),
                resultats.dateImport(),
                resultats.idPassage(),
                resultats.id());
    }
}
