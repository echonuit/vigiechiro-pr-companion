package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Habillage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le garde des captures devant un libellé enroulable auquel il manque **un pixel** de hauteur (#5899).
///
/// JavaFX n'en demande pas plus pour rabattre un texte de deux lignes sur une seule et le finir par
/// une ellipse. Le garde tolérait pourtant cet écart : sa comparaison était stricte, `manque > 1`, là
/// où la tolérance voulait seulement écarter les arrondis de la mise en page. Deux aperçus de l'écran
/// de lot sont partis ainsi, chacun avec deux consignes coupées, sans que rien ne refuse.
///
/// Le banc construit l'écart au lieu de viser une taille : il mesure la hauteur que le libellé demande
/// pour sa largeur, puis lui en retire exactement ce qu'il veut éprouver.
@ExtendWith(ApplicationExtension.class)
class LisibiliteCaptureUnPixelTest {

    private static final String CONSIGNE = "Téléversez la nuit directement sur Vigie-Chiro (les séquences"
            + " transformées, au format attendu par la plateforme). En cas de besoin, un dépôt manuel des"
            + " archives ZIP reste possible depuis le dossier :";

    /// Ce que le garde a jeté devant un libellé auquel il manque `manque` pixels, ou `null` s'il a
    /// laissé passer. Le second élément dit l'écart réellement obtenu, pour que le cas ne conclue pas
    /// sur un écart qu'il n'a pas construit.
    private static Object[] jugement(double manque) throws InterruptedException {
        AtomicReference<Object[]> rendu = new AtomicReference<>();
        CountDownLatch fini = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Label libelle = new Label(CONSIGNE);
                libelle.setWrapText(true);
                libelle.setMinHeight(0);
                VBox racine = new VBox(libelle);
                Scene scene = Habillage.scene(racine, 420, 300);
                racine.applyCss();
                racine.layout();
                double demandee = libelle.prefHeight(libelle.getWidth());
                libelle.setMaxHeight(demandee - manque);
                racine.layout();
                double obtenu = libelle.prefHeight(libelle.getWidth()) - libelle.getHeight();
                Throwable refus = null;
                try {
                    LisibiliteCapture.refuserToutTexteIllisible(scene);
                } catch (IllegalStateException probleme) {
                    refus = probleme;
                }
                rendu.set(new Object[] {refus, obtenu});
            } finally {
                fini.countDown();
            }
        });
        assertThat(fini.await(30, TimeUnit.SECONDS))
                .as("le jugement doit rendre la main")
                .isTrue();
        return rendu.get();
    }

    @Test
    @DisplayName("#5899 : un libellé enroulable auquel il manque exactement un pixel est refusé")
    void un_pixel_manquant_est_refuse() throws InterruptedException {
        Object[] rendu = jugement(1.0);

        assertThat((double) rendu[1])
                .as("l'écart construit est bien d'un pixel : sinon le cas éprouverait autre chose")
                .isEqualTo(1.0);
        assertThat(rendu[0])
                .as("un pixel suffit à JavaFX pour élider : le garde ne peut pas le tolérer")
                .isInstanceOf(IllegalStateException.class);
        assertThat(((Throwable) rendu[0]).getMessage()).contains("manque 1 px");
    }

    @Test
    @DisplayName("#5899 : un libellé enroulé qui a toute sa hauteur passe")
    void un_libelle_qui_tient_passe() throws InterruptedException {
        Object[] rendu = jugement(0.0);

        assertThat((double) rendu[1]).isZero();
        assertThat(rendu[0])
                .as("le garde ne crie pas sur un libellé qui tient : c'est son témoin négatif")
                .isNull();
    }

    @Test
    @DisplayName("#5899 : deux pixels manquants étaient déjà refusés, et le restent")
    void deux_pixels_manquants_restent_refuses() throws InterruptedException {
        Object[] rendu = jugement(2.0);

        assertThat((double) rendu[1]).isEqualTo(2.0);
        assertThat(rendu[0]).isInstanceOf(IllegalStateException.class);
    }
}
