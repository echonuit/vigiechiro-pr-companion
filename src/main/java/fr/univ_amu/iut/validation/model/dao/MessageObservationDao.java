package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.MessageObservation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// DAO du **fil de discussion** d'une observation (table `observation_message`, V26, #1417).
///
/// Le fil est un **reflet du serveur** : à chaque import VigieChiro, celui de la participation est
/// réécrit intégralement ([#remplacerFil]) plutôt que fusionné. Une fusion supposerait qu'un message
/// ait une identité stable côté serveur — il n'en a pas (l'ancrage est positionnel, l'ajout se fait
/// par `$push`), et un rapprochement au texte inventerait une identité qui n'existe pas.
///
/// [#filsDesObservations] charge les fils de **plusieurs** observations d'un coup : l'écran de
/// validation affiche une table de centaines de lignes, une requête par ligne la rendrait inutilisable.
public class MessageObservationDao extends DaoGenerique<MessageObservation, Long> {

    private static final String SQL_INSERT = "INSERT INTO observation_message"
            + " (observation_id, rank_in_thread, author_platform_id, body, posted_at)"
            + " VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE = "UPDATE observation_message SET observation_id = ?,"
            + " rank_in_thread = ?, author_platform_id = ?, body = ?, posted_at = ? WHERE id = ?";

    private static final RowMapper<MessageObservation> MAPPER = rs -> new MessageObservation(
            rs.getLong("id"),
            rs.getLong("observation_id"),
            rs.getInt("rank_in_thread"),
            rs.getString("author_platform_id"),
            rs.getString("body"),
            instant(rs.getString("posted_at")));

    public MessageObservationDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "observation_message";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<MessageObservation> mapper() {
        return MAPPER;
    }

    /// Le fil d'une observation, dans l'ordre du serveur (vide si personne n'a jamais écrit — le cas
    /// courant).
    public List<MessageObservation> filDeLObservation(Long idObservation) {
        return query(
                "SELECT * FROM observation_message WHERE observation_id = ? ORDER BY rank_in_thread",
                MAPPER,
                idObservation);
    }

    /// Fils de **toutes** les observations d'un jeu de résultats, indexés par observation : une seule
    /// requête pour peupler un écran entier. Les observations sans message n'ont pas d'entrée dans la
    /// carte (à l'appelant de retomber sur une liste vide).
    public Map<Long, List<MessageObservation>> filsDesObservations(Long idResultats) {
        List<MessageObservation> tous = query(
                "SELECT m.* FROM observation_message m"
                        + " JOIN observation o ON m.observation_id = o.id"
                        + " WHERE o.results_id = ?"
                        + " ORDER BY m.observation_id, m.rank_in_thread",
                MAPPER,
                idResultats);
        return tous.stream().collect(Collectors.groupingBy(MessageObservation::idObservation));
    }

    /// **Remplace** le fil d'une observation par `messages` (suppression puis insertion, dans la
    /// `connexion` de l'appelant : c'est son unité de travail qui commite). Le rang est celui du record,
    /// pas la position dans la liste : l'appelant reste maître de l'ordre du serveur.
    public void remplacerFil(Connection connexion, Long idObservation, List<MessageObservation> messages)
            throws SQLException {
        executerMaj(connexion, "DELETE FROM observation_message WHERE observation_id = ?", idObservation);
        if (messages.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connexion.prepareStatement(SQL_INSERT)) {
            for (MessageObservation message : messages) {
                lier(ps, valeurs(message));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public MessageObservation insert(MessageObservation message) {
        long id = insererEtRecupererCle(SQL_INSERT, valeurs(message));
        return new MessageObservation(
                id, message.idObservation(), message.rang(), message.auteur(), message.texte(), message.date());
    }

    @Override
    public void update(MessageObservation message) {
        Object[] colonnes = valeurs(message);
        Object[] avecId = new Object[colonnes.length + 1];
        System.arraycopy(colonnes, 0, avecId, 0, colonnes.length);
        avecId[colonnes.length] = message.id();
        executerMaj(SQL_UPDATE, avecId);
    }

    /// Valeurs positionnelles de [#SQL_INSERT]. La date est normalisée en ISO-8601 (instant UTC) ;
    /// `null` reste `null` — un message sans date reste un message.
    private static Object[] valeurs(MessageObservation message) {
        return new Object[] {
            message.idObservation(),
            message.rang(),
            message.auteur(),
            message.texte(),
            message.date() == null ? null : message.date().toString()
        };
    }

    /// Relit un instant ISO-8601 stocké. **Tolérant** : absent ou illisible → `null`, comme au parsing
    /// (une date qu'on n'arrive plus à relire ne doit pas emporter le message avec elle).
    private static Instant instant(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(texte.trim());
        } catch (RuntimeException formatInattendu) {
            return null;
        }
    }
}
