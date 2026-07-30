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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Parité CLI ↔ IHM pour le statut « Récupéré » (#2775, ADR 0014).
///
/// **Pourquoi ce test existe.** À la clôture de l'EPIC #2581, j'avais conclu que la parité CLI était
/// « acquise sans travail » — en **lisant** que les deux commandes affichent le libellé du statut. C'est
/// une déduction, pas une vérification : un statut à moitié posé se voit précisément là où personne ne
/// regarde. Ces deux cas font tourner les commandes réelles sur une base réelle.
class CliStatutRecupereTest {

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    private long idPassage;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        idPassage = JeuDeDonneesPassage.dans(injecteur.getInstance(SourceDeDonnees.class))
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.RECUPERE)
                .semerSquelette()
                .idPassage();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return capture.texte();
    }

    @Test
    @DisplayName("#2775 : `lister-passages` nomme le statut « Récupéré »")
    void lister_passages_nomme_le_statut() {
        int code = cli.executer(new String[] {"lister-passages"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(texteSortie())
                .as("un statut que la ligne de commande tait est un statut à moitié posé (ADR 0014)")
                .contains(StatutWorkflow.RECUPERE.libelle());
    }

    @Test
    @DisplayName("#2775 : `statut-passage` nomme le statut « Récupéré »")
    void statut_passage_nomme_le_statut() {
        int code =
                cli.executer(new String[] {"statut-passage", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(texteSortie()).contains(StatutWorkflow.RECUPERE.libelle());
    }
}
