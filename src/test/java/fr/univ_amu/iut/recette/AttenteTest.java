package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Ce que doit faire une attente, et ce qui la distingue d'une respiration de tournage.
///
/// `Respiration` ne s'arrête que si l'on filme, et c'est le bon geste pour son emploi. Employée entre
/// un geste et une assertion, elle ne tient rien : le banc réussit parce que le travail a **le plus
/// souvent** fini avant, pas parce que quelque chose le garantit (#4694).
// Le toolkit JavaFX est amorce parce que deux temoins lisent leur predicat SUR son fil : sans lui,
// `asyncFx` leve « Toolkit not initialized » avant meme d'evaluer quoi que ce soit.
@ExtendWith(ApplicationExtension.class)
@DisplayName("Attente : elle attend, et elle dit ce qu'elle a vu")
class AttenteTest {

    @Test
    @DisplayName("elle rend la main dès que la condition devient vraie, sans dormir le délai entier")
    void elle_rend_la_main_des_que_c_est_vrai() {
        AtomicInteger lectures = new AtomicInteger();

        long avant = System.nanoTime();
        Attente.que(() -> lectures.incrementAndGet() >= 3, "trois lectures");
        long duree = (System.nanoTime() - avant) / 1_000_000;

        // La condition est relue jusqu'a devenir vraie : c'est ce qui distingue une attente d'une
        // pause. Une implementation qui dormirait le delai entier passerait la seconde assertion
        // mais pas celle-ci.
        assertThat(lectures.get()).isGreaterThanOrEqualTo(3);
        assertThat(duree).isLessThan(2_000L);
    }

    @Test
    @DisplayName("elle expire en DISANT ce qu'elle attendait, au lieu d'un échec muet")
    void elle_dit_ce_qu_elle_attendait() {
        // Sans ce message, l'echec arrive plus tard sur l'assertion du banc, qui accuse le code alors
        // que c'est la mise en place qui n'a pas eu lieu. C'est ce que #4504 avait deja appris sur le
        // Stage partage, et qui vivait en prive dans AppTest.
        assertThatThrownBy(() -> Attente.que(() -> false, "que les compteurs se remplissent", 200))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("que les compteurs se remplissent")
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("elle sait lire le prédicat SUR LE FIL JavaFX, le graphe de scène n'y étant pas partageable")
    void elle_lit_sur_le_fil_javafx() {
        AtomicReference<String> filLu = new AtomicReference<>();

        // `waitFor` rappelle le predicat depuis le fil du TEST. Un predicat qui touche la scene doit
        // etre lu sur le fil JavaFX, sans quoi il lit un graphe qu'un autre fil est en train
        // d'ecrire. #4408 l'avait mesure et son remede vivait en prive dans ScenarioAccueilTest.
        Attente.queSurLeFil(
                () -> {
                    filLu.set(Thread.currentThread().getName());
                    return true;
                },
                "que le prédicat soit lu sur le fil JavaFX");

        assertThat(filLu.get()).contains("JavaFX Application Thread");
    }

    @Test
    @DisplayName("sur le fil aussi, elle expire en disant ce qu'elle attendait")
    void sur_le_fil_elle_dit_ce_qu_elle_attendait() {
        assertThatThrownBy(() -> Attente.queSurLeFil(() -> false, "que l'écran s'ouvre", 200))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("que l'écran s'ouvre");
    }

    @Test
    @DisplayName("une condition déjà vraie ne coûte aucune attente")
    void deja_vraie_ne_coute_rien() {
        long avant = System.nanoTime();
        Attente.que(() -> true, "rien à attendre");

        assertThat((System.nanoTime() - avant) / 1_000_000).isLessThan(500L);
    }
}
