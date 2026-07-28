package fr.univ_amu.iut.importation.model.dao;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Reconnaît une nuit **déjà récupérée de Vigie-Chiro** : rapatriée par la synchro avec ses observations
/// et son rattachement, mais **sans son audio** (#2580).
///
/// C'est l'état que produit la synchro depuis #2557, et il change ce qu'il faut faire d'une carte SD qui
/// rapporte la même nuit : la réactiver, pas la réimporter. Réimporter en ferait deux moitiés - une avec
/// les observations et le lien, l'autre avec le son.
///
/// Cette reconnaissance vit à part plutôt que dans [AgregatImportDao] : celui-ci porte déjà l'unicité R5,
/// l'écrasement et les doublons de nuit, et il est au plafond de cohésion que le portail qualité surveille.
/// Une lecture nouvelle, avec sa propre question, mérite son propre objet.
public class NuitRecupereeDao {

    private final SourceDeDonnees source;

    @Inject
    public NuitRecupereeDao(SourceDeDonnees source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// Le passage **déjà récupéré** de cette nuit `(enregistreur, date)`, s'il y en a un.
    ///
    /// Rien de « déclaré » ici : l'état est **observé**, comme le veut l'ADR 0048. Une nuit est récupérée
    /// quand elle réunit deux faits : elle est **rattachée à une participation** (donc elle vient de la
    /// plateforme, elle n'a pas été importée localement) et **aucun de ses originaux ne porte de
    /// fréquence d'échantillonnage** (donc aucun WAV n'a jamais été posé - ce sont des emplacements
    /// vides, pas des fichiers). Une nuit importée puis déposée réunit le premier fait, jamais le second.
    ///
    /// Vide si l'identité est incomplète : sans enregistreur ni date, il n'y a pas de nuit à reconnaître.
    public Optional<Long> nuitRecuperee(String idEnregistreur, String dateNuit) {
        return passagesRattachesSansAudio(idEnregistreur, dateNuit).stream().findFirst();
    }

    private List<Long> passagesRattachesSansAudio(String idEnregistreur, String dateNuit) {
        if (idEnregistreur == null || dateNuit == null) {
            return List.of();
        }
        String sql = "SELECT p.id FROM passage p"
                + " JOIN vigiechiro_link vl ON vl.entite = 'passage' AND vl.ref_locale = CAST(p.id AS TEXT)"
                + " WHERE p.recorder_id = ? AND p.recording_date = ?"
                + "   AND NOT EXISTS ("
                + "     SELECT 1 FROM recording_session rs"
                + "     JOIN original_recording orig ON orig.session_id = rs.id"
                + "     WHERE rs.passage_id = p.id AND orig.sample_rate_hz IS NOT NULL)"
                + " ORDER BY p.id";
        List<Long> identifiants = new ArrayList<>();
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            ps.setString(1, idEnregistreur);
            ps.setString(2, dateNuit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    identifiants.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la détection d'une nuit récupérée : " + sql, e);
        }
        return identifiants;
    }
}
