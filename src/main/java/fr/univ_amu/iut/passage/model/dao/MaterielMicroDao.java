package fr.univ_amu.iut.passage.model.dao;

import fr.univ_amu.iut.commun.persistence.DaoGenerique;
import fr.univ_amu.iut.commun.persistence.RowMapper;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.PositionMicro;

/// DAO du [MaterielMicro] d'un passage (table 1:1 `passage_equipment`, clé naturelle = `passage_id`).
///
/// Même patron d'**upsert** que [EnregistreurDao] : [#insert(MaterielMicro)] fait
/// `INSERT … ON CONFLICT(passage_id) DO UPDATE`, car il y a **au plus une** ligne de matériel par
/// passage. Un `mic_height_m` (REAL) nul est lu via `getObject` — et non `getDouble` (qui renverrait
/// `0.0`) —, et une position inconnue est tolérée par [PositionMicro#depuisTexte].
public class MaterielMicroDao extends DaoGenerique<MaterielMicro, Long> {

    private static final RowMapper<MaterielMicro> MAPPER = rs -> new MaterielMicro(
            rs.getLong("passage_id"),
            PositionMicro.depuisTexte(rs.getString("mic_position")),
            (Double) rs.getObject("mic_height_m"),
            rs.getString("mic_type"));

    public MaterielMicroDao(SourceDeDonnees source) {
        super(source);
    }

    @Override
    protected String table() {
        return "passage_equipment";
    }

    @Override
    protected String colonneCle() {
        return "passage_id";
    }

    @Override
    protected RowMapper<MaterielMicro> mapper() {
        return MAPPER;
    }

    /// Matériel du passage `idPassage`, ou un [MaterielMicro#vide] si aucune ligne n'existe (jamais
    /// `Optional.empty()` remonté à l'appelant : un passage sans matériel saisi est un cas normal).
    public MaterielMicro pour(long idPassage) {
        return findById(idPassage).orElseGet(() -> MaterielMicro.vide(idPassage));
    }

    /// Insère **ou met à jour** le matériel (upsert sur la clé naturelle `passage_id`). Renvoie
    /// l'entité telle quelle (aucune clé générée).
    @Override
    public MaterielMicro insert(MaterielMicro materiel) {
        executerMaj(
                "INSERT INTO passage_equipment (passage_id, mic_position, mic_height_m, mic_type)"
                        + " VALUES (?, ?, ?, ?) ON CONFLICT(passage_id) DO UPDATE SET"
                        + " mic_position = excluded.mic_position, mic_height_m = excluded.mic_height_m,"
                        + " mic_type = excluded.mic_type",
                materiel.idPassage(),
                nomPosition(materiel.positionMicro()),
                materiel.hauteurMetres(),
                materiel.typeMicro());
        return materiel;
    }

    @Override
    public void update(MaterielMicro materiel) {
        executerMaj(
                "UPDATE passage_equipment SET mic_position = ?, mic_height_m = ?, mic_type = ?"
                        + " WHERE passage_id = ?",
                nomPosition(materiel.positionMicro()),
                materiel.hauteurMetres(),
                materiel.typeMicro(),
                materiel.idPassage());
    }

    /// Enregistre le matériel du passage, ou **supprime** la ligne si le relevé est vide (pour ne pas
    /// conserver un enregistrement entièrement `null`). Point d'entrée à privilégier depuis le service.
    public void definir(MaterielMicro materiel) {
        if (materiel.estVide()) {
            delete(materiel.idPassage());
        } else {
            insert(materiel);
        }
    }

    private static String nomPosition(PositionMicro position) {
        return position == null ? null : position.name();
    }
}
