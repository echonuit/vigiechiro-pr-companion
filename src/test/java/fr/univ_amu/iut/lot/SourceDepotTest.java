package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.lot.model.SourceDepot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// [SourceDepot] (#1993) : la séparation entre « ce qu'il y a à déposer » (les identifiants, connus
/// tôt) et « où se trouve le fichier » (résolu au dernier moment).
///
/// C'est cette séparation qui permettra de poser le plan avant que les archives existent, puis de
/// générer au fil de l'eau et de libérer (#1994, #1995).
class SourceDepotTest {

    @Test
    @DisplayName("les identifiants sont les noms de fichiers, dans l'ordre donné")
    void identifiants_dans_l_ordre(@TempDir Path dossier) throws IOException {
        SourceDepot source = SourceDepot.desFichiers(
                List.of(fichier(dossier, "Car-2.zip"), fichier(dossier, "Car-1.zip"), fichier(dossier, "Car-3.zip")));

        // L'ordre est significatif : il fixe l'ordre du plan et celui de l'empreinte. Une copie par
        // Map.copyOf le perdrait silencieusement.
        assertThat(source.identifiants()).containsExactly("Car-2.zip", "Car-1.zip", "Car-3.zip");
    }

    @Test
    @DisplayName("résoudre rend le chemin de l'unité")
    void resoudre_rend_le_chemin(@TempDir Path dossier) throws IOException {
        Path archive = fichier(dossier, "Car-1.zip");

        assertThat(SourceDepot.desFichiers(List.of(archive)).resoudre("Car-1.zip"))
                .contains(archive);
    }

    @Test
    @DisplayName("un identifiant inconnu ne se résout pas (le moteur en fera un échec d'unité)")
    void identifiant_inconnu_ne_se_resout_pas(@TempDir Path dossier) throws IOException {
        SourceDepot source = SourceDepot.desFichiers(List.of(fichier(dossier, "Car-1.zip")));

        assertThat(source.resoudre("Car-9.zip")).isEmpty();
    }

    @Test
    @DisplayName("l'empreinte est calculée à l'appel, pas à la construction")
    void empreinte_paresseuse(@TempDir Path dossier) {
        // Construire une source sur des fichiers absents ne doit rien lire : c'est tout l'intérêt de
        // séparer les identifiants des fichiers, puisqu'une source pourra désigner des archives pas
        // encore écrites (#1994). Seul empreinte() exige qu'ils existent.
        SourceDepot source = SourceDepot.desFichiers(List.of(dossier.resolve("pas-encore-ecrite.zip")));

        assertThat(source.identifiants()).containsExactly("pas-encore-ecrite.zip");
    }

    @Test
    @DisplayName("deux sources sur les mêmes fichiers sont égales (les tests peuvent les comparer)")
    void egalite_par_valeur(@TempDir Path dossier) throws IOException {
        Path archive = fichier(dossier, "Car-1.zip");

        assertThat(SourceDepot.desFichiers(List.of(archive))).isEqualTo(SourceDepot.desFichiers(List.of(archive)));
    }

    private static Path fichier(Path dossier, String nom) throws IOException {
        Path chemin = dossier.resolve(nom);
        Files.write(chemin, new byte[] {1, 2, 3});
        return chemin;
    }
}
