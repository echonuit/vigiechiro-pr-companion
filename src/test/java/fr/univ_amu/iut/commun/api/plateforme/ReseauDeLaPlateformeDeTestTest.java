package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Les profils de réseau de la plateforme de test s'appliquent bien à ce que Companion joint : l'API, et
/// le faux S3 où partent ses téléversements (#6258, chantier #5646).
///
/// Chaque cas mesure par la JVM, comme Companion joint la plateforme. Un mandataire qui ne serait plus
/// sur le chemin, ou une adresse de dépôt qui le contournerait, laisserait le cas aussi rapide sous le
/// profil qu'au nominal, et le ferait rougir.
@Tag("plateforme-de-test")
class ReseauDeLaPlateformeDeTestTest {

    /// La taille d'un téléversement assez gros pour que le débit limité le ralentisse nettement.
    private static final int OCTETS_TELEVERSES = 128 * 1024;

    @Test
    @DisplayName("#6258 : sous le profil lent, une réponse de l'API arrive avec la latence posée")
    void sous_le_profil_lent_l_api_repond_avec_la_latence_posee() throws IOException, InterruptedException {
        URI front = URI.create(PlateformeDeTest.acces().urlDuFront());
        long nominal = dureeMs(() -> lireStatut(front));

        long lent;
        try (var _ = PlateformeDeTest.sous(ProfilReseau.LENT)) {
            lent = dureeMs(() -> lireStatut(front));
        }

        assertThat(lent)
                .as("sous le profil lent, l'appel doit durer au moins la latence posée (nominal : %d ms)", nominal)
                .isGreaterThanOrEqualTo(ProfilReseau.LATENCE_MS);
    }

    @Test
    @DisplayName("#6258 : sous le débit limité, un téléversement vers le faux S3 prend le temps de ses octets")
    void sous_le_debit_limite_un_televersement_prend_le_temps_de_ses_octets() throws IOException, InterruptedException {
        URI objet =
                URI.create(PlateformeDeTest.acces().urlS3() + "/plateforme-de-test/temoin-6258-" + UUID.randomUUID());
        long nominal = dureeMs(() -> televerser(objet));

        long limite;
        try (var _ = PlateformeDeTest.sous(ProfilReseau.DEBIT_LIMITE)) {
            limite = dureeMs(() -> televerser(objet));
        }

        // 128 Kio à 64 Kio par seconde : deux secondes. La marge absorbe ce que le mandataire laisse
        // passer d'un seul tenant au début.
        assertThat(limite)
                .as(
                        "sous le débit limité, le téléversement doit traverser le mandataire du faux S3"
                                + " (nominal : %d ms)",
                        nominal)
                .isGreaterThanOrEqualTo(1_500L);
    }

    @Test
    @DisplayName("#6258 : sous la coupure, l'API est injoignable, et elle répond de nouveau une fois le profil retiré")
    void sous_la_coupure_l_api_est_injoignable_puis_repond() throws IOException, InterruptedException {
        URI front = URI.create(PlateformeDeTest.acces().urlDuFront());

        try (var _ = PlateformeDeTest.sous(ProfilReseau.COUPURE)) {
            assertThatThrownBy(() -> lireStatut(front))
                    .as("sous la coupure, aucune connexion ne doit aboutir")
                    .isInstanceOf(IOException.class);
        }

        assertThat(lireStatut(front))
                .as("le profil retiré, la plateforme doit répondre comme avant")
                .isEqualTo(200);
    }

    /// Un client neuf par appel : une connexion gardée d'un appel à l'autre traverserait un profil posé
    /// entre les deux sans le subir.
    private static int lireStatut(URI adresse) throws IOException, InterruptedException {
        try (HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest requete = HttpRequest.newBuilder(adresse)
                    .timeout(Duration.ofSeconds(10))
                    .build();
            return client.send(requete, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    private static int televerser(URI objet) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest requete = HttpRequest.newBuilder(objet)
                    .timeout(Duration.ofSeconds(30))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[OCTETS_TELEVERSES]))
                    .build();
            int statut =
                    client.send(requete, HttpResponse.BodyHandlers.discarding()).statusCode();
            assertThat(statut).as("le faux S3 doit accepter le téléversement").isEqualTo(200);
            return statut;
        }
    }

    private static long dureeMs(Appel appel) throws IOException, InterruptedException {
        long debut = System.nanoTime();
        appel.lancer();
        return (System.nanoTime() - debut) / 1_000_000;
    }

    @FunctionalInterface
    private interface Appel {
        int lancer() throws IOException, InterruptedException;
    }
}
