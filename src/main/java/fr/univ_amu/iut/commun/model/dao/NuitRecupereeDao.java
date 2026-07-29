package fr.univ_amu.iut.commun.model.dao;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/// Reconnaît une nuit **déjà récupérée de Vigie-Chiro** : rapatriée par la synchro avec ses observations
/// et son rattachement, mais **sans son audio** (#2580, #2581).
///
/// C'est l'état que produit la synchro depuis #2557, et il change deux choses. Une carte SD qui rapporte
/// la même nuit doit la **réactiver**, pas la réimporter (#2580). Et la fiche d'une telle nuit ne doit pas
/// se refermer sur les gardes de « Déposé », qui protègent une nuit **que nous avons déposée** (#2581).
///
/// ## Le critère
///
/// Rien de « déclaré » ici : l'état est **observé**, comme le veut l'ADR 0048. Une nuit est récupérée
/// quand elle réunit deux faits :
///
///  - elle est **rattachée à une participation** - donc elle vient de la plateforme, elle n'a pas été
///    importée ici ;
///  - **aucun de ses originaux ne porte de fréquence d'échantillonnage** - donc aucun WAV n'a jamais été
///    posé : ce sont des emplacements vides, pas des fichiers.
///
/// Une nuit importée puis déposée réunit le premier fait, jamais le second. C'est ce qui fait tenir la
/// distinction sans colonne supplémentaire.
///
/// ## Pourquoi dans le socle
///
/// **Deux features posent la même question sous deux angles.** L'import demande « la nuit que je
/// m'apprête à importer est-elle déjà là ? », et la connaît par son identité `(enregistreur, date)`. La
/// fiche du passage demande « celle que j'affiche vient-elle de la plateforme sans que rien n'y ait été
/// fait ici ? », et la connaît par son identifiant. Même critère, deux clés d'entrée : le loger dans
/// l'une des deux features ferait dépendre l'autre d'elle sans raison.
public class NuitRecupereeDao {

    /// Le critère, une seule fois. Les deux questions n'en changent que la clé d'entrée - les garder sur
    /// une base commune est ce qui garantit qu'elles répondront toujours la même chose.
    private static final String RATTACHEE_SANS_AUDIO = "SELECT p.id FROM passage p"
            + " JOIN vigiechiro_link vl ON vl.entite = 'passage' AND vl.ref_locale = CAST(p.id AS TEXT)"
            + " WHERE NOT EXISTS ("
            + "   SELECT 1 FROM recording_session rs"
            + "   JOIN original_recording orig ON orig.session_id = rs.id"
            + "   WHERE rs.passage_id = p.id AND orig.sample_rate_hz IS NOT NULL)";

    private final SourceDeDonnees source;

    @Inject
    public NuitRecupereeDao(SourceDeDonnees source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// Le passage **déjà récupéré** de cette nuit `(enregistreur, date)`, s'il y en a un.
    ///
    /// Vide si l'identité est incomplète : sans enregistreur ni date, il n'y a pas de nuit à reconnaître.
    public Optional<Long> nuitRecuperee(String idEnregistreur, String dateNuit) {
        if (idEnregistreur == null || dateNuit == null) {
            return Optional.empty();
        }
        return premier(RATTACHEE_SANS_AUDIO + " AND p.recorder_id = ? AND p.recording_date = ? ORDER BY p.id", ps -> {
            ps.setString(1, idEnregistreur);
            ps.setString(2, dateNuit);
        });
    }

    /// Ce passage précis est-il une nuit **récupérée**, au sens du critère observé ?
    ///
    /// ⚠️ **Ce n'est plus une garde de production.** Depuis #2772 l'état est porté par le passage
    /// (`StatutWorkflow.RECUPERE`), et les gardes le lisent là - entretenir deux chemins vers la même
    /// vérité, c'est se donner deux réponses possibles.
    ///
    /// Cette méthode reste parce qu'elle est devenue **l'oracle de la migration V37** : le critère existe
    /// aussi dans son `WHERE`, et `MigrationV37StatutRecupereTest` compare les deux verdicts nuit par
    /// nuit. La supprimer supprimerait la garantie que l'ADR 2581 déclare - il n'y aurait plus rien à
    /// quoi comparer la migration, et sa divergence future passerait inaperçue.
    ///
    /// Faux si `idPassage` est nul : il n'y a pas de passage à reconnaître.
    public boolean estRecuperee(Long idPassage) {
        if (idPassage == null) {
            return false;
        }
        return premier(RATTACHEE_SANS_AUDIO + " AND p.id = ?", ps -> ps.setLong(1, idPassage))
                .isPresent();
    }

    /// Pose les paramètres d'une requête ; existe pour que les deux questions partagent leur exécution
    /// sans partager leurs clés.
    @FunctionalInterface
    private interface Parametres {
        void poser(PreparedStatement ps) throws SQLException;
    }

    private Optional<Long> premier(String sql, Parametres parametres) {
        try (Connection cx = source.getConnection();
                PreparedStatement ps = cx.prepareStatement(sql)) {
            parametres.poser(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("id")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec de la détection d'une nuit récupérée : " + sql, e);
        }
    }
}
