package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.OutilsImport;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fou (esprit cliquet) de **niveau import** : pour chaque spec déclarant des rejets attendus
/// (`attendu.rejets > 0`), il génère la carte puis lance un **import réel** headless et vérifie que le
/// rapport rejette bien le(s) faux WAV. C'est le seul cas de recette qui ne se lit pas à l'inspection
/// seule : un rejet est produit à la transformation, donc on exerce [ServiceImport] jusqu'au bout.
///
/// Import monté à la main sur une base SQLite jetable (`@TempDir` + [MigrationSchema] + parents FK),
/// sans Guice ni JavaFX, sur le patron de `ServiceImportTest`.
class GenerationCartesSDImportCliquetTest {

    private static final Path DOSSIER_SPECS = Path.of("recette", "fixtures", "spec");
    private static final String ID_USER = "u-recette";
    private static final LocalDate DATE_FIGEE = LocalDate.of(2026, 5, 31);
    private static final Prefixe PREFIXE = new Prefixe("640380", 2026, 2, "Z1");

    private final LecteurSpec lecteur = new LecteurSpec();
    private final GenerateurCartesSD generateur = new GenerateurCartesSD();

    @TempDir
    private Path racineTravail;

    /// Id du point d'écoute créé pour l'import de la carte courante (posé par monterServiceImport).
    private Long idPoint;

    @TestFactory
    Stream<DynamicTest> chaque_spec_avec_rejets_les_produit_a_l_import() throws IOException {
        return specsAvecRejets().stream()
                .map(spec -> DynamicTest.dynamicTest(spec.getFileName().toString(), () -> verifierImport(spec)));
    }

    private void verifierImport(Path fichierSpec) throws IOException {
        SpecCarteSd spec = lecteur.lire(fichierSpec);
        Path sd = racineTravail.resolve(spec.fixture() + "-sd");
        generateur.genererVers(spec, sd);

        ServiceImport service = monterServiceImport(spec.fixture());
        ResultatImport resultat = service.importer(sd, idPoint, PREFIXE);
        RapportImport rapport = resultat.rapport();

        assertThat(rapport.compte(StatutImportFichier.REJETE))
                .as("%s : nombre de fichiers rejetés à l'import", spec.fixture())
                .isEqualTo(spec.attendu().rejets());
        assertThat(rapport.aDesRejets())
                .as("%s : le rapport signale des rejets", spec.fixture())
                .isTrue();
    }

    /// Monte un [ServiceImport] sur une base jetable et crée les parents FK (utilisateur -> site ->
    /// point), puis mémorise l'id du point pour l'import. Retourne le service prêt.
    private ServiceImport monterServiceImport(String fixture) {
        Workspace workspace = new Workspace(racineTravail.resolve(fixture + "-ws"));
        SourceDeDonnees source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();

        idPoint = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre(PREFIXE.carre())
                .nomSite("Site recette")
                .point(PREFIXE.codePoint())
                .position(43.5, 5.4)
                .semerSiteEtPoint()
                .idPoint();

        return new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(new CopieProtegee(), new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                workspace,
                new HorlogeFigee(DATE_FIGEE),
                idPassage -> 0,
                new ServiceSauvegarde(source, new HorlogeFigee(DATE_FIGEE), () -> {}),
                Optional.empty(),
                () -> {});
    }

    private List<Path> specsAvecRejets() throws IOException {
        List<Path> specsYaml;
        try (Stream<Path> flux = Files.list(DOSSIER_SPECS)) {
            specsYaml = flux.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        List<Path> avecRejets = new ArrayList<>();
        for (Path spec : specsYaml) {
            if (lecteur.lire(spec).attendu().rejets() > 0) {
                avecRejets.add(spec);
            }
        }
        return avecRejets;
    }
}
