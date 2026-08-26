package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// La seconde voie vers la plateforme réelle : `parleALaPlateforme()`, qui **ne dépose rien**.
///
/// Deux voies y mènent, et elles ne diffèrent que par le dépôt du jeton. Un seul drapeau portait
/// pourtant les deux sens, et seule [BancDeRecette#connecteALaPlateforme()] le levait : un scénario passé
/// par [BancDeRecette#jetonDeLaPlateforme()] tombait dans le client hors ligne que #4332 lie à tout banc
/// n'ayant déclaré aucun serveur. Jeton réel, URL réelle dans l'environnement, appel au port 1, et sur le
/// tir 32894626486 : `Vigie-Chiro est injoignable (ConnectException)`.
///
/// C'est l'écran hors ligne convaincant et **muet sur son propre objet** que l'ADR 4142 interdit. Rien ne
/// le disait : la connexion paraissait brève, et le badge d'identité restait gris.
///
/// Ces cas sont le symétrique de [BancDeRecetteUrlTest] : là-bas, un banc qui n'a rien déclaré ne doit
/// recevoir **aucune** connexion ; ici, un banc qui a déclaré doit en recevoir **au moins une**. Même
/// prise TCP sur port éphémère, pour la raison écrite là-bas.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecetteSansDepotTest {

    /// La forme exacte d'un jeton Vigie-Chiro : trente-deux caractères tirés dans `A-Z0-9`.
    private static final String JETON_REEL = "PLATEFORMESANSDEPOTPOURLEBANC123";

    private final AtomicInteger recues = new AtomicInteger();

    private ServerSocket guichet;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        guichet = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        Thread accueil = new Thread(() -> {
            while (!guichet.isClosed()) {
                try (Socket entrant = guichet.accept()) {
                    recues.incrementAndGet();
                } catch (IOException fermeture) {
                    return;
                }
            }
        });
        accueil.setDaemon(true);
        accueil.start();

        // Posées AVANT le banc, comme dans les deux classes voisines : `@Start` s'exécute avant les
        // `@BeforeEach`, et une propriété posée plus tard arriverait après la construction de
        // l'injecteur.
        System.setProperty("vigiechiro.url", "http://127.0.0.1:" + guichet.getLocalPort() + "/api/v1");
        System.setProperty("vigiechiro.token", JETON_REEL);

        injecteur = BancDeRecette.surLeChrome()
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .parleALaPlateforme()
                .montrer(stage);
    }

    /// Le fork est partagé et réutilisé : une propriété laissée derrière soi servirait à toutes les
    /// classes qui passent après, dans l'ordre où la répartition les a mises.
    @AfterEach
    void rendreLEtatPartage() {
        System.clearProperty("vigiechiro.url");
        System.clearProperty("vigiechiro.token");
        System.clearProperty("vigiechiro.workspace");
        try {
            if (guichet != null) {
                guichet.close();
            }
        } catch (IOException fermeture) {
            // Rien à réparer, mais rien à taire non plus : un guichet qui ne se referme pas laisse un
            // port ouvert pour toute la suite du fork, et l'ADR 0008 refuse qu'un échec s'évapore.
            System.out.printf("  guichet non refermé : %s%n", fermeture.getMessage());
        }
    }

    @Test
    @DisplayName("#4447 : cette voie ne dépose rien, le scénario collera le jeton lui-même")
    void rien_n_est_depose() {
        assertThat(injecteur.getInstance(StockageConnexion.class).token())
                .as("""
                        La réserve du banc doit être VIDE au lever de rideau.

                        C'est la différence entre les deux voies, et la seule : `connecteALaPlateforme()`
                        dépose pour que la modale revérifie seule ; celle-ci ne dépose rien, parce que le
                        scénario filme le geste de coller le jeton.

                        Un dépôt ici connecterait la modale avant que la caméra ne tourne, et le clip
                        montrerait une modale déjà connectée au lieu de la connexion.""")
                .isEmpty();
    }

    @Test
    @DisplayName("#4447 : un banc qui a déclaré la plateforme parle à l'URL ambiante, pas au port 1")
    void le_client_parle_a_l_url_ambiante() {
        // Le jeton, posé comme le scénario le pose : par le champ, donc dans la réserve du banc. Sans
        // lui, `TransportVigieChiro.lire` rend `nonConnecte` SANS appeler, et le cas serait vert quelle
        // que soit l'URL - c'est-à-dire vide.
        injecteur.getInstance(StockageConnexion.class).enregistrer(JETON_REEL, null);

        injecteur.getInstance(ClientVigieChiro.class).moi();

        assertThat(recues.get()).as("""
                        L'adresse désignée par `vigiechiro.url` doit recevoir AU MOINS une connexion.

                        Ce banc a déclaré `parleALaPlateforme()` : son client doit garder le câblage de
                        production, donc composer l'URL de l'environnement.

                        Zéro connexion veut dire qu'il est reparti sur `http://localhost:1`, le client
                        hors ligne de #4332. Un scénario connecté filmerait alors un écran hors ligne
                        convaincant et muet sur son propre objet (ADR 4142) : un jeton réel, une URL
                        réelle, une connexion instantanée, et un badge d'identité qui reste gris sans
                        que rien ne dise pourquoi.""").isPositive();
    }
}
