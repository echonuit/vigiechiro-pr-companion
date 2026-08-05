package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les filtres de `lister-passages` (#3269), portés depuis l'écran « Carte & passages ».
///
/// La commande listait **tout** et ne savait rien restreindre, quand l'écran offre sept critères. La
/// question qu'il permet de poser en trois clics - « les passages de ce carré, vérifiés, de cette
/// année » - n'avait aucune réponse scriptable.
class CliListerPassagesFiltresTest {

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();

        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        // `semerSquelette()` est le TERMINAL : tout ce qui décrit le passage se pose avant lui. Posées
        // après, les valeurs ne touchent qu'un objet déjà inséré, et la fixture sème ses défauts.
        JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .nuit(1, 2026, "2026-06-08")
                .statut(StatutWorkflow.DEPOSE)
                .semerSquelette();
        JeuDeDonneesPassage.dans(source)
                .carre("710255")
                .point("B2")
                .nuit(2, 2025, "2025-07-19")
                .statut(StatutWorkflow.IMPORTE)
                .semerSquelette();
    }

    private String texteSortie() {
        return capture.texte();
    }

    @Test
    @DisplayName("#3269 : --carre ne garde que les passages de ce carré")
    void filtre_par_carre() {
        int code = cli.executer(new String[] {"lister-passages", "--carre", "640380"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("640380");
        assertThat(texteSortie())
                .as("sans exclusion vérifiée, ce test passerait même si --carre ne filtrait rien")
                .doesNotContain("710255");
    }

    @Test
    @DisplayName("#3269 : --annee et --statut se cumulent, comme les puces de l'écran")
    void filtres_se_cumulent() {
        int code = cli.executer(
                new String[] {"lister-passages", "--annee", "2025", "--statut", "IMPORTE"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("710255").doesNotContain("640380");
    }

    @Test
    @DisplayName("#3269 : un filtre qui ne retient rien le dit, plutôt que de paraître vide")
    void filtre_sans_resultat_le_dit() {
        // Sans cette phrase, la commande afficherait « Aucun passage enregistré » sur une base qui en
        // porte deux : le filtre ferait passer une base peuplée pour une base vide.
        int code = cli.executer(new String[] {"lister-passages", "--carre", "999999"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .contains("Aucun passage ne correspond aux filtres")
                .doesNotContain("Aucun passage enregistré");
    }

    @Test
    @DisplayName("#3269 : les filtres valent aussi pour --json, que les scripts consomment")
    void filtres_valent_pour_json() {
        int code = cli.executer(new String[] {"lister-passages", "--carre", "640380", "--json"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("640380").doesNotContain("710255");
    }

    @Test
    @DisplayName("#3269 : --lieu retient par le n° de carré comme par le point qualifié")
    void filtre_par_lieu() {
        int code = cli.executer(new String[] {"lister-passages", "--lieu", "710255"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("710255").doesNotContain("640380");

        capture.vider();
        code = cli.executer(new String[] {"lister-passages", "--lieu", "640380 · A1"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie())
                .as("un point se désigne qualifié par son carré : « A1 » seul en désignerait autant"
                        + " qu'il y a de carrés (#2992)")
                .contains("640380")
                .doesNotContain("710255");
    }

    @Test
    @DisplayName("#3269 : --analyse retient l'état déduit, sans que la règle soit réécrite")
    void filtre_par_etat_analyse() {
        // L'état d'analyse se DÉDUIT du statut, il ne se lit pas : « Déposé » est sur la plateforme et
        // n'a aucun relevé, donc JAMAIS_RELEVE ; « Importé » n'y est pas, donc SANS_OBJET. Les deux
        // passages semés portent ainsi deux états distincts, et chaque valeur discrimine réellement.
        // La règle vit dans `EtatAnalyse.deduire`, la même que celle de l'écran.
        int code = cli.executer(new String[] {"lister-passages", "--analyse", "JAMAIS_RELEVE"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("640380").doesNotContain("710255");

        capture.vider();
        code = cli.executer(new String[] {"lister-passages", "--analyse", "SANS_OBJET"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("710255").doesNotContain("640380");
    }

    @Test
    @DisplayName("#3269 : --campagne ne retient rien quand aucune nuit n'est rattachée")
    void filtre_par_campagne_sans_rattachement() {
        // Une nuit non rattachée n'est JAMAIS retenue par une campagne : c'est la règle de
        // `FiltresMultisite`, pas une invention de la commande.
        int code = cli.executer(new String[] {"lister-passages", "--campagne", "ENS"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(texteSortie()).contains("Aucun passage ne correspond aux filtres");
    }

    @Test
    @DisplayName("#3269 : une valeur hors énumération est refusée, pas ignorée en silence")
    void statut_inconnu_refuse() {
        int code = cli.executer(new String[] {"lister-passages", "--statut", "PAS_UN_STATUT"}, sortie, erreur);

        assertThat(code)
                .as("picocli doit refuser l'invocation plutôt que de filtrer sur rien")
                .isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
    }
}
