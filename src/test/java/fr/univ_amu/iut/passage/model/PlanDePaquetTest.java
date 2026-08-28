package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ce que le plan d'un paquet garantit **avant** qu'un octet soit écrit (#4625).
///
/// Le patron est celui de `CompacteurDepot`, qui sépare déjà « planifier sans rien écrire » (#1994)
/// de l'écriture, et expose une estimation « avec le même calcul que le garde-fou » (#808).
class PlanDePaquetTest {

    /// Nommé plutôt que répété : compter des octets à la main dans une assertion, c'est écrire un
    /// second calcul qui peut se tromper, et celui-ci s'était trompé d'un octet.
    private static final String MANIFESTE = "{\"nuit\":1}";

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Un plan n'écrit aucun fichier : c'est ce qui le distingue d'un essai")
    void un_plan_n_ecrit_aucun_fichier() throws IOException {
        Path sequence = fichierDe("seq-1.wav", 1_500);
        Path destination = dossier.resolve("paquet.zip");

        PlanDePaquet plan = PlanDePaquet.pour(destination, MANIFESTE, List.of(sequence));

        assertThat(plan.octetsEstimes()).as("le plan sait ce qu'il pèsera").isPositive();
        assertThat(Files.exists(destination))
                .as("et n'a rien écrit pour le savoir")
                .isFalse();
        assertThat(fichiersDu(dossier))
                .as("aucun fichier neuf, pas même temporaire")
                .containsExactly(sequence);
    }

    @Test
    @DisplayName("L'estimation se ventile par nature : le poids des séquences se voit séparément")
    void l_estimation_se_ventile_par_nature() throws IOException {
        Path a = fichierDe("seq-a.wav", 1_000);
        Path b = fichierDe("seq-b.wav", 2_000);

        PlanDePaquet plan = PlanDePaquet.pour(dossier.resolve("p.zip"), MANIFESTE, List.of(a, b));

        assertThat(plan.octetsParNature().get(NatureDEntree.SEQUENCE))
                .as("les séquences pèsent la somme de leurs tailles")
                .isEqualTo(3_000L);
        assertThat(plan.octetsParNature().get(NatureDEntree.METADONNEES))
                .as("les métadonnées pèsent leur texte, et se voient à part des séquences")
                .isEqualTo(MANIFESTE.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    @DisplayName("Une séquence introuvable est un avertissement du plan, pas une panne d'écriture")
    void une_sequence_introuvable_est_un_avertissement() throws IOException {
        Path presente = fichierDe("seq-ok.wav", 500);
        Path absente = dossier.resolve("seq-partie.wav");

        PlanDePaquet plan = PlanDePaquet.pour(dossier.resolve("p.zip"), "{}", List.of(presente, absente));

        assertThat(plan.avertissements())
                .as("le plan nomme ce qu'il n'a pas pu lire, plutôt que de le compter pour zéro")
                .singleElement()
                .asString()
                .contains("seq-partie.wav");
        assertThat(plan.octetsEstimes())
                .as("et n'ajoute pas un poids qu'il n'a pas mesuré")
                .isEqualTo(500L + 2L);
    }

    private Path fichierDe(String nom, int octets) throws IOException {
        Path fichier = dossier.resolve(nom);
        Files.write(fichier, new byte[octets]);
        return fichier;
    }

    private static List<Path> fichiersDu(Path racine) throws IOException {
        try (Stream<Path> flux = Files.list(racine)) {
            return flux.sorted().toList();
        }
    }
}
