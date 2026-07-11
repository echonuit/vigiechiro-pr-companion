package fr.univ_amu.iut.lot.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// DAO du suivi de dépôt **par unité** (table `depot_unite`, #981) : le plan des fichiers à
/// téléverser vers VigieChiro et leur avancement, persistés au fil de l'eau pour rendre le dépôt
/// **reprenable** (#982) et réhydratable à la réouverture de M-Lot (#983).
///
/// Deux écritures :
/// - [#synchroniserPlan] : pose (ou complète) le plan d'un passage, **idempotente** — les unités déjà
///   suivies gardent leur statut (c'est la clé de la reprise), les manquantes sont ajoutées
///   « à déposer », celles sorties du plan (archives régénérées autrement) sont retirées ;
/// - [#mettreAJour] : avancement d'une unité (statut, id distant, message d'erreur), unitaire et
///   committée immédiatement — une interruption ne perd que l'unité en vol.
public class DepotUniteDao extends DaoGenerique<DepotUnite, Long> {

    private static final RowMapper<DepotUnite> MAPPER = rs -> new DepotUnite(
            rs.getLong("id"),
            rs.getLong("passage_id"),
            rs.getString("identifiant_unite"),
            TypeDepotUnite.parValeur(rs.getString("type")),
            StatutDepotUnite.parValeur(rs.getString("statut")),
            rs.getString("fichier_id_distant"),
            rs.getString("message_erreur"),
            rs.getString("maj_le"));

    public DepotUniteDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "depot_unite";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<DepotUnite> mapper() {
        return MAPPER;
    }

    @Override
    public DepotUnite insert(DepotUnite unite) {
        long id = insererEtRecupererCle(
                "INSERT INTO depot_unite (passage_id, identifiant_unite, type, statut, fichier_id_distant,"
                        + " message_erreur, maj_le) VALUES (?, ?, ?, ?, ?, ?, ?)",
                unite.passageId(),
                unite.identifiantUnite(),
                unite.type().valeur(),
                unite.statut().valeur(),
                unite.fichierIdDistant(),
                unite.messageErreur(),
                unite.majLe());
        return new DepotUnite(
                id,
                unite.passageId(),
                unite.identifiantUnite(),
                unite.type(),
                unite.statut(),
                unite.fichierIdDistant(),
                unite.messageErreur(),
                unite.majLe());
    }

    @Override
    public void update(DepotUnite unite) {
        mettreAJour(unite.id(), unite.statut(), unite.fichierIdDistant(), unite.messageErreur(), unite.majLe());
    }

    /// Unités suivies du passage, dans l'ordre du plan (id croissant). Vide si aucun dépôt n'a été
    /// entamé.
    public List<DepotUnite> parPassage(Long passageId) {
        return query("SELECT * FROM depot_unite WHERE passage_id = ? ORDER BY id", MAPPER, passageId);
    }

    /// Unités **restantes** du passage (tout sauf `depose`, dans l'ordre du plan) : celles que le
    /// moteur reprenable (#982) doit (re)téléverser. Une unité laissée `en_cours` par une interruption
    /// en fait partie — son téléversement n'a jamais été confirmé.
    public List<DepotUnite> restantes(Long passageId) {
        return query(
                "SELECT * FROM depot_unite WHERE passage_id = ? AND statut != ? ORDER BY id",
                MAPPER,
                passageId,
                StatutDepotUnite.DEPOSE.valeur());
    }

    /// `true` si le plan du passage existe **et** que toutes ses unités sont `depose` : la condition
    /// pour basculer le passage « Déposé » (#980). `false` sans plan (aucun dépôt entamé).
    public boolean toutesDeposees(Long passageId) {
        return !parPassage(passageId).isEmpty() && restantes(passageId).isEmpty();
    }

    /// Pose (ou complète) le **plan de dépôt** du passage, de façon **idempotente** :
    ///
    /// - une unité du plan déjà suivie (même `identifiant_unite`) est **conservée telle quelle**
    ///   (statut, id distant… — c'est ce qui rend la reprise possible) ;
    /// - une unité du plan encore inconnue est insérée telle que fournie (normalement « à déposer ») ;
    /// - une unité suivie qui ne figure plus dans le plan est **retirée** (archives régénérées
    ///   différemment : son suivi ne correspond plus à rien).
    ///
    /// Transactionnel : le plan résultant est cohérent même si l'application meurt pendant la pose.
    public void synchroniserPlan(Long passageId, List<DepotUnite> plan) {
        Set<String> identifiantsPlan = new LinkedHashSet<>();
        for (DepotUnite unite : plan) {
            identifiantsPlan.add(unite.identifiantUnite());
        }
        Set<String> dejaSuivies = new LinkedHashSet<>();
        for (DepotUnite existante : parPassage(passageId)) {
            dejaSuivies.add(existante.identifiantUnite());
        }
        try (Connection cx = source.getConnection()) {
            cx.setAutoCommit(false);
            try {
                supprimerHorsPlan(cx, passageId, identifiantsPlan);
                insererNouvelles(cx, passageId, plan, dejaSuivies);
                cx.commit();
            } catch (SQLException echec) {
                cx.rollback();
                throw echec;
            } finally {
                cx.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Synchronisation du plan de dépôt impossible (passage " + passageId + ")", e);
        }
    }

    /// Avancement d'une unité : nouveau statut, id distant du fichier créé (ou `null`), message
    /// d'erreur (ou `null`), horodatage. Committée immédiatement (persistance au fil de l'eau).
    public void mettreAJour(
            long id, StatutDepotUnite statut, String fichierIdDistant, String messageErreur, String majLe) {
        executerMaj(
                "UPDATE depot_unite SET statut = ?, fichier_id_distant = ?, message_erreur = ?, maj_le = ?"
                        + " WHERE id = ?",
                statut.valeur(),
                fichierIdDistant,
                messageErreur,
                majLe,
                id);
    }

    private static void supprimerHorsPlan(Connection cx, Long passageId, Set<String> identifiantsPlan)
            throws SQLException {
        try (PreparedStatement lister =
                        cx.prepareStatement("SELECT identifiant_unite FROM depot_unite WHERE passage_id = ?");
                PreparedStatement supprimer =
                        cx.prepareStatement("DELETE FROM depot_unite WHERE passage_id = ? AND identifiant_unite = ?")) {
            lister.setLong(1, passageId);
            try (var rs = lister.executeQuery()) {
                while (rs.next()) {
                    String identifiant = rs.getString(1);
                    if (!identifiantsPlan.contains(identifiant)) {
                        supprimer.setLong(1, passageId);
                        supprimer.setString(2, identifiant);
                        supprimer.addBatch();
                    }
                }
            }
            supprimer.executeBatch();
        }
    }

    private static void insererNouvelles(Connection cx, Long passageId, List<DepotUnite> plan, Set<String> dejaSuivies)
            throws SQLException {
        try (PreparedStatement insert = cx.prepareStatement(
                "INSERT INTO depot_unite (passage_id, identifiant_unite, type, statut, fichier_id_distant,"
                        + " message_erreur, maj_le) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (DepotUnite unite : plan) {
                if (dejaSuivies.contains(unite.identifiantUnite())) {
                    continue; // reprise : l'unité suivie garde son statut, on ne la réinsère pas
                }
                insert.setLong(1, passageId);
                insert.setString(2, unite.identifiantUnite());
                insert.setString(3, unite.type().valeur());
                insert.setString(4, unite.statut().valeur());
                insert.setString(5, unite.fichierIdDistant());
                insert.setString(6, unite.messageErreur());
                insert.setString(7, unite.majLe());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
