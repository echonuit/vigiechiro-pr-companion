package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Taxon;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// DAO de l'entité [Taxon] (table `taxon`).
///
/// Illustre une **clé naturelle** (`code`, TEXT) : comme `UtilisateurDao`, l'insertion n'utilise
/// pas `insererEtRecupererCle` (aucune clé générée) mais [#executerMaj(String, Object...)], et
/// renvoie l'entité telle quelle. Les colonnes nom latin / nom vernaculaire sont nullable (lues
/// directement via `rs.getString`, qui renvoie `null`).
public class TaxonDao extends DaoGenerique<Taxon, String> {

    private static final RowMapper<Taxon> MAPPER = rs -> new Taxon(
            rs.getString("code"),
            rs.getString("latin_name"),
            rs.getString("vernacular_name_fr"),
            rs.getLong("group_id"));

    public TaxonDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "taxon";
    }

    @Override
    protected String colonneCle() {
        return "code";
    }

    @Override
    protected RowMapper<Taxon> mapper() {
        return MAPPER;
    }

    /// Taxons rattachés à un groupe taxonomique donné, triés par code.
    /// Codes des taxons **prioritaires** au sens du Plan National d'Actions Chiroptères (table latérale
    /// `taxon_prioritaire`, V36, #2353). Lecture **groupée** : les écrans en gardent un instantané et le
    /// consultent une fois par ligne affichée, ce qu'une requête par ligne ne supporterait pas.
    ///
    /// Lu ici, avec le reste du référentiel taxonomique, plutôt que par un DAO à part : la table latérale
    /// n'a pas de vie propre — l'application ne l'écrit jamais, seule la migration l'alimente. Un second
    /// DAO n'aurait fait qu'un collaborateur de plus à faire descendre jusqu'aux écrans, dans des
    /// constructeurs déjà au plafond.
    ///
    /// `LinkedHashSet` : l'ordre de lecture reste stable d'une exécution à l'autre.
    public Set<String> codesPrioritaires() {
        return new LinkedHashSet<>(projeter(
                "SELECT taxon_code FROM taxon_prioritaire ORDER BY taxon_code", rs -> rs.getString("taxon_code")));
    }

    public List<Taxon> findByGroupe(Long idGroupe) {
        return query("SELECT * FROM taxon WHERE group_id = ? ORDER BY code", MAPPER, idGroupe);
    }

    /// Enregistre des taxons **hors référentiel** (auto-souches de l'import tolérant) : pour chaque `code`
    /// inconnu, insère une ligne minimale (code seul, sans nom latin) rattachée au groupe « Hors
    /// référentiel » (V04), afin de respecter la FK `observation.taxon_tadarida -> taxon(code)` sans
    /// rejeter tout l'import quand le CSV contient des taxons non semés. `INSERT OR IGNORE` : un code déjà
    /// présent est laissé intact. Écrit sur la `connexion` de la transaction d'import (atomicité).
    public void enregistrerHorsReferentiel(Connection connexion, Collection<String> codes) throws SQLException {
        if (codes.isEmpty()) {
            return;
        }
        // Nom latin et vernaculaire NULL : la souche est identifiée par son groupe « Hors référentiel »
        // (V04), et la vue affiche alors le code Tadarida brut (pas un nom inventé).
        String sql = "INSERT OR IGNORE INTO taxon (code, latin_name, vernacular_name_fr, group_id)"
                + " SELECT ?, NULL, NULL, g.id FROM taxonomic_group g WHERE g.name = 'Hors référentiel'";
        try (PreparedStatement ps = connexion.prepareStatement(sql)) {
            for (String code : codes) {
                ps.setString(1, code);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /// Fusion **conservatrice** du référentiel officiel VigieChiro (#717, axe 2) dans la table `taxon`,
    /// depuis une table `code -> nom latin` (issue de `GET /taxons/liste`). Pour chaque entrée :
    /// - **taxon absent** : inséré, rattaché au groupe catch-all « Référentiel VigieChiro » (V16) ;
    /// - **taxon présent au nom latin absent** (souche auto-créée à l'import) : le nom latin est complété.
    ///
    /// Ne réécrit **jamais** un nom latin ou vernaculaire déjà renseigné : le seed V05 reste la source de
    /// vérité pour ce qu'il possède (les noms français, notamment, absents de l'API). Transaction unique
    /// (deux lots atomiques), idempotente.
    public void fusionnerReferentielOfficiel(Map<String, String> codeVersNomLatin) {
        if (codeVersNomLatin.isEmpty()) {
            return;
        }
        try (Connection cx = source.getConnection()) {
            cx.setAutoCommit(false);
            try {
                // 1. Taxons officiels absents : insérés sous le groupe catch-all « Référentiel VigieChiro »
                // (résolu par son nom, comme les souches « Hors référentiel »). Absent = laissé intact.
                String insertion = "INSERT OR IGNORE INTO taxon (code, latin_name, vernacular_name_fr, group_id)"
                        // Le libellé du groupe est une **donnée stockée** (V16), pas un libellé d'IHM : il garde
                        // sa graphie d'origine. L'unifier exigerait une migration, pas une réécriture ici.
                        + " SELECT ?, ?, NULL, g.id FROM taxonomic_group g WHERE g.name = 'Référentiel VigieChiro'";
                try (PreparedStatement ps = cx.prepareStatement(insertion)) {
                    for (Map.Entry<String, String> entree : codeVersNomLatin.entrySet()) {
                        ps.setString(1, entree.getKey());
                        ps.setString(2, entree.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // 2. Taxons déjà présents mais sans nom latin (souches auto) : complétés depuis l'officiel.
                try (PreparedStatement ps =
                        cx.prepareStatement("UPDATE taxon SET latin_name = ? WHERE code = ? AND latin_name IS NULL")) {
                    for (Map.Entry<String, String> entree : codeVersNomLatin.entrySet()) {
                        ps.setString(1, entree.getValue());
                        ps.setString(2, entree.getKey());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                cx.commit();
            } catch (SQLException echec) {
                cx.rollback();
                throw echec;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la fusion du référentiel taxons officiel", e);
        }
    }

    @Override
    public Taxon insert(Taxon taxon) {
        executerMaj(
                "INSERT INTO taxon (code, latin_name, vernacular_name_fr, group_id) VALUES (?, ?, ?, ?)",
                taxon.code(),
                taxon.nomLatin(),
                taxon.nomVernaculaireFr(),
                taxon.idGroupe());
        return taxon;
    }

    @Override
    public void update(Taxon taxon) {
        executerMaj(
                "UPDATE taxon SET latin_name = ?, vernacular_name_fr = ?, group_id = ? WHERE code = ?",
                taxon.nomLatin(),
                taxon.nomVernaculaireFr(),
                taxon.idGroupe(),
                taxon.code());
    }
}
