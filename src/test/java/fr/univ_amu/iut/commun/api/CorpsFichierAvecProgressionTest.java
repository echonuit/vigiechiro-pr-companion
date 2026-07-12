package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Publisher comptant (#984) : streame un fichier depuis le disque tout en remontant sa fraction
/// envoyée (0 à 1), throttlée au pour-cent, en conservant le `Content-Length` (exigé par le `PUT` S3
/// présigné). On draine le publisher comme le ferait HttpClient et on observe les fractions notifiées.
class CorpsFichierAvecProgressionTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("remonte une fraction croissante jusqu'à 1.0 et conserve le Content-Length")
    void progression_croissante_jusqu_a_un() throws Exception {
        Path fichier = Files.write(dossier.resolve("gros.bin"), new byte[500_000]);
        List<Double> fractions = new ArrayList<>();
        HttpRequest.BodyPublisher publisher = CorpsFichierAvecProgression.depuis(fichier, fractions::add);

        assertThat(publisher.contentLength()).isEqualTo(500_000L);
        drainer(publisher);

        assertThat(fractions).isNotEmpty().isSorted();
        assertThat(fractions).allSatisfy(f -> assertThat(f).isBetween(0.0, 1.0));
        assertThat(fractions.get(fractions.size() - 1)).isEqualTo(1.0);
    }

    /// Consomme entièrement le publisher (comme HttpClient) et attend la fin ou l'erreur.
    private static void drainer(HttpRequest.BodyPublisher publisher) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                item.position(item.limit()); // consomme le tampon comme l'abonné réel
            }

            @Override
            public void onError(Throwable throwable) {
                fini.countDown();
            }

            @Override
            public void onComplete() {
                fini.countDown();
            }
        });
        assertThat(fini.await(5, TimeUnit.SECONDS)).as("upload simulé terminé").isTrue();
    }
}
