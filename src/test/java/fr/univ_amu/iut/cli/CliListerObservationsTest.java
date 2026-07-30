package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `lister-observations` (#1311) : la **surface de découverte** de la revue.
///
/// Ce qu'elle protège avant tout : **les identifiants sortent**. Sans eux, `discussion --observation <id>`
/// (livrée en #1418) et tous les gestes de revue à venir sont aveugles - il fallait ouvrir la base SQLite
/// à la main pour savoir quoi leur passer.
///
/// Et le contrat qui rend les gestes par filtre sûrs : ce que cette commande **montre** est exactement ce
/// qu'un geste avec les **mêmes filtres** toucherait, parce que c'est le même code qui choisit.
class CliListerObservationsTest {

    @TempDir
    Path workspaceDir;

    private Injector injecteur;
    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    private long idNonTouchee;
    private long idValidee;
    private long idCorrigeeDouteuse;
    private long idPassage;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        semer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String sortieTexte() {
        return capture.texte();
    }

    @Test
    @DisplayName("Sans filtre : les trois observations sortent, AVEC leur identifiant, c'est tout l'objet"
            + " de la commande")
    void liste_avec_les_identifiants() {
        int code = cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(sortieTexte())
                .contains(String.valueOf(idNonTouchee))
                .contains(String.valueOf(idValidee))
                .contains(String.valueOf(idCorrigeeDouteuse))
                .contains("3 observation(s)");
    }

    @Test
    @DisplayName("--statut NON_TOUCHEE ne garde que les non revues")
    void filtre_par_statut() {
        cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--statut", "NON_TOUCHEE"},
                sortie,
                erreur);

        assertThat(sortieTexte()).contains("1 observation(s)").contains(String.valueOf(idNonTouchee));
        assertThat(sortieTexte()).doesNotContain("Nyclei");
    }

    @Test
    @DisplayName("--douteux ne garde que les douteuses ; SANS l'option, elles sont toutes là (le drapeau"
            + " absent ne veut pas dire « non douteuse »)")
    void filtre_douteux_est_ternaire() {
        cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--douteux"},
                sortie,
                erreur);

        assertThat(sortieTexte())
                .contains("1 observation(s)")
                .contains(String.valueOf(idCorrigeeDouteuse))
                .contains("douteux");

        capture.vider();
        cli.executer(new String[] {"lister-observations", "--passage", String.valueOf(idPassage)}, sortie, erreur);
        assertThat(sortieTexte())
                .as("sans --douteux, on veut LES DEUX, pas « seulement les non-douteuses »")
                .contains("3 observation(s)");
    }

    @Test
    @DisplayName("--json émet les identifiants et les trois avis, exploitables en script")
    void sortie_json() {
        int code = cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--json"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(sortieTexte())
                .contains("\"id\":")
                .contains("\"taxonTadarida\":")
                .contains("\"taxonValidateur\":")
                .contains("\"certitude\":")
                .contains("\"messages\":");
    }

    @Test
    @DisplayName("Filtre qui ne retient rien : la commande le DIT, sans faire croire au vide du passage")
    void aucun_resultat_se_dit() {
        int code = cli.executer(
                new String[] {"lister-observations", "--passage", String.valueOf(idPassage), "--taxon", "Rhihip"},
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(sortieTexte()).contains("Aucune observation ne correspond");
    }

    /// Un passage et trois observations qui couvrent les trois statuts : une non revue, une validée (le
    /// taxon de l'observateur est celui de Tadarida), une corrigée **et** douteuse.
    private void semer() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(injecteur.getInstance(SourceDeDonnees.class))
                .carre("130711")
                .point("Z41")
                .semer();
        idPassage = jeu.idPassage();

        idNonTouchee = jeu.ajouterObservation("Pipkuh");
        idValidee = jeu.ajouterObservationValidee("Nyclei");
        idCorrigeeDouteuse =
                jeu.ajouterObservation("Pipkuh", "Pippip", ModeValidation.MANUEL, true, Certitude.PROBABLE);
    }
}
