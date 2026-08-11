package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.persistence.ArborescenceFichiers;
import fr.univ_amu.iut.importation.model.BornesExtraction;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le **cas réel** du chemin `.zip` : une carte SD produite par le générateur de recette, compressée
/// puis décompressée avec les **bornes de production** (#2732).
///
/// Ce test manquait, et son absence a coûté un aller-retour de CI. Les nuits des E2E d'import sont
/// **synthétiques** - une dent de scie périodique, très compressible - et aucune ne passait par
/// l'extraction. Un plafond de taux de décompression écrit sur cette base a donc paru vert en local et
/// refusé, en CI, les fixtures qui ressemblent le plus à une vraie carte (#2732, ADR 2732).
///
/// Il éprouve ce qu'aucun test unitaire ne peut éprouver : que les **défauts** conviennent à ce que
/// l'application existe pour lire. C'est `BornesExtraction.parDefaut()` qui est employé, jamais des
/// bornes fabriquées pour le test.
///
/// Mesuré : la carte `sd-nominale` fait 6,9 Mo et se compresse à 49 Ko, soit un taux de **≈ 140**. Le
/// plafond de 100 qui avait été écrit l'aurait donc refusée - c'est la preuve que ce test couvre
/// l'incident, et pas seulement qu'il en parle.
class ExtractionCarteReelleTest {

    private static final Path SPEC_NOMINALE = Path.of("recette", "fixtures", "spec", "sd-nominale.yaml");

    private final LecteurSpec lecteur = new LecteurSpec();
    private final GenerateurCartesSD generateur = new GenerateurCartesSD();

    @TempDir
    private Path racineTravail;

    @Test
    @DisplayName("Une vraie carte SD, livrée en .zip, passe les bornes de production (#2732)")
    void une_carte_generee_passe_les_bornes_par_defaut() throws IOException {
        SpecCarteSd spec = lecteur.lire(SPEC_NOMINALE);
        Path carte = racineTravail.resolve("carte");
        generateur.genererVers(spec, carte);
        List<Path> attendus = fichiersRelatifs(carte);
        assertThat(attendus).as("la carte générée n'est pas vide").isNotEmpty();

        Path zip = racineTravail.resolve("nuit.zip");
        compresser(carte, zip);
        Path base = Files.createDirectories(racineTravail.resolve("workspace"));
        List<Progression> points = new ArrayList<>();

        Path extrait = ExtracteurZip.extraireVersDossierTemporaire(
                zip, base, points::add, JetonAnnulation.neutre(), BornesExtraction.parDefaut());

        try {
            // Le fait qui compte : les bornes par défaut laissent passer ce que le produit existe pour
            // lire. Un seuil trop serré ferait rougir ici, pas chez l'utilisateur.
            assertThat(fichiersRelatifs(extrait))
                    .as("tous les fichiers de la carte sont ressortis, à l'identique")
                    .containsExactlyInAnyOrderElementsOf(attendus);
            assertThat(points)
                    .as("la décompression rend compte de son avancement")
                    .isNotEmpty();
        } finally {
            ArborescenceFichiers.effacerAuMieux(extrait);
        }
    }

    private static List<Path> fichiersRelatifs(Path racine) throws IOException {
        try (Stream<Path> contenu = Files.walk(racine)) {
            return contenu.filter(Files::isRegularFile)
                    .map(racine::relativize)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void compresser(Path dossier, Path zip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Path fichier : fichiersRelatifs(dossier)) {
                zos.putNextEntry(new ZipEntry(fichier.toString().replace('\\', '/')));
                Files.copy(dossier.resolve(fichier), zos);
                zos.closeEntry();
            }
        }
    }
}
