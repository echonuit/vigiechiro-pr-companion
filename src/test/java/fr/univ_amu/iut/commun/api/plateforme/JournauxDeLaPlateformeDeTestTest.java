package fr.univ_amu.iut.commun.api.plateforme;

import fr.univ_amu.iut.recette.Attente;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Chaque conteneur de la plateforme verse son journal là où le job le dépose quand il rougit (#6183).
///
/// Le job rougissait sur des écritures restées sans réponse, et rien ne disait ce que l'API faisait à
/// ce moment-là. Ce banc envoie à l'API une requête marquée, et exige de la relire dans le journal que
/// la plateforme a écrit : un journal qui ne serait plus versé le fait rougir.
@Tag("plateforme-de-test")
class JournauxDeLaPlateformeDeTestTest {

    private static final long DELAI_DU_JOURNAL_MS = 10_000L;

    @Test
    @DisplayName("#6183 : une requête reçue par l'API se relit dans le journal que la plateforme verse")
    void une_requete_recue_par_l_api_se_relit_dans_son_journal() throws IOException, InterruptedException {
        String marque = "temoin-6183-" + UUID.randomUUID();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest requete = HttpRequest.newBuilder(
                            URI.create(PlateformeDeTest.acces().urlDuFront() + "?" + marque))
                    .build();
            client.send(requete, HttpResponse.BodyHandlers.discarding());
        }
        attendreDansLeJournal("api", marque);
    }

    @Test
    @DisplayName("#6183 : le faux S3 et Mongo versent aussi le leur")
    void le_faux_s3_et_mongo_versent_aussi_le_leur() {
        PlateformeDeTest.acces();

        attendreDansLeJournal("faux-s3", "faux-s3 \"PUT ");
        attendreDansLeJournal("mongo", "Waiting for connections");
    }

    /// Le journal arrive par un flux à part de celui que la plateforme attend pour démarrer : la ligne
    /// peut y paraître un peu après.
    private static void attendreDansLeJournal(String conteneur, String ligne) {
        Path journal = JournauxDesConteneurs.fichier(conteneur);
        Attente.que(
                () -> lire(journal).contains(ligne),
                () -> "« " + ligne + " » n'a pas paru dans " + journal,
                DELAI_DU_JOURNAL_MS);
    }

    private static String lire(Path journal) {
        try {
            return Files.exists(journal) ? Files.readString(journal) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
