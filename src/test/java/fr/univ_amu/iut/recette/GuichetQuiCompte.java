package fr.univ_amu.iut.recette;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/// Une adresse locale qui dit **combien de fois on l'a composée**, et rien d'autre.
///
/// Les bancs de recette doivent savoir vers quel serveur un client parle, sans que `ClientVigieChiro`
/// expose son URL : l'exposer pour un test ouvrirait la production pour la commodité de l'éprouver. Un
/// guichet répond à cette question en comptant les connexions qu'il reçoit, puis en les refermant. Ce
/// qu'on y dit n'importe pas : la question est de savoir si on l'a appelé.
///
/// Une simple prise TCP, et non un serveur HTTP : `com.sun.net.httpserver` vit dans le module
/// `jdk.httpserver`, que le `module-info.java` de production ne lit pas, et l'y ajouter élargirait la
/// surface du produit pour un test.
///
/// Extrait à la clôture de #5642 : trois bancs (`BancDeRecetteUrlTest`, `BancDeRecetteSansDepotTest`,
/// `BancDeRecettePlateformeDeTestTest`) portaient chacun la même boucle. Le partage n'est pas qu'une
/// économie : `BancDeRecetteUrlTest` attend ZÉRO connexion, donc un guichet devenu muet le laisse vert.
/// Sa copie privée pouvait l'être sans que rien ne rougisse ; partagée, ses deux voisins, qui en
/// attendent au moins une, rougissent à sa place (mutation jouée à la clôture).
final class GuichetQuiCompte implements AutoCloseable {

    private final ServerSocket prise;
    private final AtomicInteger recues = new AtomicInteger();

    private GuichetQuiCompte(ServerSocket prise) {
        this.prise = prise;
    }

    /// Un guichet sur la boucle locale, à un port éphémère, qui accepte dès son retour.
    static GuichetQuiCompte ouvrir() throws IOException {
        GuichetQuiCompte guichet = new GuichetQuiCompte(new ServerSocket(0, 0, InetAddress.getLoopbackAddress()));
        Thread accueil = new Thread(() -> {
            while (!guichet.prise.isClosed()) {
                try (Socket entrant = guichet.prise.accept()) {
                    guichet.recues.incrementAndGet();
                } catch (IOException fermeture) {
                    return;
                }
            }
        });
        accueil.setDaemon(true);
        accueil.start();
        return guichet;
    }

    /// L'URL d'API que ce guichet tient, sous la forme qu'attend `ClientVigieChiro`.
    String urlApi() {
        return "http://127.0.0.1:" + prise.getLocalPort() + "/api/v1";
    }

    /// Le nombre de connexions reçues depuis l'ouverture.
    int connexionsRecues() {
        return recues.get();
    }

    /// La fermeture REMONTE : un guichet qui refuse de se fermer laisse un port pris, et c'est au banc
    /// de dire s'il le tait ou s'il échoue.
    @Override
    public void close() throws IOException {
        prise.close();
    }
}
