package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.gluonhq.maps.tile.TileRetriever;
import com.gluonhq.maps.tile.TileRetrieverProvider;
import java.util.concurrent.CompletableFuture;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le fond de carte des bancs ne touche pas le réseau (#5789).
///
/// **Le premier cas est celui qui porte tout.** Si `TileRetrieverProvider.load()` ne rendait pas ce
/// service, les autres passeraient quand même : le repli réseau fonctionne quand le réseau
/// fonctionne, et le banc ne dirait rien de ce qu'il arrive sans lui.
@ExtendWith(ApplicationExtension.class)
class TuilesSansReseauTest {

    /// Le côté d'une tuile OSM, que `MapView` suppose de ce qu'on lui rend.
    private static final int COTE = 256;

    @Start
    void start(Stage stage) {
        // Rien à monter : ces cas n'ont besoin que du runtime JavaFX, que `WritableImage` exige.
    }

    @Test
    @DisplayName("#5789 : c'est NOTRE récupérateur que la bibliothèque choisit, pas son repli réseau")
    void le_service_declare_est_celui_que_la_bibliotheque_charge() {
        TileRetriever charge = TileRetrieverProvider.getInstance().load();

        assertThat(charge)
                .as("le module `com.gluonhq.maps` declare `uses TileRetriever` et `load()` prend le"
                        + " premier service trouve ; sans notre declaration il construirait un"
                        + " `CachedOsmTileRetriever` qui interroge tile.openstreetmap.org")
                .isInstanceOf(TuilesSansReseau.class);
    }

    @Test
    @DisplayName("#5789 : la tuile est DÉJÀ là quand on la demande, donc rien n'a été attendu")
    void la_tuile_est_rendue_sans_attendre() {
        CompletableFuture<Image> tuile = new TuilesSansReseau().loadTile(12, 2048, 1364);

        // ⟨« déjà complété » est ce qu'aucun récupérateur réseau ne peut garantir⟩ Un futur en vol
        // serait indiscernable d'un succès, puis échouerait plus tard dans l'attente d'un banc qui
        // ne parle pas de carte - c'est exactement le défaut qu'on retire.
        assertThat(tuile.isDone())
                .as("le futur est complété avant qu'on le regarde")
                .isTrue();
        assertThat(tuile.isCompletedExceptionally())
                .as("et il n'est pas complété par un échec, qui reproduirait le défaut")
                .isFalse();
        assertThat(tuile.join())
                .as("une image blanche, et non null : `MapView` dessine ce qu'on lui rend")
                .isNotNull();
    }

    @Test
    @DisplayName("#5789 : la tuile a le côté qu'un fond de carte attend")
    void la_tuile_a_le_cote_d_une_tuile_osm() {
        Image tuile = new TuilesSansReseau().loadTile(0, 0, 0).join();

        assertThat(tuile.getWidth()).as("largeur").isEqualTo(COTE);
        assertThat(tuile.getHeight()).as("hauteur").isEqualTo(COTE);
    }

    @Test
    @DisplayName("#5789 : la même tuile sert à tout l'écran, et n'est pas réallouée par appel")
    void la_tuile_est_partagee_entre_les_appels() {
        TuilesSansReseau service = new TuilesSansReseau();

        Image premiere = service.loadTile(12, 2048, 1364).join();
        Image seconde = service.loadTile(3, 1, 7).join();

        // `MapView` demande des centaines de tuiles par écran. Allouer une image de 256 sur 256 par
        // appel ferait payer aux bancs ce que ce service existe pour leur épargner.
        assertThat(seconde)
                .as("la même instance, quelles que soient les coordonnées")
                .isSameAs(premiere);
    }
}
