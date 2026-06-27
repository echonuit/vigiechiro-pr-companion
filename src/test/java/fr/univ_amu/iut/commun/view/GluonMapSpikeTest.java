package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// **Spike #152 — faisabilité Gluon Maps sous JavaFX 26 headless.**
///
/// Prouve qu'un [MapView] s'instancie, s'attache à la scène et accepte une **couche custom**
/// ([MapLayer] + projection `getMapPoint`) sous la plateforme Glass Headless (FX 26), **sans tuiles
/// réseau** (le fond de tuiles est best-effort et ne charge pas en CI ; on ne l'asserte jamais). C'est
/// exactement le mécanisme des futurs overlays carrés/points. Si ce test passe en headless, Gluon Maps
/// est viable pour le composant `CarteSites`.
@ExtendWith(ApplicationExtension.class)
class GluonMapSpikeTest {

    private MapView carte;
    private CoucheTest couche;

    @Start
    void start(Stage stage) {
        carte = new MapView();
        carte.setZoom(6);
        carte.setCenter(new MapPoint(46.6, 2.5)); // centre France métropolitaine
        couche = new CoucheTest();
        carte.addLayer(couche);
        stage.setScene(new Scene(carte, 640, 480));
        stage.show();
    }

    @Test
    void mapview_et_couche_custom_s_instancient_headless() {
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(carte.getScene())
                .as("MapView attaché à la scène sous FX 26 headless (pas de crash)")
                .isNotNull();
        assertThat(carte.getZoom()).isEqualTo(6.0);
        assertThat(couche.getChildrenUnmodifiable())
                .as("la couche custom a bien placé son nœud (mécanisme des overlays)")
                .isNotEmpty();
        assertThat(couche.projectionObtenue)
                .as("getMapPoint a renvoyé une projection (la couche s'est mise en page)")
                .isTrue();
    }

    /// Couche minimale : un marqueur projeté via `getMapPoint`, comme le feront `CoucheCarres` /
    /// `CouchePoints`. Note si la projection a abouti (mise en page effective).
    private static final class CoucheTest extends MapLayer {
        private final Circle marqueur = new Circle(5);
        private boolean projectionObtenue;

        private CoucheTest() {
            getChildren().add(marqueur);
        }

        @Override
        protected void layoutLayer() {
            Point2D p = getMapPoint(46.6, 2.5);
            if (p != null) {
                projectionObtenue = true;
                marqueur.setTranslateX(p.getX());
                marqueur.setTranslateY(p.getY());
            }
        }
    }
}
