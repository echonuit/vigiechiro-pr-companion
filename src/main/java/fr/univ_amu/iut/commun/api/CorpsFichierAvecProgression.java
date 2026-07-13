package fr.univ_amu.iut.commun.api;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Flow;
import java.util.function.DoubleConsumer;

/// [HttpRequest.BodyPublisher] qui **streame un fichier depuis le disque** (comme
/// `BodyPublishers.ofFile`) tout en **remontant l'avancement** octet par octet (#984), pour alimenter
/// une barre de progression par archive. Conserve le `Content-Length` du fichier (le `PUT` S3 présigné
/// l'exige, contrairement à un envoi en morceaux). L'avancement est **throttlé au pour-cent** (au plus
/// 101 notifications par fichier) pour ne pas inonder le fil consommateur (chaque notification finit,
/// via le relais IHM, sur le fil JavaFX fourni par le socle).
final class CorpsFichierAvecProgression implements HttpRequest.BodyPublisher {

    private final HttpRequest.BodyPublisher delegue;
    private final long taille;
    private final DoubleConsumer progression;

    private CorpsFichierAvecProgression(HttpRequest.BodyPublisher delegue, long taille, DoubleConsumer progression) {
        this.delegue = delegue;
        this.taille = taille;
        this.progression = progression;
    }

    /// Enveloppe le fichier `fichier` en remontant sa fraction envoyée (0 à 1) à `progression`.
    ///
    /// @throws IOException si le fichier est illisible (taille indéterminable) — le `PUT` échouera de
    ///     toute façon, l'appelant traite l'exception comme un échec de téléversement.
    static HttpRequest.BodyPublisher depuis(Path fichier, DoubleConsumer progression) throws IOException {
        return new CorpsFichierAvecProgression(
                HttpRequest.BodyPublishers.ofFile(fichier), Files.size(fichier), progression);
    }

    @Override
    public long contentLength() {
        return delegue.contentLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> abonne) {
        delegue.subscribe(new AbonneComptant(abonne));
    }

    /// Abonné intercalaire : compte les octets qui transitent vers l'abonné réel et notifie la fraction
    /// à chaque pour-cent franchi. Délègue tout le reste (souscription, erreur, fin) tel quel.
    private final class AbonneComptant implements Flow.Subscriber<ByteBuffer> {

        private final Flow.Subscriber<? super ByteBuffer> reel;
        private long envoyes;
        private int dernierPourcent = -1;

        AbonneComptant(Flow.Subscriber<? super ByteBuffer> reel) {
            this.reel = reel;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            reel.onSubscribe(subscription);
        }

        @Override
        public void onNext(ByteBuffer item) {
            int octets = item.remaining(); // avant transmission : l'abonné réel va consommer le tampon
            reel.onNext(item);
            envoyes += octets;
            if (taille > 0) {
                int pourcent = (int) Math.min(100, envoyes * 100 / taille);
                if (pourcent > dernierPourcent) {
                    dernierPourcent = pourcent;
                    progression.accept(pourcent / 100.0);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            reel.onError(throwable);
        }

        @Override
        public void onComplete() {
            reel.onComplete();
        }
    }
}
