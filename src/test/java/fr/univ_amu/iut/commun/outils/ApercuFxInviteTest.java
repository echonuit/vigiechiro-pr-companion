package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Habillage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Garde-fou du garde-fou, sur l'**invite** d'un champ de recherche (#3170).
///
/// Le contrôle d'élision d'[ApercuFx] refuse un libellé rendu **avec** une ellipse. Une invite de
/// `TextField` n'en pose aucune : JavaFX la **rogne net**, « Rechercher (espèce, fichier, comm ». Le
/// garde ne la voyait donc pas, et la capture partait avec une invite amputée - qui ne dit plus ce que
/// la recherche couvre, c'est-à-dire tout ce qu'elle avait à dire.
///
/// Le correctif a élargi le contrôle aux `TextInputControl`. **Rien ne le vérifiait** : c'est ce que
/// cette clôture de suites a trouvé, et la raison d'être de ce fichier. Sans lui, retirer le contrôle
/// de l'invite ne casserait rien de visible, et la panne réapparaîtrait sous la forme exacte qu'elle
/// avait déjà prise.
@ExtendWith(ApplicationExtension.class)
class ApercuFxInviteTest {

    /// L'invite réelle de « Sons & validation », celle qui a motivé #3170.
    private static final String INVITE = "Rechercher (espèce, fichier, commentaire…)";

    /// Exécute `capture` **sur le fil JavaFX** et rend ce qu'elle a jeté, ou `null`.
    private static Throwable executerSurLeFilFx(Runnable capture) throws InterruptedException {
        AtomicReference<Throwable> jetee = new AtomicReference<>();
        CountDownLatch fini = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                capture.run();
            } catch (RuntimeException | Error probleme) {
                jetee.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        assertThat(fini.await(30, TimeUnit.SECONDS))
                .as("la capture doit rendre la main")
                .isTrue();
        return jetee.get();
    }

    private static Scene sceneAvecChamp(double largeurChamp) {
        TextField champ = new TextField();
        champ.setPromptText(INVITE);
        champ.setMinWidth(Double.NEGATIVE_INFINITY);
        champ.setPrefWidth(largeurChamp);
        champ.setMaxWidth(largeurChamp);
        return Habillage.scene(new VBox(champ), largeurChamp + 40, 120);
    }

    @Test
    @DisplayName("#3170 : une invite rognée fait échouer la capture, alors qu'aucune ellipse ne la signale")
    void invite_rognee_refuse(@TempDir Path dossier) throws InterruptedException {
        Path fichier = dossier.resolve("apercu.png");

        Throwable refus = executerSurLeFilFx(() -> ApercuFx.enregistrerPng(sceneAvecChamp(140), fichier));

        assertThat(refus)
                .as("c'est précisément le cas que l'ancien contrôle laissait passer")
                .isInstanceOf(IllegalStateException.class);
        assertThat(refus.getMessage()).contains("Rechercher");
        assertThat(fichier)
                .as("une capture dont l'invite est amputée ne doit pas être écrite")
                .doesNotExist();
    }

    @Test
    @DisplayName("#3337 : la bascule tombe à la largeur du texte plus les marges, pas ailleurs")
    void le_seuil_est_celui_du_texte_plus_les_marges() throws InterruptedException {
        // Les comparaisons de `LisibiliteCapture` survivaient à PIT parce que les cas précédents sont
        // LOIN du seuil (140 px et 420 px) : décaler une borne ou un signe n'y change aucun verdict.
        //
        // Plutôt que de viser un pixel - fragile, les polices de la CI rendant ~7 % plus large - ce cas
        // **cherche** la largeur de bascule par dichotomie, puis la confronte à ce qu'elle doit valoir :
        // la largeur du texte, plus les marges internes du champ. Une addition mise à la place de la
        // soustraction déplacerait ce point de deux fois les marges ; mesurer une police par défaut au
        // lieu de celle du champ le déplacerait bien davantage.
        AtomicReference<double[]> mesures = new AtomicReference<>();
        CountDownLatch fini = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                double basse = 40;
                double haute = 1400;
                // Dichotomie : on cherche la plus petite largeur qui NE fait PAS échouer la capture.
                for (int i = 0; i < 24; i++) {
                    double milieu = (basse + haute) / 2;
                    if (echoue(sceneAvecChampGrandePolice(milieu))) {
                        basse = milieu;
                    } else {
                        haute = milieu;
                    }
                }
                // Ce que la bascule DOIT valoir : largeur du texte + marges internes.
                TextField temoin = champDeLargeur(haute);
                Text mesure = new Text(INVITE);
                mesure.setFont(temoin.getFont());
                double attendu = mesure.getLayoutBounds().getWidth()
                        + temoin.getInsets().getLeft()
                        + temoin.getInsets().getRight();
                mesures.set(new double[] {haute, attendu});
            } finally {
                fini.countDown();
            }
        });
        assertThat(fini.await(30, TimeUnit.SECONDS)).isTrue();

        double bascule = mesures.get()[0];
        double attendu = mesures.get()[1];
        assertThat(bascule)
                .as("bascule mesurée %.1f px, attendue %.1f px (texte + marges)", bascule, attendu)
                .isCloseTo(attendu, org.assertj.core.data.Offset.offset(3.0));
    }

    /// Comme [#sceneAvecChamp], mais le champ porte une police **nettement plus grande** que le défaut.
    ///
    /// Sans cet écart, mesurer l'invite avec la police du champ ou avec celle par défaut donne le même
    /// résultat en headless - les deux sont la même - et la ligne qui applique la police du champ
    /// devient invisible : elle survit à sa suppression sans qu'aucun test ne bronche.
    private static Scene sceneAvecChampGrandePolice(double largeurChamp) {
        Scene scene = sceneAvecChamp(largeurChamp);
        TextField champ = (TextField)
                ((javafx.scene.layout.VBox) scene.getRoot()).getChildren().get(0);
        champ.setFont(javafx.scene.text.Font.font(champ.getFont().getFamily(), 28));
        return scene;
    }

    /// Vrai si la capture de `scene` est refusée. Sur le fil JavaFX, sans écrire de fichier.
    private static boolean echoue(Scene scene) {
        // Sans passe de mise en page, `champ.getWidth()` vaut 0 et le garde ne mesure rien : la scène
        // paraîtrait saine à toute largeur. Les autres cas passent par `enregistrerPng`, qui monte un
        // Stage et provoque cette passe ; ici on la déclenche à la main.
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        try {
            LisibiliteCapture.refuserToutTexteIllisible(scene);
            return false;
        } catch (IllegalStateException _) {
            return true;
        }
    }

    /// Un champ **monté dans une scène** (donc mesuré) portant l'invite, à la largeur voulue.
    private static TextField champDeLargeur(double largeur) {
        Scene scene = sceneAvecChampGrandePolice(largeur);
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        return (TextField)
                ((javafx.scene.layout.VBox) scene.getRoot()).getChildren().get(0);
    }

    @Test
    @DisplayName("#3170 : un champ assez large passe, le contrôle ne crie pas sur un rendu sain")
    void invite_entiere_passe(@TempDir Path dossier) throws InterruptedException {
        // Sans ce second cas, le contrôle pourrait refuser TOUTE invite et le premier resterait vert :
        // un garde qui refuse tout ne garde rien, il bloque.
        Path fichier = dossier.resolve("apercu.png");

        Throwable refus = executerSurLeFilFx(() -> ApercuFx.enregistrerPng(sceneAvecChamp(420), fichier));

        assertThat(refus).isNull();
        assertThat(fichier).exists();
    }
}
