package fr.univ_amu.iut.commun.model.dao;

import fr.univ_amu.iut.commun.model.Reglage;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.util.Optional;

/// DAO des réglages applicatifs (table `app_setting`, cf. [Reglage]).
///
/// Table transverse à **clé naturelle** (`cle`, en TEXT) : comme [UtilisateurDao], l'insertion ne
/// récupère aucune clé générée. `findAll` / `findById` / `delete` sont hérités de [DaoGenerique].
///
/// L'écriture usuelle d'un réglage est un **upsert** ([#ecrire(String, String)]) : « poser cette
/// valeur, qu'elle existe déjà ou non », sans que l'appelant ait à distinguer insertion et mise à
/// jour. Les `insert` / `update` du contrat CRUD restent disponibles mais ne sont pas le chemin
/// nominal.
public class ReglagesDao extends DaoGenerique<Reglage, String> {

    private static final RowMapper<Reglage> MAPPER = rs -> new Reglage(rs.getString("cle"), rs.getString("valeur"));

    public ReglagesDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "app_setting";
    }

    @Override
    protected String colonneCle() {
        return "cle";
    }

    @Override
    protected RowMapper<Reglage> mapper() {
        return MAPPER;
    }

    /// Valeur brute du réglage `cle`, ou [Optional#empty()] s'il n'a jamais été écrit.
    public Optional<String> lire(String cle) {
        return findById(cle).map(Reglage::valeur);
    }

    /// **Upsert** du réglage `cle` : insère la valeur, ou la remplace si la clé existe déjà (SQLite
    /// `ON CONFLICT`). Chemin d'écriture nominal, idempotent.
    public void ecrire(String cle, String valeur) {
        executerMaj(
                "INSERT INTO app_setting (cle, valeur) VALUES (?, ?) "
                        + "ON CONFLICT(cle) DO UPDATE SET valeur = excluded.valeur",
                cle,
                valeur);
    }

    @Override
    public Reglage insert(Reglage reglage) {
        executerMaj("INSERT INTO app_setting (cle, valeur) VALUES (?, ?)", reglage.cle(), reglage.valeur());
        return reglage;
    }

    @Override
    public void update(Reglage reglage) {
        executerMaj("UPDATE app_setting SET valeur = ? WHERE cle = ?", reglage.valeur(), reglage.cle());
    }
}
