package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.recette.SpecCarteSd.Attendu;
import fr.univ_amu.iut.recette.SpecCarteSd.Enregistreur;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fou (esprit cliquet) du générateur de cartes SD de recette : pour **chaque** spec de
/// `recette/fixtures/spec/`, il génère la carte puis vérifie que l'**inspection réelle** du code
/// d'import ([InspecteurDossier]) constate bien la pathologie déclarée dans le bloc `attendu` de la
/// spec.
///
/// Ainsi le test amarre le générateur à la réalité du code, pas à une liste tenue à la main : il
/// devient rouge si le générateur cesse de produire la bonne carte **ou** si un détecteur d'import
/// change de comportement. Pur système de fichiers, sans JavaFX (comme [InspecteurDossier]).
class GenerationCartesSDCliquetTest {

    private static final Path DOSSIER_SPECS = Path.of("recette", "fixtures", "spec");

    private final LecteurSpec lecteur = new LecteurSpec();
    private final GenerateurCartesSD generateur = new GenerateurCartesSD();
    private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());

    @TempDir
    private Path racineTravail;

    @TestFactory
    Stream<DynamicTest> chaque_spec_produit_la_pathologie_attendue() throws IOException {
        List<Path> specs = specsDeRecette();
        assertThat(specs)
                .as("au moins une spec de carte SD dans %s", DOSSIER_SPECS)
                .isNotEmpty();
        return specs.stream()
                .map(spec -> DynamicTest.dynamicTest(spec.getFileName().toString(), () -> verifier(spec)));
    }

    private void verifier(Path fichierSpec) throws IOException {
        SpecCarteSd spec = lecteur.lire(fichierSpec);
        Path carte = racineTravail.resolve(spec.fixture());
        generateur.genererVers(spec, carte);
        Attendu attendu = spec.attendu();

        if (!attendu.journalLisible()) {
            assertThatThrownBy(() -> inspecteur.inspecter(carte))
                    .as("%s : un journal illisible doit faire échouer l'inspection", spec.fixture())
                    .isInstanceOf(IllegalArgumentException.class);
            return;
        }

        RapportInspection rapport = inspecteur.inspecter(carte);
        assertThat(rapport.aUnJournal())
                .as("%s : présence du journal", spec.fixture())
                .isEqualTo(attendu.aJournal());
        assertThat(rapport.aUnReleveClimatique())
                .as("%s : présence du relevé climatique", spec.fixture())
                .isEqualTo(attendu.aReleve());
        assertThat(rapport.nombreOriginaux())
                .as("%s : nombre d'enregistrements originaux générés", spec.fixture())
                .isEqualTo(nombreOriginauxAttendu(spec));
        assertThat(rapport.etatNommage().name())
                .as("%s : état de nommage des originaux", spec.fixture())
                .isEqualTo(attendu.etatNommage());
        assertThat(rapport.melange().plusieursEnregistreurs())
                .as("%s : mélange de plusieurs enregistreurs", spec.fixture())
                .isEqualTo(attendu.plusieursEnregistreurs());
        assertThat(rapport.coherence().incoherent())
                .as("%s : incohérence journal / enregistrements", spec.fixture())
                .isEqualTo(attendu.incoherent());
        assertThat(rapport.partitionNuits())
                .as("%s : nombre de nuits détectées", spec.fixture())
                .hasSize(attendu.nuits());

        if (spec.zip()) {
            verifierCheminZip(spec, carte, rapport);
        }
    }

    /// Vérifie le chemin décompression : l'archive produite est reconnue comme zip, et l'inspection de
    /// l'arbre extrait retrouve les mêmes originaux et la même présence de journal que l'arbre direct.
    private void verifierCheminZip(SpecCarteSd spec, Path carte, RapportInspection direct) throws IOException {
        Path zip = racineTravail.resolve(spec.fixture() + ".zip");
        GenerateurCartesSD.compresserVers(carte, zip);
        assertThat(ExtracteurZip.estZip(zip))
                .as("%s : l'archive est reconnue comme zip", spec.fixture())
                .isTrue();
        Path base = Files.createDirectories(racineTravail.resolve(spec.fixture() + "-extrait"));
        Path extrait = ExtracteurZip.racineEffective(ExtracteurZip.extraireVersDossierTemporaire(zip, base));
        RapportInspection depuisZip = inspecteur.inspecter(extrait);
        assertThat(depuisZip.nombreOriginaux())
                .as("%s : mêmes originaux après décompression", spec.fixture())
                .isEqualTo(direct.nombreOriginaux());
        assertThat(depuisZip.aUnJournal())
                .as("%s : présence du journal après décompression", spec.fixture())
                .isEqualTo(direct.aUnJournal());
    }

    private static int nombreOriginauxAttendu(SpecCarteSd spec) {
        int total = 0;
        for (Enregistreur enregistreur : spec.enregistreurs()) {
            total += enregistreur.horodatages().size() + enregistreur.fauxWav().size();
        }
        return total;
    }

    private static List<Path> specsDeRecette() throws IOException {
        try (Stream<Path> flux = Files.list(DOSSIER_SPECS)) {
            return flux.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }
}
