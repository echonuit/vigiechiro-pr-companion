package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les gestes de revue en ligne de commande (#1311) : `valider-observations`, `corriger-observations`.
///
/// Ce que ces tests protègent, dans l'ordre d'importance :
///
/// 1. **Le refus.** Viser un passage **sans filtre**, c'est viser la nuit entière. Sans `--confirmer`, la
///    commande **refuse** - c'est le test qui compte le plus, parce qu'un filtre oublié dans un script ne
///    se distingue de rien.
/// 2. **L'effet réel** : la base a changé, et pas « un mock a été appelé ».
/// 3. **Le même choix que la liste** : les filtres d'un geste sont ceux de `lister-observations`.
class CliGestesRevueTest {

    @TempDir
    Path workspaceDir;

    private Injector injecteur;
    private Cli cli;
    private SourceDeDonnees source;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    private long idPassage;
    private long idPipkuhA;
    private long idPipkuhB;
    private long idNyclei;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        source = injecteur.getInstance(SourceDeDonnees.class);
        semer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("LE REFUS : --passage sans aucun filtre viserait la nuit ENTIÈRE, la commande refuse sans"
            + " --confirmer, et rien n'est touché")
    void passage_entier_sans_confirmer_est_refuse() {
        int code = cli.executer(
                new String[] {"valider-observations", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code).isNotZero();
        assertThat(capture.texteErreur()).contains("TOUTES les observations").contains("--confirmer");
        assertThat(taxonObservateur(idPipkuhA))
                .as("rien ne doit avoir bougé : un refus n'est pas une exécution partielle")
                .isNull();
    }

    @Test
    @DisplayName("Avec --confirmer, le passage entier est bien validé (l'utilisateur l'a dit)")
    void passage_entier_avec_confirmer() {
        int code = cli.executer(
                new String[] {"valider-observations", "--passage", String.valueOf(idPassage), "--confirmer"},
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(capture.texte()).contains("passage ENTIER");
        assertThat(taxonObservateur(idPipkuhA)).isEqualTo("Pipkuh");
        assertThat(taxonObservateur(idNyclei)).isEqualTo("Nyclei");
    }

    @Test
    @DisplayName("Par identifiants : seules les lignes désignées bougent")
    void valider_par_identifiants() {
        int code = cli.executer(
                new String[] {"valider-observations", "--observation", idPipkuhA + "," + idPipkuhB}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(taxonObservateur(idPipkuhA)).isEqualTo("Pipkuh");
        assertThat(taxonObservateur(idPipkuhB)).isEqualTo("Pipkuh");
        assertThat(taxonObservateur(idNyclei))
                .as("la ligne non désignée n'a pas été touchée")
                .isNull();
    }

    @Test
    @DisplayName("Par filtre : corriger-observations touche EXACTEMENT ce que lister-observations montrait")
    void corriger_par_filtre_touche_ce_que_la_liste_montrait() {
        // Le filtre --taxon-tadarida Pipkuh désigne les deux Pipkuh, pas le Nyclei : c'est ce que
        // `lister-observations --passage N --taxon Pipkuh` afficherait, et c'est le même code qui choisit.
        int code = cli.executer(
                new String[] {
                    "corriger-observations",
                    "--taxon",
                    "Pippip",
                    "--passage",
                    String.valueOf(idPassage),
                    "--taxon-tadarida",
                    "Pipkuh"
                },
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(taxonObservateur(idPipkuhA)).isEqualTo("Pippip");
        assertThat(taxonObservateur(idPipkuhB)).isEqualTo("Pippip");
        assertThat(taxonObservateur(idNyclei)).isNull();
    }

    @Test
    @DisplayName("Un taxon inconnu du référentiel arrête TOUT avant la moindre écriture")
    void taxon_inconnu_n_ecrit_rien() {
        int code = cli.executer(
                new String[] {"corriger-observations", "--taxon", "Zzzzzz", "--observation", String.valueOf(idPipkuhA)},
                sortie,
                erreur);

        assertThat(code).isNotZero();
        assertThat(taxonObservateur(idPipkuhA))
                .as("une correction en masse vers un taxon inexistant serait un dégât silencieux")
                .isNull();
    }

    @Test
    @DisplayName("Un filtre qui ne retient rien LÈVE, au lieu de répondre « 0 observation traitée »")
    void filtre_vide_leve() {
        int code = cli.executer(
                new String[] {
                    "valider-observations", "--passage", String.valueOf(idPassage), "--taxon-tadarida", "Rhihip"
                },
                sortie,
                erreur);

        assertThat(code).isNotZero();
        assertThat(capture.texteErreur()).contains("Aucune observation");
    }

    private String taxonObservateur(long idObservation) {
        return new ObservationDao(source).findById(idObservation).orElseThrow().taxonObservateur();
    }

    /// Trois observations non revues : deux que Tadarida croit `Pipkuh`, une `Nyclei`.
    ///
    /// Les taxons viennent du **vrai référentiel** (migration `V02__seed_taxons.sql`) : c'est ce qui rend le
    /// cas « taxon inconnu » significatif - `Zzzzzz` n'y est vraiment pas.
    private void semer() {
        JeuDeDonneesPassage jeu =
                JeuDeDonneesPassage.dans(source).carre("130711").point("Z41").semer();
        idPassage = jeu.idPassage();

        idPipkuhA = jeu.ajouterObservation("Pipkuh");
        idPipkuhB = jeu.ajouterObservation("Pipkuh");
        idNyclei = jeu.ajouterObservation("Nyclei");
    }
}
