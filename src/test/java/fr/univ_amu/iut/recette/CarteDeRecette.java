package fr.univ_amu.iut.recette;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Une carte SD de recette, matérialisée là où un banc filmé peut la désigner.
///
/// [GenerateurCartesSD] sait déjà le faire, mais sa méthode est de portée paquet : elle sert à son
/// `main` et aux cas qui vivent ici. Les bancs filmés, eux, vivent dans le paquet de l'écran qu'ils
/// filment - `importation.view` pour `S2` - et n'y ont pas accès.
///
/// Cette façade est donc le point d'entrée des bancs, et le seul. Y passer plutôt que d'élargir la
/// portée du générateur garde une seule porte : le jour où les cartes se matérialisent autrement -
/// mises en cache entre deux cas, par exemple - un seul endroit change.
///
/// Ce que la spec garantit et qui vaut d'être dit : l'arbre est reconstruit **à l'octet près**,
/// aucune date tirée de l'horloge, aucun octet aléatoire. Deux tournages du même commit rendent donc
/// la même carte, et un clip qui change dit que le produit a changé.
public final class CarteDeRecette {

    private static final Path SPECS = Path.of("recette", "fixtures", "spec");

    private CarteDeRecette() {}

    /// Matérialise la carte `fixture` (`sd-nominale`, `sd-melange`…) dans un dossier temporaire neuf.
    ///
    /// @param fixture nom court de la carte, sans le suffixe `.yaml`
    /// @return le dossier de la carte générée
    public static Path materialiser(String fixture) throws IOException {
        Path spec = SPECS.resolve(fixture + ".yaml");
        if (!Files.isRegularFile(spec)) {
            throw new IllegalArgumentException("Aucune spec de carte nommée « " + fixture + " » sous "
                    + SPECS.toAbsolutePath() + ". Les cartes de recette sont des specs déclaratives, et"
                    + " un banc qui en désigne une qui n'existe pas filmerait une inspection vide.");
        }
        return new GenerateurCartesSD().genererDepuisFichier(spec, Files.createTempDirectory("vc-carte-" + fixture));
    }
}
