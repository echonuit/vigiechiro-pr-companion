package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.images.builder.ImageFromDockerfile;

/// L'image de l'API refuse de se construire sans l'une de ses deux révisions (#5737, #6060).
///
/// `api.Dockerfile` n'a pas de valeur par défaut pour `REVISION` ni pour `FRONT_REVISION`, pour que
/// `epingles.properties` reste le seul endroit qui les porte (#5661). [PlateformeDeTest] les passe
/// toujours, donc le chemin du refus ne tournait jamais : rendre une valeur par défaut à un `ARG`, ce qui
/// recréerait une seconde épingle, n'aurait rien fait rougir.
///
/// Chaque cas vise le NOM de son épingle. Les deux refus citent `epingles.properties`, et l'étape du front
/// se construit avant celle de l'API : sans cela, le cas de l'API passerait sur le refus du front.
@Tag("plateforme-de-test")
class ImageDeLApiSansRevisionTest {

    @Test
    @DisplayName("#5737 : l'image de l'API refuse de se construire sans révision")
    void l_image_refuse_de_se_construire_sans_revision() {
        ImageFromDockerfile sansRevision = new ImageFromDockerfile("vigiechiro-plateforme-api-sans-revision", true)
                .withDockerfile(PlateformeDeTest.dossierDeLaPlateforme().resolve("api.Dockerfile"))
                .withBuildArg("FRONT_REVISION", epingles().getProperty("front.revision"));

        assertThatThrownBy(sansRevision::get).as("""
                        Construite sans `--build-arg REVISION`, l'image de l'API doit ÉCHOUER, et sur
                        l'instruction qui renvoie à `api.revision`.

                        Une image construite veut dire que l'`ARG REVISION` a retrouvé une valeur par
                        défaut : la révision vivrait alors à deux endroits, et le Dockerfile pourrait
                        monter une API que l'épingle ne désigne pas.""").hasStackTraceContaining("api.revision");
    }

    @Test
    @DisplayName("#6060 : l'image de l'API refuse de se construire sans révision du front")
    void l_image_refuse_de_se_construire_sans_revision_du_front() {
        ImageFromDockerfile sansRevision = new ImageFromDockerfile("vigiechiro-plateforme-api-sans-front", true)
                .withDockerfile(PlateformeDeTest.dossierDeLaPlateforme().resolve("api.Dockerfile"))
                .withBuildArg("REVISION", epingles().getProperty("api.revision"));

        assertThatThrownBy(sansRevision::get).as("""
                        Construite sans `--build-arg FRONT_REVISION`, l'image doit ÉCHOUER sur
                        l'instruction qui renvoie à `front.revision`. Une image construite voudrait dire
                        que le front a retrouvé une révision par défaut, hors de `epingles.properties`.""").hasStackTraceContaining("front.revision");
    }

    private static Properties epingles() {
        return PlateformeDeTest.epingles(PlateformeDeTest.dossierDeLaPlateforme());
    }
}
