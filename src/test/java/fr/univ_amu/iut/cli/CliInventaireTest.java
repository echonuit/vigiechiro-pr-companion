package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `lister-especes` et `lister-carres` (#3269) : les **deux inventaires** d'« Espèces & observations »,
/// posables en ligne de commande avec les cinq critères de l'écran.
///
/// L'écran répond à « quelles espèces » et « sur quels carrés » sous un jeu de filtres. Aucune des deux
/// questions n'était scriptable : la ligne de commande savait lister des observations une à une, mais rien
/// n'agrégeait. Un naturaliste qui voulait sa liste d'espèces devait ouvrir l'application.
///
/// Ce que ce fichier vérifie, et que les tests unitaires de `FiltresAnalyse` ne peuvent pas voir : que les
/// critères **atteignent** l'agrégation, que les deux formats rendent la même sélection, et que `--sortie`
/// écrit vraiment - dans les deux formats.
class CliInventaireTest {

    @TempDir
    Path workspace;

    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    private Injector injecteur;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    /// Semé à la demande, et non dans le `@BeforeEach` : un test au moins doit voir la base **vide**, et
    /// c'est celui qui compte le plus - c'est là que les critères qui désignent peuvent mentir.
    private void semer() {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        // Deux carrés, deux taxons parents, deux états de revue : le minimum pour qu'un filtre qui ne
        // filtre pas se voie. Une fixture homogène rendrait tous ces tests verts pour rien.
        JeuDeDonneesPassage vallon = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .nomSite("Vallon")
                .point("A1")
                .nuit(1, 2026, "2026-06-08")
                .semer();
        vallon.ajouterObservationValidee("Pipkuh");
        vallon.ajouterObservation("Accnis");

        JeuDeDonneesPassage crete = JeuDeDonneesPassage.dans(source)
                .carre("710255")
                .nomSite("Crête")
                .point("B2")
                .nuit(2, 2025, "2025-07-19")
                .semer();
        crete.ajouterObservation("Nyclei");
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private int executer(String... args) {
        return cli.executer(args, sortie, erreur);
    }

    @Test
    @DisplayName("#3269 : lister-especes rend l'inventaire complet, colonnes de l'export comprises")
    void lister_especes_rend_linventaire() {
        semer();
        int code = executer("lister-especes");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte())
                .as("les colonnes sont celles de l'export de l'écran, pas une seconde nomenclature")
                .contains("code")
                .contains("nom_latin")
                .contains("Pipkuh")
                .contains("Accnis")
                .contains("Nyclei");
    }

    @Test
    @DisplayName("#3269 : --taxon-parent atteint bien l'agrégation, il ne la traverse pas")
    void filtre_par_taxon_parent() {
        semer();
        int code = executer("lister-especes", "--taxon-parent", "Oiseaux");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("Accnis");
        assertThat(capture.texte())
                .as("sans exclusion vérifiée, ce test passerait même si le filtre ne filtrait rien")
                .doesNotContain("Pipkuh");
    }

    @Test
    @DisplayName("#3269 : --lieu et --statut restreignent l'inventaire, comme les puces de l'écran")
    void filtres_par_lieu_et_par_statut() {
        semer();
        assertThat(executer("lister-especes", "--lieu", "640380")).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("Pipkuh").doesNotContain("Nyclei");

        capture.vider();
        assertThat(executer("lister-especes", "--statut", "VALIDEE")).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte())
                .as("une seule des trois observations est validée")
                .contains("Pipkuh")
                .doesNotContain("Accnis")
                .doesNotContain("Nyclei");
    }

    @Test
    @DisplayName("#3269 : DÉSIGNER un taxon parent absent refuse, en nommant ce qui existe (ADR 3082)")
    void un_taxon_parent_absent_refuse() {
        semer();
        int code = executer("lister-especes", "--taxon-parent", "Amphibiens");

        assertThat(code)
                .as("une liste vide laisserait croire que ce taxon parent existe et n'a rien")
                .isNotEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.tout()).contains("Oiseaux").contains("Chiroptères");
    }

    @Test
    @DisplayName("#3269 : lister-carres donne la richesse, recalculée sous les filtres")
    void lister_carres_donne_la_richesse() {
        semer();
        assertThat(executer("lister-carres")).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("richesse").contains("640380").contains("710255");

        capture.vider();
        assertThat(executer("lister-carres", "--taxon-parent", "Chiroptères")).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte())
                .as("la richesse suit la sélection : c'est déjà ce que fait l'écran sous ses puces")
                .contains("640380")
                .contains("710255");
    }

    @Test
    @DisplayName("#3269 : --format json rend la même sélection que le CSV")
    void format_json() {
        semer();
        int code = executer("lister-especes", "--format", "json", "--taxon-parent", "Oiseaux");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte())
                .contains("\"especes\"")
                .contains("\"Accnis\"")
                .doesNotContain("Pipkuh");
    }

    @Test
    @DisplayName("#3269 : --a-enjeu lit le VRAI référentiel du Plan National d'Actions")
    void filtre_par_espece_a_enjeu() {
        semer();
        int code = executer("lister-especes", "--a-enjeu");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        // Deux côtés, parce qu'un seul mentirait. Nyctalus leisleri EST prioritaire (V36) : sa présence
        // prouve que le référentiel est bien chargé et bien câblé jusqu'à la commande. Sans elle, un
        // référentiel vide ou une liaison cassée rendrait la sortie vide - et les exclusions ci-dessous
        // passeraient au vert pour la pire des raisons.
        assertThat(capture.texte())
                .as("Nyctalus leisleri est prioritaire au PNA Chiroptères")
                .contains("Nyclei");
        assertThat(capture.texte())
                .as("Pipistrellus kuhlii ne l'est pas, et un épervier n'est pas un chiroptère")
                .doesNotContain("Pipkuh")
                .doesNotContain("Accnis");
    }

    @Test
    @DisplayName("#3269 : un format inconnu se refuse avant de lire la base")
    void format_inconnu_refuse() {
        int code = executer("lister-carres", "--format", "xml");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(capture.tout()).contains("Format non pris en charge");
    }

    @Test
    @DisplayName("#3269 : --sortie écrit le fichier dans les DEUX formats, et le dit")
    void sortie_ecrit_dans_les_deux_formats() throws Exception {
        semer();
        Path csv = workspace.resolve("inventaire.csv");
        assertThat(executer("lister-especes", "--sortie", csv.toString())).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("Inventaire exporté").contains("espèce(s)");
        assertThat(Files.readString(csv, StandardCharsets.UTF_8))
                .as("le CSV du fichier est celui de la sortie standard : même table, même écrivain")
                .contains("nom_latin")
                .contains("Pipkuh");

        capture.vider();
        Path json = workspace.resolve("sous/inventaire.json");
        assertThat(executer("lister-carres", "--format", "json", "--sortie", json.toString()))
                .isEqualTo(Cli.CODE_SUCCES);
        assertThat(Files.readString(json, StandardCharsets.UTF_8))
                .as("--sortie posé avec --format json était ignoré en silence par la commande d'origine")
                .contains("\"carres\"")
                .contains("640380");
    }

    @Test
    @DisplayName("#3269 : sur une base vide, un critère qui DÉSIGNE ne prétend pas que le lieu manque")
    void base_vide_ne_se_lit_pas_comme_un_lieu_absent() {
        // Pas de `semer()` : c'est le sujet. Sans garde, `--lieu` refuserait en disant « ce lieu n'existe
        // pas » alors que la vérité est qu'il n'y a aucune observation - deux constats opposés, et deux
        // conduites opposées. Piège déjà payé sur `lister-passages`, de même forme ici.
        int code = executer("lister-especes", "--lieu", "640380", "--taxon-parent", "Chiroptères");

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.tout())
                .as("un inventaire vide reste un CSV lisible : un script qui demande du CSV en reçoit")
                .contains("nom_latin")
                .doesNotContain("Lieux présents");
    }
}
