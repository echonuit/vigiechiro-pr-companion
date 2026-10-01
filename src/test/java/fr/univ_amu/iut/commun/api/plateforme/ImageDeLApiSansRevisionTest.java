package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.images.builder.ImageFromDockerfile;

/// L'image de l'API refuse de se construire sans révision (#5737).
///
/// `api.Dockerfile` n'a pas de valeur par défaut pour `REVISION`, pour que `epingles.properties` reste
/// le seul endroit qui porte la révision (#5661). [PlateformeDeTest] la passe toujours, donc le chemin
/// du refus ne tournait jamais : rendre une valeur par défaut à l'`ARG`, ce qui recréerait une seconde
/// épingle, n'aurait rien fait rougir.
///
/// Le refus est la deuxième instruction après `FROM` : la construction échoue en quelques secondes, sur
/// une image de base que le job a déjà tirée.
@Tag("plateforme-de-test")
class ImageDeLApiSansRevisionTest {

    @Test
    @DisplayName("#5737 : l'image de l'API refuse de se construire sans révision")
    void l_image_refuse_de_se_construire_sans_revision() {
        ImageFromDockerfile sansRevision = new ImageFromDockerfile("vigiechiro-plateforme-api-sans-revision", true)
                .withDockerfile(PlateformeDeTest.dossierDeLaPlateforme().resolve("api.Dockerfile"));

        assertThatThrownBy(sansRevision::get).as("""
                        Construite sans `--build-arg REVISION`, l'image de l'API doit ÉCHOUER, et sur
                        l'instruction qui renvoie à `epingles.properties`.

                        Une image construite veut dire que l'`ARG REVISION` a retrouvé une valeur par
                        défaut : la révision vivrait alors à deux endroits, et le Dockerfile pourrait
                        monter une API que l'épingle ne désigne pas.""").hasStackTraceContaining("epingles.properties");
    }
}
