package fr.univ_amu.iut.commun.model.dao;

import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.util.List;

/// DAO de [VueSauvegardee] (table `saved_filter_view`, migration V11) : CRUD des **vues mémorisées
/// génériques** (#623). Généralise le `SavedViewDao` du multisite à toutes les vues tabulaires ; le
/// descripteur de filtres est conservé **tel quel** dans la colonne JSON `descriptor_json`.
///
/// `findAll` / `findById` / `delete` sont hérités de [DaoGenerique] ; seuls les écritures dépendantes des
/// colonnes (`insert` / `update`) et la requête métier [#findByFeature(String)] sont écrites ici.
public class VueSauvegardeeDao extends DaoGenerique<VueSauvegardee, Long> {

    private static final RowMapper<VueSauvegardee> MAPPER = rs -> new VueSauvegardee(
            rs.getLong("id"), rs.getString("feature"), rs.getString("name"), rs.getString("descriptor_json"));

    public VueSauvegardeeDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "saved_filter_view";
    }

    @Override
    protected String colonneCle() {
        return "id";
    }

    @Override
    protected RowMapper<VueSauvegardee> mapper() {
        return MAPPER;
    }

    /// Vues mémorisées d'une **feature** (écran/table) donnée, triées par identifiant : c'est ainsi que
    /// chaque écran ne liste que ses propres vues.
    public List<VueSauvegardee> findByFeature(String feature) {
        return query("SELECT * FROM saved_filter_view WHERE feature = ? ORDER BY id", MAPPER, feature);
    }

    @Override
    public VueSauvegardee insert(VueSauvegardee vue) {
        long id = insererEtRecupererCle(
                "INSERT INTO saved_filter_view (feature, name, descriptor_json) VALUES (?, ?, ?)",
                vue.feature(),
                vue.nom(),
                vue.descripteurJson());
        return new VueSauvegardee(id, vue.feature(), vue.nom(), vue.descripteurJson());
    }

    @Override
    public void update(VueSauvegardee vue) {
        executerMaj(
                "UPDATE saved_filter_view SET feature = ?, name = ?, descriptor_json = ? WHERE id = ?",
                vue.feature(),
                vue.nom(),
                vue.descripteurJson(),
                vue.id());
    }
}
