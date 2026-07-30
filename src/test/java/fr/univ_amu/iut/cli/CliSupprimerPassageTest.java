package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de `supprimer-passage` (#2278).
///
/// **Ce que ces tests protègent.** La commande détruit ; sa sûreté ne tient pas à une modale mais à un
/// **drapeau**. Les chemins qui comptent sont donc les chemins **non nominaux** - refus sans drapeau,
/// passage déposé, passage introuvable - et l'invariant qui les traverse : *quand la commande refuse,
/// la base est intacte*. Un refus qui aurait déjà supprimé serait pire que pas de refus du tout.
///
/// Le semis passe par [JeuDeDonneesPassage] : semer la topologie d'une nuit à la main est une dette que
/// [fr.univ_amu.iut.fixture.CliquetFixturePassageTest] compte et fait rétrécir.
class CliSupprimerPassageTest {

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    private PassageDao passageDao;
    private long idPassage;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        passageDao = new PassageDao(injecteur.getInstance(SourceDeDonnees.class));
        idPassage = semerUneNuit(1, StatutWorkflow.IMPORTE);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return capture.texte();
    }

    @Test
    @DisplayName("Sans --confirmer : la perte est chiffrée, RIEN n'est supprimé, sortie 2")
    void refus_sans_confirmer_ne_touche_a_rien() {
        int code = cli.executer(
                new String[] {"supprimer-passage", "--passage", String.valueOf(idPassage)}, sortie, erreur);

        assertThat(code)
                .as("2 arrête un script qui enchaînerait, sans le confondre avec un échec (1)")
                .isEqualTo(2);
        assertThat(passageDao.findById(idPassage))
                .as("un refus qui aurait déjà supprimé serait pire que pas de refus du tout")
                .isPresent();
        assertThat(texteSortie())
                .as("le drapeau ne dispense pas d'informer : il déplace le moment où l'on décide")
                .contains("Suppression DÉFINITIVE")
                .contains("2 séquence(s)");
        assertThat(capture.texteErreur()).contains("--confirmer");
    }

    @Test
    @DisplayName("Un passage DÉPOSÉ est refusé par le métier (2), même avec --confirmer")
    void passage_depose_refuse_meme_confirme() {
        long idDepose = semerUneNuit(2, StatutWorkflow.DEPOSE);

        int code = cli.executer(
                new String[] {"supprimer-passage", "--passage", String.valueOf(idDepose), "--confirmer"},
                sortie,
                erreur);

        assertThat(code)
                .as("le drapeau atteste d'une intention, il ne déroge pas à la règle métier qui protège un"
                        + " passage DÉPOSÉ (refus, état intact)")
                .isEqualTo(Cli.CODE_REFUS);
        assertThat(passageDao.findById(idDepose)).isPresent();
    }

    @Test
    @DisplayName("Un passage introuvable est refusé (2) sans rien détruire")
    void passage_introuvable_echoue() {
        int code =
                cli.executer(new String[] {"supprimer-passage", "--passage", "999999", "--confirmer"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_REFUS);
        assertThat(passageDao.findAll()).as("la base reste intacte").hasSize(1);
    }

    @Test
    @DisplayName("Avec --confirmer : le passage et sa nuit disparaissent, sortie 0")
    void confirme_supprime_le_passage() {
        int code = cli.executer(
                new String[] {"supprimer-passage", "--passage", String.valueOf(idPassage), "--confirmer"},
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(passageDao.findById(idPassage)).isEmpty();
        assertThat(texteSortie()).contains("supprimé");
    }

    /// Une nuit complète (session, original, deux séquences), pour que la perte annoncée soit chiffrée sur
    /// autre chose que zéro. Le site et le point sont partagés : la fixture les retrouve ou les crée.
    private long semerUneNuit(int numero, StatutWorkflow statut) {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(injecteur.getInstance(SourceDeDonnees.class))
                .nuit(numero, 2026, "2026-06-20")
                .statut(statut)
                .semer();
        jeu.ajouterSequence();
        jeu.ajouterSequence();
        return jeu.idPassage();
    }
}
