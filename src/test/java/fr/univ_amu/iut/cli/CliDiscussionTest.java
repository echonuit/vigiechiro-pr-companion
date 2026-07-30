package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.validation.model.MessageObservation;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `discussion` (#1418) : la **parité CLI** du fil d'échange avec le validateur, le lire, et y répondre.
///
/// ⚠️ Aucun envoi réel n'est tiré ici : l'injecteur applicatif n'est pas connecté, et le test s'arrête
/// **avant** l'appel réseau. Ce qu'il protège, c'est le garde-fou : **rien ne part sans `--confirmer`**,
/// sur une route qui ne sait pas revenir en arrière.
class CliDiscussionTest {

    @TempDir
    Path workspaceDir;

    private Injector injecteur;
    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#1418 : --message sans --confirmer n'envoie RIEN, on n'écrit pas l'irréversible par une"
            + " option laissée traîner dans un script")
    void message_exige_confirmer() {
        int code = cli.executer(
                new String[] {"discussion", "--observation", "7", "--message", "Je doute."}, sortie, erreur);

        assertThat(code).isEqualTo(2);
        assertThat(capture.texteErreur())
                .contains("ne pourra PAS être supprimé")
                .contains("--confirmer");
    }

    @Test
    @DisplayName("#1418 : aucun message sur l'observation → la commande le DIT (lecture seule, code 0)")
    void fil_vide() {
        int code = cli.executer(new String[] {"discussion", "--observation", "7"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(capture.texte()).contains("Aucun message");
    }

    @Test
    @DisplayName("#1417 : le fil se lit en CLI, dans l'ordre du serveur, avec auteur et date")
    void fil_lisible() throws SQLException {
        long idObservation = semerUnFil();

        int code = cli.executer(
                new String[] {"discussion", "--observation", String.valueOf(idObservation)}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(capture.texte())
                .as("l'ordre du serveur ($push) est l'ordre chronologique : la CLI le rend tel quel")
                .containsSubsequence("u-validateur", "C'est un Pipnat.", "u-moi", "Je repasse le son.");
    }

    /// Une observation, et deux messages sur son fil. Le fil est un **reflet du serveur** (c'est l'import
    /// qui l'y met) : on l'écrit donc directement en base. L'observation, elle, doit exister pour de bon :
    /// la clé étrangère du schéma y veille, et c'est heureux : un fil sans détection ne veut rien dire.
    private long semerUnFil() throws SQLException {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        JeuDeDonneesPassage jeu =
                JeuDeDonneesPassage.dans(source).carre("130711").point("Z41").semer();
        long idObservation = jeu.ajouterObservation("Pipkuh");

        MessageObservationDao dao = new MessageObservationDao(source);
        new UniteDeTravail(source)
                .executer(connexion -> dao.remplacerFil(
                        connexion,
                        idObservation,
                        List.of(
                                new MessageObservation(
                                        null,
                                        idObservation,
                                        0,
                                        "u-validateur",
                                        "C'est un Pipnat.",
                                        Instant.parse("2026-07-11T21:04:00Z")),
                                new MessageObservation(null, idObservation, 1, "u-moi", "Je repasse le son.", null))));
        return idObservation;
    }
}
