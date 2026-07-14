package fr.univ_amu.iut.commun.model.dao;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// DAO du **dernier état connu du traitement serveur** (table `participation_traitement`, cf.
/// [ReleveTraitement], #1262).
///
/// Un seul relevé par passage, **écrasé** à chaque rafraîchissement (`ON CONFLICT`) : on retient où en
/// est le calcul, pas par où il est passé. Écriture unique ([#enregistrer]), lecture unique
/// ([#pour]) — le cache n'a pas d'autre vie.
///
/// La lecture de l'état est **tolérante** comme celle du client (#1260) : une valeur inconnue (base
/// écrite par une version plus récente, ou serveur ayant introduit un état) redonne un traitement dont
/// l'état est `null`, jamais une exception.
public class ReleveTraitementDao extends DaoGenerique<ReleveTraitement, Long> {

    private static final RowMapper<ReleveTraitement> MAPPER = rs -> new ReleveTraitement(
            rs.getLong("passage_id"), rs.getString("participation_id"), lireTraitement(rs), rs.getString("releve_le"));

    public ReleveTraitementDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "participation_traitement";
    }

    @Override
    protected String colonneCle() {
        return "passage_id";
    }

    @Override
    protected RowMapper<ReleveTraitement> mapper() {
        return MAPPER;
    }

    /// Enregistre (ou écrase) le relevé du passage. Idempotent : deux rafraîchissements d'affilée
    /// laissent une seule ligne, celle du dernier.
    public void enregistrer(ReleveTraitement releve) {
        Traitement traitement = releve.traitement();
        executerMaj(
                "INSERT INTO participation_traitement (passage_id, participation_id, etat, date_planification,"
                        + " date_debut, date_fin, message, retry, releve_le)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(passage_id) DO UPDATE SET"
                        + " participation_id = excluded.participation_id, etat = excluded.etat,"
                        + " date_planification = excluded.date_planification, date_debut = excluded.date_debut,"
                        + " date_fin = excluded.date_fin, message = excluded.message, retry = excluded.retry,"
                        + " releve_le = excluded.releve_le",
                releve.idPassage(),
                releve.participationId(),
                traitement.etat() == null ? null : traitement.etat().name(),
                traitement.datePlanification(),
                traitement.dateDebut(),
                traitement.dateFin(),
                traitement.message(),
                traitement.retry(),
                releve.releveLe());
    }

    /// Dernier état connu du traitement de ce passage, ou vide si on ne l'a jamais relevé.
    public Optional<ReleveTraitement> pour(Long idPassage) {
        return findById(idPassage);
    }

    /// **Tous** les relevés, indexés par passage : la lecture en masse dont les vues d'ensemble ont besoin
    /// (#1338). Le tableau multi-sites affiche des milliers de lignes : les interroger une par une via
    /// [#pour] ferait autant de requêtes (N+1). La table ne porte qu'une ligne par nuit déposée relevée,
    /// elle tient donc sans peine en mémoire.
    public Map<Long, ReleveTraitement> parPassage() {
        return findAll().stream().collect(Collectors.toMap(ReleveTraitement::idPassage, releve -> releve));
    }

    /// Le cache n'a **qu'un seul chemin d'écriture** : poser un relevé est idempotent par nature (il n'y a
    /// rien à « créer » puis « modifier », seulement un dernier état connu à tenir à jour). Les deux entrées
    /// du contrat générique mènent donc à [#enregistrer].
    @Override
    public ReleveTraitement insert(ReleveTraitement releve) {
        enregistrer(releve);
        return releve;
    }

    /// Voir [#insert] : même chemin, l'upsert.
    @Override
    public void update(ReleveTraitement releve) {
        enregistrer(releve);
    }

    /// Bloc traitement d'une ligne : état lu **tolérante**ment, dates/message/essais tels quels.
    private static Traitement lireTraitement(ResultSet rs) throws SQLException {
        return new Traitement(
                EtatTraitement.depuis(rs.getString("etat")).orElse(null),
                rs.getString("date_planification"),
                rs.getString("date_debut"),
                rs.getString("date_fin"),
                rs.getString("message"),
                lireEntier(rs, "retry"));
    }

    /// Entier nullable : `null` en base doit rester `null` (et non `0`, que `getInt` renverrait).
    private static Integer lireEntier(ResultSet rs, String colonne) throws SQLException {
        int valeur = rs.getInt(colonne);
        return rs.wasNull() ? null : valeur;
    }
}
