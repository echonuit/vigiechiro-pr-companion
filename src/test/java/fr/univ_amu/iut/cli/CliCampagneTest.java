package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de `creer-campagne` / `lister-campagnes` (#2355) sur l'injecteur
/// applicatif complet. Les campagnes ne sont pas rattachées à l'utilisateur (mono-poste) : pas de seed
/// d'identité nécessaire.
class CliCampagneTest {

    @TempDir
    Path workspace;

    private Cli cli;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Exécute une commande avec une capture neuve et renvoie sa sortie standard (élaguée).
    private String executerSortie(String... args) {
        SortieCapturee capture = new SortieCapturee();
        int code = cli.executer(args, capture.sortie(), capture.erreur());
        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        return capture.texte().strip();
    }

    @Test
    @DisplayName("creer-campagne puis lister-campagnes : la campagne apparaît")
    void creer_puis_lister() {
        String creation = executerSortie("creer-campagne", "--nom", "Suivi ENS", "--annee", "2026");
        assertThat(creation).contains("Campagne créée").contains("Suivi ENS");

        String liste = executerSortie("lister-campagnes");
        assertThat(liste).contains("Suivi ENS").contains("2026");
    }

    @Test
    @DisplayName("lister-campagnes --json expose des champs stables")
    void lister_json() {
        executerSortie("creer-campagne", "--nom", "Campagne JSON", "--annee", "2025");

        String json = executerSortie("lister-campagnes", "--json");
        assertThat(json).contains("\"nom\"").contains("Campagne JSON").contains("2025");
    }

    @Test
    @DisplayName("lister-campagnes sur une base vide : message explicite")
    void lister_vide() {
        assertThat(executerSortie("lister-campagnes")).contains("Aucune campagne");
    }

    /// Identifiant de la campagne créée : la sortie l'annonce sous la forme « Campagne créée : #12 … ».
    private static String idDepuis(String sortieCreation) {
        return sortieCreation.replaceAll("(?s).*#(\\d+).*", "$1");
    }

    @Test
    @DisplayName("#2355 : modifier-campagne corrige le nom et l'année, lister-campagnes le reflète")
    void modifier_campagne() {
        String id = idDepuis(executerSortie("creer-campagne", "--nom", "Nom fautif", "--annee", "2025"));

        String modification =
                executerSortie("modifier-campagne", "--campagne", id, "--nom", "Suivi ENS", "--annee", "2026");

        assertThat(modification)
                .contains("Campagne modifiée")
                .contains("Suivi ENS")
                .contains("2026");
        assertThat(executerSortie("lister-campagnes")).contains("Suivi ENS").doesNotContain("Nom fautif");
    }

    @Test
    @DisplayName("#2355 : supprimer-campagne retire le regroupement et le dit sans ambiguïté")
    void supprimer_campagne() {
        String id = idDepuis(executerSortie("creer-campagne", "--nom", "À supprimer", "--annee", "2026"));

        String suppression = executerSortie("supprimer-campagne", "--campagne", id);

        assertThat(suppression)
                .as("la sortie doit lever le doute : on supprime un regroupement, pas des nuits")
                .contains("supprimée")
                .contains("détachés");
        assertThat(executerSortie("lister-campagnes")).contains("Aucune campagne");
    }
}
