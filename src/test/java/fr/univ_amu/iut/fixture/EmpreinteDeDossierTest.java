package fr.univ_amu.iut.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'empreinte voit-elle ce qu'elle promet de voir (#5433, passe 6) ?
///
/// Elle sert un seul cas, `ServiceImportTest#import_ne_touche_pas_a_la_carte`, et rien ne la gardait.
/// C'est le défaut que ce dépôt redoute le plus : une implantation dégénérée qui rendrait toujours la
/// même chose ferait comparer `{}` à `{}`, et son usager passerait au vert **sans rien vérifier**.
///
/// Les trois premiers cas sont les trois façons de toucher un dossier ; le quatrième est le contrôle
/// négatif, sans lequel une empreinte qui changerait à chaque appel les passerait tous.
class EmpreinteDeDossierTest {

    @TempDir
    Path dossier;

    private Map<Path, String> avant;

    @BeforeEach
    void semer() throws IOException {
        Files.writeString(dossier.resolve("un.txt"), "premier");
        Files.createDirectories(dossier.resolve("sous"));
        Files.writeString(dossier.resolve("sous").resolve("deux.txt"), "second");
        avant = EmpreinteDeDossier.de(dossier);
    }

    @Test
    @DisplayName("#5433 : un fichier AJOUTÉ change l'empreinte")
    void un_fichier_ajoute_se_voit() throws IOException {
        Files.writeString(dossier.resolve("indesirable.tmp"), "x");

        assertThat(EmpreinteDeDossier.de(dossier)).isNotEqualTo(avant);
    }

    @Test
    @DisplayName("#5433 : un fichier RENOMMÉ change l'empreinte, alors que son contenu est intact")
    void un_fichier_renomme_se_voit() throws IOException {
        // Le cas que `CopieProtegee` ne pouvait pas voir : elle compare l'empreinte de CHAQUE fichier
        // avant et après, et un renommage les laisse toutes intactes.
        Files.move(dossier.resolve("un.txt"), dossier.resolve("un-renomme.txt"));

        assertThat(EmpreinteDeDossier.de(dossier)).isNotEqualTo(avant);
    }

    @Test
    @DisplayName("#5433 : un contenu MODIFIÉ change l'empreinte, à nom et à taille égaux")
    void un_contenu_modifie_se_voit() throws IOException {
        // Même longueur que « premier », pour que la taille seule ne suffise pas à trancher.
        Files.writeString(dossier.resolve("un.txt"), "dernier");

        assertThat(EmpreinteDeDossier.de(dossier)).isNotEqualTo(avant);
    }

    @Test
    @DisplayName("#5433 : LIRE le dossier ne change pas son empreinte")
    void une_simple_lecture_ne_change_rien() throws IOException {
        // Le contrôle négatif, et la raison d'être de l'empreinte : elle ne lit ni dates ni
        // permissions, sinon un `atime` ferait rougir son usager sur une lecture, c'est-à-dire sur ce
        // que la règle R9 autorise expressément.
        Files.readAllBytes(dossier.resolve("un.txt"));
        Files.readAllBytes(dossier.resolve("sous").resolve("deux.txt"));

        assertThat(EmpreinteDeDossier.de(dossier)).isEqualTo(avant);
    }
}
