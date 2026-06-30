package fr.univ_amu.iut.validation.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.Taxon;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

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
