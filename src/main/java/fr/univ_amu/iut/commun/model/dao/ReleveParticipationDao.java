package fr.univ_amu.iut.commun.model.dao;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.model.ReleveParticipation;
import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/// Le relevé de ce que la plateforme portait pour une participation, à notre dernière lecture
/// (#4706, [ReleveParticipation]).
///
/// Un relevé par passage, **écrasé** à chaque lecture : on retient l'état courant vu, pas son passé.
/// La configuration voyage en JSON dans une colonne, comme un dictionnaire libre dont les clés sont
/// celles du serveur et non les nôtres.
public class ReleveParticipationDao extends DaoGenerique<ReleveParticipation, Long> {

    private static final Gson GSON = new Gson();
    private static final Type DICTIONNAIRE = new TypeToken<Map<String, String>>() {}.getType();

    private static final RowMapper<ReleveParticipation> MAPPER = rs -> new ReleveParticipation(
            rs.getLong("passage_id"),
            rs.getString("participation_id"),
            rs.getString("date_debut"),
            rs.getString("date_fin"),
            lireMeteo(rs),
            lireConfiguration(rs),
            rs.getString("releve_le"));

    public ReleveParticipationDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "participation_relevee";
    }

    @Override
    protected String colonneCle() {
        return "passage_id";
    }

    @Override
    protected RowMapper<ReleveParticipation> mapper() {
        return MAPPER;
    }

    /// Le relevé du passage, ou vide s'il n'a jamais été lu : sans base, la question du conflit
    /// reste sans réponse, et c'est un cas à traiter et non un défaut.
    public Optional<ReleveParticipation> pour(Long idPassage) {
        return findById(idPassage);
    }

    /// Écrit le relevé, en écrasant le précédent du même passage.
    public void enregistrer(ReleveParticipation releve) {
        MeteoDepot meteo = releve.meteo();
        executerMaj(
                "INSERT INTO participation_relevee (passage_id, participation_id, date_debut, date_fin,"
                        + " meteo_vent, meteo_couverture, meteo_temperature_debut, meteo_temperature_fin,"
                        + " configuration, releve_le)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(passage_id) DO UPDATE SET"
                        + " participation_id = excluded.participation_id, date_debut = excluded.date_debut,"
                        + " date_fin = excluded.date_fin, meteo_vent = excluded.meteo_vent,"
                        + " meteo_couverture = excluded.meteo_couverture,"
                        + " meteo_temperature_debut = excluded.meteo_temperature_debut,"
                        + " meteo_temperature_fin = excluded.meteo_temperature_fin,"
                        + " configuration = excluded.configuration, releve_le = excluded.releve_le",
                releve.passageId(),
                releve.participationId(),
                releve.dateDebut(),
                releve.dateFin(),
                meteo == null ? null : meteo.vent(),
                meteo == null ? null : meteo.couverture(),
                meteo == null ? null : meteo.temperatureDebut(),
                meteo == null ? null : meteo.temperatureFin(),
                GSON.toJson(releve.configuration() == null ? Map.of() : releve.configuration()),
                releve.releveLe());
    }

    @Override
    public ReleveParticipation insert(ReleveParticipation releve) {
        enregistrer(releve);
        return releve;
    }

    @Override
    public void update(ReleveParticipation releve) {
        enregistrer(releve);
    }

    /// Le bloc météo relu, **avec ses températures** (#4768). En omettre une partie ne rendrait pas la
    /// comparaison moins précise : elle la rendrait toujours vraie, donc la garde toujours bloquante.
    private static MeteoDepot lireMeteo(ResultSet rs) throws SQLException {
        String vent = rs.getString("meteo_vent");
        String couverture = rs.getString("meteo_couverture");
        Integer debut = lireEntier(rs, "meteo_temperature_debut");
        Integer fin = lireEntier(rs, "meteo_temperature_fin");
        return vent == null && couverture == null && debut == null && fin == null
                ? null
                : new MeteoDepot(vent, couverture, debut, fin);
    }

    /// Un entier de colonne, ou `null` : `getInt` rend `0` sur un `NULL`, ce qui ferait d'une
    /// température absente une température de zéro degré.
    private static Integer lireEntier(ResultSet rs, String colonne) throws SQLException {
        int valeur = rs.getInt(colonne);
        return rs.wasNull() ? null : valeur;
    }

    private static Map<String, String> lireConfiguration(ResultSet rs) throws SQLException {
        String json = rs.getString("configuration");
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> lu = GSON.fromJson(json, DICTIONNAIRE);
        return lu == null ? Map.of() : lu;
    }
}
