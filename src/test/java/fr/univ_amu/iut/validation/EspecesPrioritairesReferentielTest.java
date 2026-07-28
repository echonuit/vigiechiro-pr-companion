package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fou du **référentiel des espèces à enjeu** (#2353) : la migration V36 marque les espèces dites
/// *prioritaires* du Plan National d'Actions Chiroptères 2016-2025, et ce test vérifie qu'elle marque
/// **exactement** ce que le plan désigne — ni plus, ni moins.
///
/// Une donnée de référence embarquée se périme en silence : le plan courant s'achève en 2025, et la
/// prochaine liste ne sera pas la même. Ces assertions sont là pour qu'une divergence se voie au build
/// plutôt qu'à l'écran.
class EspecesPrioritairesReferentielTest {

    /// Les 19 espèces prioritaires du PNA 2016-2025, par leur **nom latin** (le pivot entre référentiels).
    /// Relevées sur <https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/> (espèces
    /// surlignées en orange) et recoupées sur une seconde source le 2026-07-28.
    private static final List<String> PRIORITAIRES_PNA = List.of(
            "Rhinolophus hipposideros",
            "Rhinolophus ferrumequinum",
            "Rhinolophus euryale",
            "Rhinolophus mehelyi",
            "Miniopterus schreibersii",
            "Myotis dasycneme",
            "Myotis punicus",
            "Myotis capaccinii",
            "Myotis blythii",
            "Myotis escalerai",
            "Myotis bechsteinii",
            "Nyctalus lasiopterus",
            "Nyctalus noctula",
            "Nyctalus leisleri",
            "Pipistrellus pipistrellus",
            "Pipistrellus nathusii",
            "Eptesicus serotinus",
            "Eptesicus nilssonii",
            "Plecotus macrobullaris");

    /// Les deux espèces prioritaires que le référentiel Tadarida embarqué (V05) ne porte **pas**, et que
    /// la migration ne peut donc pas marquer.
    private static final List<String> HORS_REFERENTIEL_TADARIDA = List.of("Rhinolophus mehelyi", "Myotis escalerai");

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private TaxonDao dao;

    @BeforeEach
    void migrer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        dao = new TaxonDao(source);
    }

    @Test
    @DisplayName("Le marquage couvre toutes les espèces prioritaires que le référentiel Tadarida connaît")
    void marque_toutes_les_prioritaires_connues() {
        List<String> attendues = new ArrayList<>(PRIORITAIRES_PNA);
        attendues.removeAll(HORS_REFERENTIEL_TADARIDA);

        assertThat(nomsLatinsMarques())
                .as("espèces du plan national non marquées par V36")
                .containsExactlyInAnyOrderElementsOf(attendues);
    }

    @Test
    @DisplayName("Le marquage n'invente rien : aucune espèce marquée hors de la liste du plan")
    void ne_marque_que_des_prioritaires() {
        // L'assertion réciproque de la précédente. Les deux ensemble disent « exactement », là où chacune
        // seule laisserait passer un débordement ou un oubli.
        assertThat(PRIORITAIRES_PNA)
                .as("espèce marquée par V36 alors que le plan ne la désigne pas")
                .containsAll(nomsLatinsMarques());
    }

    @Test
    @DisplayName("Le compte est de 17, et ce compte est un fait constaté, pas un objectif")
    void marque_dix_sept_taxons() {
        // 19 espèces au plan, 2 absentes du référentiel Tadarida. Si ce nombre bouge, c'est que l'une des
        // deux listes a bougé — et il faut alors relire l'ADR 2353 avant de corriger le chiffre.
        assertThat(dao.codesPrioritaires()).hasSize(17);
    }

    @Test
    @DisplayName("Chaque code marqué désigne un chiroptère réellement présent au référentiel")
    void chaque_code_marque_existe() {
        Set<String> marques = dao.codesPrioritaires();
        assertThat(marques).isNotEmpty();
        assertThat(codesDuGroupe("Chiroptères"))
                .as("code marqué absent du référentiel, ou rattaché à un autre groupe")
                .containsAll(marques);
    }

    @Test
    @DisplayName(
            "Les deux espèces hors référentiel Tadarida le sont toujours : le jour où elles y entrent, ce test le dit")
    void les_deux_especes_absentes_le_sont_encore() {
        // Ce test n'a pas vocation à rester rouge : il a vocation à PARLER. Si le référentiel Tadarida
        // gagne un jour l'une de ces deux espèces, la migration la marquera d'elle-même (le SELECT ne
        // change pas) et ce test rappellera de mettre à jour le compte attendu et l'ADR.
        assertThat(nomsLatinsDuGroupe("Chiroptères"))
                .as("une espèce jusqu'ici absente est entrée au référentiel : revoir le compte de V36")
                .doesNotContainAnyElementsOf(HORS_REFERENTIEL_TADARIDA);
    }

    private List<String> nomsLatinsMarques() {
        return colonne("SELECT t.latin_name AS valeur FROM taxon_prioritaire p JOIN taxon t ON t.code = p.taxon_code");
    }

    private List<String> codesDuGroupe(String groupe) {
        return colonne("SELECT t.code AS valeur FROM taxon t"
                + " JOIN taxonomic_group g ON g.id = t.group_id WHERE g.name = '" + groupe + "'");
    }

    private List<String> nomsLatinsDuGroupe(String groupe) {
        return colonne("SELECT t.latin_name AS valeur FROM taxon t"
                + " JOIN taxonomic_group g ON g.id = t.group_id WHERE g.name = '" + groupe + "'");
    }

    private List<String> colonne(String sql) {
        List<String> valeurs = new ArrayList<>();
        try (Connection connexion = source.getConnection();
                var requete = connexion.prepareStatement(sql);
                ResultSet lignes = requete.executeQuery()) {
            while (lignes.next()) {
                valeurs.add(lignes.getString("valeur"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture du référentiel impossible : " + sql, e);
        }
        return valeurs;
    }
}
