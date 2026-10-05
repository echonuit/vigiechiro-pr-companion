package fr.univ_amu.iut.commun.api.plateforme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/// Ce que le worker de Vigie-Chiro ferait, joué à la demande sur la plateforme de test (#5970).
///
/// La plateforme de test n'a pas de worker (ADR 5641) : une archive déposée y reste une archive, et
/// l'analyse ne tourne pas. Cette classe lance, dans le conteneur de l'API, la part du worker qu'un
/// banc a besoin d'observer, par le code du serveur lui-même. Elle vit hors de [PlateformeDeTest],
/// qui monte la plateforme et n'a pas à savoir ce qu'on y joue ensuite.
public final class WorkerDeLaPlateformeDeTest {

    private static final String ARCHIVES_DANS_L_API = "/tmp/archives";

    private WorkerDeLaPlateformeDeTest() {}

    /// Joue l'extraction des archives d'une participation, et rend ce que la participation porte
    /// ensuite pour les sons dont le titre commence par `prefixe`.
    ///
    /// `joue_l_extraction.py` lance la moitié du worker qui extrait. Les archives lui sont remises par
    /// leur titre : le conteneur de l'API ne joint pas le faux S3, que seule la JVM atteint.
    public static synchronized JsonObject jouerLExtraction(
            String participation, Map<String, Path> archivesParTitre, String prefixe) {
        GenericContainer<?> api = PlateformeDeTest.api();
        api.copyFileToContainer(
                MountableFile.forHostPath(
                        PlateformeDeTest.dossierDeLaPlateforme().resolve("joue_l_extraction.py")),
                "/tmp/joue_l_extraction.py");
        try {
            api.execInContainer("sh", "-c", "rm -rf " + ARCHIVES_DANS_L_API + " && mkdir -p " + ARCHIVES_DANS_L_API);
            archivesParTitre.forEach((titre, archive) ->
                    api.copyFileToContainer(MountableFile.forHostPath(archive), ARCHIVES_DANS_L_API + "/" + titre));
            Container.ExecResult rendu = api.execInContainer(
                    "python", "/tmp/joue_l_extraction.py",
                    "--participation", participation,
                    "--archives", ARCHIVES_DANS_L_API,
                    "--prefixe", prefixe);
            if (rendu.getExitCode() != 0) {
                throw new IllegalStateException("l'extraction a échoué : " + rendu.getStderr());
            }
            // `unzip` écrit ce qu'il extrait sur la même sortie : le bilan est la dernière ligne.
            String bilan = rendu.getStdout()
                    .strip()
                    .lines()
                    .reduce((avant, ligne) -> ligne)
                    .orElse("");
            return JsonParser.parseString(bilan).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
