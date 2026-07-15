package fr.univ_amu.iut.commun.model.dao;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// DAO des correspondances locale ↔ VigieChiro (table `vigiechiro_link`, cf. [LienVigieChiro], #728).
///
/// Table à **clé primaire composite** (`entite`, `ref_locale`) : les lectures/écritures nominales
/// sont donc toujours **portées par l'entité** ([#objectidPour], [#tous], [#remplacer]), et non par
/// la seule clé locale. Les `findById` / `delete` hérités de [DaoGenerique] (mono-colonne) restent
/// disponibles mais ne distinguent pas l'entité : ne pas les utiliser pour ce DAO.
///
/// La colonne `verrouille` (V17, #718) n'a de sens que pour les **sites** : `1`/`0` selon l'état
/// *verrouillé* du site VigieChiro (dépôt possible), `NULL` pour les taxons.
///
/// Deux chemins d'écriture, tous deux **idempotents** :
/// - [#upsert(LienVigieChiro)] : pose (ou remplace) une correspondance unitaire (`ON CONFLICT`) ;
/// - [#remplacer(String, Collection)] : **resynchronisation** transactionnelle d'une entité entière
///   (purge puis réinsertion), qui supprime au passage les correspondances devenues obsolètes.
public class LienVigieChiroDao extends DaoGenerique<LienVigieChiro, String> {

    private static final RowMapper<LienVigieChiro> MAPPER = rs -> new LienVigieChiro(
            rs.getString("entite"), rs.getString("ref_locale"), rs.getString("objectid"), lireVerrouille(rs));

    public LienVigieChiroDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "vigiechiro_link";
    }

    @Override
    protected String colonneCle() {
        return "ref_locale";
    }

    @Override
    protected RowMapper<LienVigieChiro> mapper() {
        return MAPPER;
    }

    /// `objectid` VigieChiro correspondant à `refLocale` pour l'`entite` donnée, ou vide si aucune
    /// correspondance n'a encore été rapprochée.
    public Optional<String> objectidPour(String entite, String refLocale) {
        return queryUnique(
                        "SELECT * FROM vigiechiro_link WHERE entite = ? AND ref_locale = ?", MAPPER, entite, refLocale)
                .map(LienVigieChiro::objectid);
    }

    /// Toutes les correspondances d'une entité, sous forme de table `ref_locale -> objectid` (ordre
    /// d'insertion préservé). Utile aux features consommatrices pour résoudre en masse leurs clés.
    public Map<String, String> tous(String entite) {
        Map<String, String> liens = new LinkedHashMap<>();
        for (LienVigieChiro lien :
                query("SELECT * FROM vigiechiro_link WHERE entite = ? ORDER BY ref_locale", MAPPER, entite)) {
            liens.put(lien.refLocale(), lien.objectid());
        }
        return liens;
    }

    /// Clés locales des correspondances **verrouillées** d'une entité (`verrouille = 1`) : pour les
    /// sites (#718), les sites dont le pendant VigieChiro accepte le dépôt.
    public Set<String> verrouilles(String entite) {
        Set<String> refs = new LinkedHashSet<>();
        for (LienVigieChiro lien : query(
                "SELECT * FROM vigiechiro_link WHERE entite = ? AND verrouille = 1 ORDER BY ref_locale",
                MAPPER,
                entite)) {
            refs.add(lien.refLocale());
        }
        return refs;
    }

    /// Nombre de correspondances enregistrées pour une entité (indicateur de synchro).
    public long compter(String entite) {
        return query("SELECT * FROM vigiechiro_link WHERE entite = ?", MAPPER, entite)
                .size();
    }

    /// **Upsert** unitaire : pose la correspondance, ou remplace `objectid`/`verrouille` si
    /// `(entite, ref_locale)` existe déjà (SQLite `ON CONFLICT`). Idempotent.
    public void upsert(LienVigieChiro lien) {
        executerMaj(
                "INSERT INTO vigiechiro_link (entite, ref_locale, objectid, verrouille) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(entite, ref_locale) DO UPDATE SET "
                        + "objectid = excluded.objectid, verrouille = excluded.verrouille",
                lien.entite(),
                lien.refLocale(),
                lien.objectid(),
                enEntier(lien.verrouille()));
    }

    /// **Resynchronisation** transactionnelle d'une entité : purge toutes ses correspondances puis
    /// réinsère `liens` (objectid + verrouille), en une seule transaction (purge + lot atomiques).
    /// Reflète l'état courant de la plateforme et supprime les correspondances obsolètes. Idempotent :
    /// rejouer avec la même collection produit le même résultat.
    public void remplacer(String entite, Collection<LienVigieChiro> liens) {
        try (Connection cx = source.getConnection()) {
            cx.setAutoCommit(false);
            try {
                try (PreparedStatement purge = cx.prepareStatement("DELETE FROM vigiechiro_link WHERE entite = ?")) {
                    purge.setString(1, entite);
                    purge.executeUpdate();
                }
                try (PreparedStatement insert = cx.prepareStatement(
                        "INSERT INTO vigiechiro_link (entite, ref_locale, objectid, verrouille) VALUES (?, ?, ?, ?)")) {
                    for (LienVigieChiro lien : liens) {
                        insert.setString(1, entite);
                        insert.setString(2, lien.refLocale());
                        insert.setString(3, lien.objectid());
                        insert.setObject(4, enEntier(lien.verrouille()));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                cx.commit();
            } catch (SQLException echec) {
                cx.rollback();
                throw echec;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la resynchronisation des correspondances : " + entite, e);
        }
    }

    /// Supprime la correspondance **unitaire** `(entite, refLocale)`. À utiliser au lieu du `delete`
    /// hérité (mono-colonne, qui ne distingue pas l'entité, cf. en-tête). Idempotent : supprimer une
    /// correspondance absente ne fait rien. Sert notamment à défaire un `upsert` lors d'une compensation.
    public void supprimer(String entite, String refLocale) {
        executerMaj("DELETE FROM vigiechiro_link WHERE entite = ? AND ref_locale = ?", entite, refLocale);
    }

    @Override
    public LienVigieChiro insert(LienVigieChiro lien) {
        executerMaj(
                "INSERT INTO vigiechiro_link (entite, ref_locale, objectid, verrouille) VALUES (?, ?, ?, ?)",
                lien.entite(),
                lien.refLocale(),
                lien.objectid(),
                enEntier(lien.verrouille()));
        return lien;
    }

    @Override
    public void update(LienVigieChiro lien) {
        executerMaj(
                "UPDATE vigiechiro_link SET objectid = ?, verrouille = ? WHERE entite = ? AND ref_locale = ?",
                lien.objectid(),
                enEntier(lien.verrouille()),
                lien.entite(),
                lien.refLocale());
    }

    /// Convertit le drapeau `verrouille` (nullable) en entier SQLite (`1`/`0`/`NULL`).
    private static Integer enEntier(Boolean verrouille) {
        return verrouille == null ? null : (verrouille ? 1 : 0);
    }

    /// Lit la colonne `verrouille` nullable : `NULL` → `null`, sinon `true`/`false`.
    private static Boolean lireVerrouille(ResultSet rs) throws SQLException {
        int valeur = rs.getInt("verrouille");
        return rs.wasNull() ? null : valeur != 0;
    }
}
