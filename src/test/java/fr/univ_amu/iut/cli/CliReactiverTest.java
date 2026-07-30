package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Invocation de bout en bout de `reactiver` (#1302), et surtout de ce que le chantier #2554 y a fait
/// circuler : la **phase 0**, qui rapatrie les observations d'une nuit récupérée avant de rebrancher son
/// audio (#2555).
///
/// **Pourquoi ce fichier existe.** La parité CLI de `reactiver` avait été livrée par #1703, dans
/// `CliArchivageTest`. Ce fichier a été supprimé par #2280 (retrait du geste d'archivage) et a emporté
/// avec lui un test qui ne parlait pas d'archivage : depuis, **aucun test n'exerçait cette commande**.
/// Le chantier #2554 a fait passer la phase 0 dessus sans s'en apercevoir, en supposant la parité acquise
/// du fait que la CLI et l'IHM partagent le même service. C'est vrai du câblage, et cela ne se vérifie pas
/// tout seul.
///
/// Les chemins couverts sont les chemins **non nominaux**, ceux qu'un utilisateur rencontre vraiment : une
/// nuit qu'on ne peut pas hydrater faute de passerelle, et un dossier source absent. Le chemin nominal
/// complet (télécharger, régénérer, rebrancher) est exercé par
/// [fr.univ_amu.iut.e2e.ParcoursRestaurationDepuisVigieChiroE2ETest], qui dispose du vrai câblage.
///
/// Le semis passe par [JeuDeDonneesPassage] : semer la topologie d'une nuit à la main est une dette que
/// [fr.univ_amu.iut.fixture.CliquetFixturePassageTest] compte et fait rétrécir.
class CliReactiverTest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    private SourceDeDonnees source;
    private Path dossierSource;

    @BeforeEach
    void preparer() throws Exception {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        source = injecteur.getInstance(SourceDeDonnees.class);
        dossierSource = Files.createDirectories(workspace.resolve("carte-sd"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return capture.texte() + capture.texteErreur();
    }

    /// Une nuit **rapatriée par la synchro** : session archivée, aucune séquence, rattachée à sa
    /// participation. C'est l'état sur lequel la phase 0 se déclenche.
    private long semerSquelette() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .statut(StatutWorkflow.DEPOSE)
                .cheminSession(workspace.resolve("Car130711-2026-Pass1-Z41").toString())
                .semerSquelette();
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(
                        LienVigieChiro.ENTITE_PASSAGE, String.valueOf(jeu.idPassage()), PARTICIPATION));
        return jeu.idPassage();
    }

    @Test
    @DisplayName("#2555 : sur une nuit récupérée, la CLI refuse en DISANT qu'il faut se connecter")
    void squelette_sans_passerelle_refuse_en_disant_quoi_faire() {
        long idPassage = semerSquelette();

        int code = cli.executer(
                new String[] {"reactiver", "--passage", String.valueOf(idPassage), "--source", dossierSource.toString()
                },
                sortie,
                erreur);

        // L'injecteur de la CLI n'installe pas la passerelle VigieChiro hors connexion : la nuit ne peut pas
        // récupérer ses observations. Le point du test n'est pas le code de retour, c'est que le refus
        // ORIENTE - là où le comportement d'avant #2555 était de rebrancher zéro fichier en silence.
        assertThat(code).isEqualTo(Cli.CODE_REFUS);
        assertThat(texteSortie())
                .as("un refus qui ne dit pas quoi faire laisse l'utilisateur devant une nuit muette")
                .contains("Non connecté à Vigie-Chiro")
                .contains("Connectez-vous");
        // Le refus ne parle NI d'un geste qu'on n'a pas demandé, NI d'une surface qui n'existe pas ici.
        // Avant #2554 passe 2, la CLI recevait « avant de reconstruire un passage (menu ☰ > …) » : un menu
        // dans un terminal, et « reconstruire » à qui réactive.
        assertThat(texteSortie()).doesNotContain("☰").doesNotContain("reconstruire");
        // Et rien n'a été écrit : la nuit reste exactement le squelette qu'elle était.
        SessionDao sessions = new SessionDao(source);
        Long idSession = sessions.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(new SequenceDao(source).findBySession(idSession))
                .as("un refus qui aurait déjà écrit serait pire que pas de refus du tout")
                .isEmpty();
    }

    @Test
    @DisplayName("Un dossier source inexistant est refusé, sans rien toucher")
    void dossier_source_absent_refuse() {
        long idPassage = semerSquelette();

        int code = cli.executer(
                new String[] {
                    "reactiver",
                    "--passage",
                    String.valueOf(idPassage),
                    "--source",
                    workspace.resolve("nulle-part").toString()
                },
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_REFUS);
        assertThat(texteSortie()).contains("Dossier introuvable");
    }

    @Test
    @DisplayName("Un passage inexistant est refusé sans trace d'écriture")
    void passage_inexistant_refuse() {
        int code = cli.executer(
                new String[] {"reactiver", "--passage", "9999", "--source", dossierSource.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_REFUS);
    }
}
