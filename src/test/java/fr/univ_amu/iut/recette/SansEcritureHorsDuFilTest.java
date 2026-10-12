package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.recette.EcrituresHorsDuFil.Population;
import fr.univ_amu.iut.recette.EcrituresHorsDuFil.Releve;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// L'auto-test de [SansEcritureHorsDuFil] : il rougit sur ce qu'il doit voir, et reste vert sur ce
/// qu'il doit laisser passer (#6142).
///
/// La classe ne porte pas l'extension : chaque cas ouvre et ferme le relevé lui-même, pour lire ce
/// qui a été vu au lieu de seulement rougir.
@ExtendWith(ApplicationExtension.class)
class SansEcritureHorsDuFilTest {

    private static final long DELAI_MS = 10_000L;
    private static final String FILM = "recette.film";

    private TextField temoin;
    private Label libelle;

    @Start
    void start(Stage stage) {
        temoin = new TextField("avant");
        temoin.setId("temoin");
        libelle = new Label("avant");
        libelle.setId("libelle");
        FenetreAjustable.poserHabillee(stage, new VBox(temoin, libelle), 320, 200);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("une écriture directe depuis un autre fil est relevée, avec le nœud, la propriété et le fil")
    void une_ecriture_directe_hors_du_fil_est_relevee() {
        EcrituresHorsDuFil.ouvrir();
        surUnFilEtranger("tache-de-fond", () -> temoin.setText("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();

        assertThat(proprietesDuTemoin(releves)).contains("text");
        assertThat(releves).first().satisfies(releve -> {
            assertThat(releve.noeud()).isEqualTo("TextField#temoin");
            assertThat(releve.fil()).contains("tache-de-fond");
            assertThat(releve.parLeBanc()).isFalse();
            assertThat(releve.site()).contains("SansEcritureHorsDuFilTest");
        });
    }

    @Test
    @DisplayName("une écriture qui atteint le nœud par une liaison est relevée sur le nœud")
    void une_ecriture_par_liaison_est_relevee() {
        StringProperty modele = new SimpleStringProperty("avant");
        surLeFil(() -> temoin.textProperty().bind(modele));

        EcrituresHorsDuFil.ouvrir();
        surUnFilEtranger("tache-de-fond", () -> modele.set("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();
        surLeFil(() -> temoin.textProperty().unbind());

        assertThat(releves).extracting(Releve::noeud).contains("TextField#temoin");
        assertThat(releves).extracting(Releve::propriete).contains("text");
    }

    @Test
    @DisplayName("une écriture qui atteint le nœud par un écouteur est relevée sur le nœud")
    void une_ecriture_par_ecouteur_est_relevee() {
        StringProperty modele = new SimpleStringProperty("avant");
        modele.addListener((_, _, apres) -> temoin.setText(apres));

        EcrituresHorsDuFil.ouvrir();
        surUnFilEtranger("tache-de-fond", () -> modele.set("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();

        assertThat(releves).extracting(Releve::noeud).contains("TextField#temoin");
    }

    @Test
    @DisplayName("un libellé écrit hors du fil est déjà refusé par JavaFX, et l'écriture est relevée quand même")
    void un_libelle_ecrit_hors_du_fil_est_refuse_par_javafx_et_releve() {
        EcrituresHorsDuFil.ouvrir();
        Throwable leve = surUnFilEtranger("tache-de-fond", () -> libelle.setText("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();

        assertThat(leve)
                .as("l'habillage d'un libellé refait ses enfants, et la liste des enfants vérifie le fil")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not on FX application thread");
        assertThat(releves).extracting(Releve::noeud).contains("Label#libelle");
    }

    @Test
    @DisplayName("la même écriture faite sur le fil JavaFX n'est pas relevée, et elle est comptée")
    void une_ecriture_sur_le_fil_n_est_pas_relevee() {
        EcrituresHorsDuFil.ouvrir();
        surLeFil(() -> temoin.setText("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();
        Population population = EcrituresHorsDuFil.population();

        assertThat(releves).isEmpty();
        assertThat(population.surLeFil())
                .as("un zéro ne vaut que si l'écriture a été regardée")
                .isPositive();
    }

    @Test
    @DisplayName("un nœud pas encore attaché à une scène s'écrit hors du fil sans rien faire relever")
    void un_noeud_libre_s_ecrit_hors_du_fil() {
        Label libre = new Label("avant");

        EcrituresHorsDuFil.ouvrir();
        surUnFilEtranger("tache-de-fond", () -> libre.setText("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();
        Population population = EcrituresHorsDuFil.population();

        assertThat(releves).isEmpty();
        assertThat(population.surUnNoeudLibre())
                .as("l'écriture a été vue et classée, pas manquée")
                .isPositive();
    }

    @Test
    @DisplayName("une propriété de modèle de vue liée à aucun nœud s'écrit hors du fil sans rien faire relever")
    void une_propriete_sans_noeud_s_ecrit_hors_du_fil() {
        StringProperty modele = new SimpleStringProperty("avant");

        EcrituresHorsDuFil.ouvrir();
        surUnFilEtranger("tache-de-fond", () -> modele.set("après"));
        List<Releve> releves = EcrituresHorsDuFil.fermer();
        Population population = EcrituresHorsDuFil.population();

        assertThat(releves).isEmpty();
        assertThat(population.horsDuFil()).isPositive();
        assertThat(population.filsEtrangers()).containsKey("tache-de-fond");
    }

    @Test
    @DisplayName("l'exécuteur synchrone joue la tâche sur le fil du geste : rien n'est relevé")
    void l_executeur_synchrone_ne_fait_rien_relever() {
        ExecuteurTache synchrone = new ExecuteurTacheSynchrone();

        EcrituresHorsDuFil.ouvrir();
        surLeFil(() -> synchrone.executer(
                () -> {
                    temoin.setText("dans la tâche");
                    return "rendu";
                },
                temoin::setText,
                _ -> temoin.setText("échec")));
        List<Releve> releves = EcrituresHorsDuFil.fermer();

        assertThat(releves).isEmpty();
    }

    @Test
    @DisplayName("l'exécuteur asynchrone : la tâche qui écrit le nœud est relevée sous le nom de son fil")
    void la_tache_asynchrone_qui_ecrit_est_relevee() {
        ExecuteurTache asynchrone = new ExecuteurTacheAsynchrone();
        CountDownLatch conclu = new CountDownLatch(1);

        CountDownLatch ecrit = new CountDownLatch(1);

        EcrituresHorsDuFil.ouvrir();
        filJavaFxAuRepos(() -> {
            asynchrone.executer(
                    () -> {
                        temoin.setText("dans la tâche");
                        ecrit.countDown();
                        return "rendu";
                    },
                    _ -> conclu.countDown(),
                    _ -> conclu.countDown());
            attendre(ecrit);
        });
        attendre(conclu);
        List<Releve> releves = EcrituresHorsDuFil.fermer();

        assertThat(proprietesDuTemoin(releves)).contains("text");
        assertThat(releves).first().satisfies(releve -> {
            assertThat(releve.noeud()).isEqualTo("TextField#temoin");
            assertThat(releve.fil()).contains("tache-occupation");
        });
    }

    @Test
    @DisplayName("l'exécuteur asynchrone : la tâche qui rend une valeur appliquée au retour ne fait rien relever")
    void la_tache_asynchrone_qui_rend_une_valeur_est_saine() {
        ExecuteurTache asynchrone = new ExecuteurTacheAsynchrone();
        CountDownLatch conclu = new CountDownLatch(1);

        EcrituresHorsDuFil.ouvrir();
        asynchrone.executer(
                () -> "rendu",
                rendu -> {
                    temoin.setText(rendu);
                    conclu.countDown();
                },
                _ -> conclu.countDown());
        attendre(conclu);
        WaitForAsyncUtils.waitForFxEvents();
        List<Releve> releves = EcrituresHorsDuFil.fermer();

        assertThat(releves).isEmpty();
        assertThat(surLeFilRendant()).isEqualTo("rendu");
    }

    @Test
    @DisplayName("l'extension rougit en nommant le nœud, la propriété, le fil et le site")
    void l_extension_rougit_en_nommant_l_ecriture() {
        SansEcritureHorsDuFil extension = new SansEcritureHorsDuFil();
        ExtensionContext contexte = contexte();

        extension.beforeEach(contexte);
        surUnFilEtranger("tache-de-fond", () -> temoin.setText("après"));

        assertThatThrownBy(() -> extension.afterEach(contexte))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("TextField#temoin")
                .hasMessageMatching("(?s).*propriétés « [^»]*\\btext\\b[^»]* ».*")
                .hasMessageContaining("tache-de-fond")
                .hasMessageContaining("SansEcritureHorsDuFilTest");
    }

    @Test
    @DisplayName("l'extension ne rougit pas d'une écriture faite par le fil du banc, mais elle la relève")
    void l_extension_ne_rougit_pas_du_fil_du_banc() {
        SansEcritureHorsDuFil extension = new SansEcritureHorsDuFil();
        ExtensionContext contexte = contexte();

        extension.beforeEach(contexte);
        temoin.setText("écrit par le banc");

        assertThatCode(() -> extension.afterEach(contexte)).doesNotThrowAnyException();

        EcrituresHorsDuFil.ouvrir();
        temoin.setText("écrit encore par le banc");
        assertThat(EcrituresHorsDuFil.fermer())
                .isNotEmpty()
                .allSatisfy(releve -> assertThat(releve.parLeBanc()).isTrue());
    }

    @Test
    @DisplayName("pendant un tournage l'extension ne relève rien et ne rougit pas, même d'une écriture hors du fil")
    void pendant_un_tournage_l_extension_ne_releve_rien() {
        SansEcritureHorsDuFil extension = new SansEcritureHorsDuFil();
        ExtensionContext contexte = contexte();
        String avant = System.setProperty(FILM, "");
        try {
            extension.beforeEach(contexte);
            surUnFilEtranger("tache-de-fond", () -> temoin.setText("après"));

            assertThatCode(() -> extension.afterEach(contexte)).doesNotThrowAnyException();
        } finally {
            if (avant == null) {
                System.clearProperty(FILM);
            } else {
                System.setProperty(FILM, avant);
            }
        }
    }

    private static List<String> proprietesDuTemoin(List<Releve> releves) {
        return releves.stream()
                .filter(releve -> "TextField#temoin".equals(releve.noeud()))
                .map(Releve::propriete)
                .toList();
    }

    private static ExtensionContext contexte() {
        ExtensionContext contexte = mock(ExtensionContext.class);
        when(contexte.getRequiredTestClass()).thenAnswer(_ -> SansEcritureHorsDuFilTest.class);
        when(contexte.getDisplayName()).thenReturn("un cas de l'auto-test");
        return contexte;
    }

    private String surLeFilRendant() {
        return Attente.surLeFil(() -> temoin.getText(), "relire le témoin", DELAI_MS);
    }

    private static void surLeFil(Runnable action) {
        Attente.surLeFil(action, "agir sur le fil JavaFX", DELAI_MS);
    }

    /// Joue `action` sur un fil de plateforme nommé et rend ce qu'elle a levé, s'il y a lieu.
    ///
    /// Le fil porte son propre gestionnaire : JavaFX donne à celui du fil courant ce que lève un
    /// écouteur, et à défaut c'est celui de TestFX qui le garde et le relance au cas suivant.
    ///
    /// Le fil JavaFX est tenu au repos pendant l'écriture : sans quoi l'écriture hors du fil que ce
    /// cas fabrique exprès court contre la mise en page du pulse, et le pulse lève parfois la
    /// `ConcurrentModificationException` de #6138 sur le chemin du curseur. Le défaut que l'espion
    /// relève ferait alors rougir son propre auto-test, une fois sur plusieurs.
    private static Throwable surUnFilEtranger(String nom, Runnable action) {
        AtomicReference<Throwable> leve = new AtomicReference<>();
        filJavaFxAuRepos(() -> {
            Thread fil = Thread.ofPlatform()
                    .name(nom)
                    .uncaughtExceptionHandler((_, rejet) -> leve.compareAndSet(null, rejet))
                    .start(action);
            try {
                fil.join(DELAI_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrompu en attendant le fil étranger", e);
            }
        });
        return leve.get();
    }

    /// Joue `pendant` depuis le fil du test, le fil JavaFX garé dans un `runLater` jusqu'à la fin.
    private static void filJavaFxAuRepos(Runnable pendant) {
        CountDownLatch gare = new CountDownLatch(1);
        CountDownLatch libere = new CountDownLatch(1);
        Platform.runLater(() -> {
            gare.countDown();
            try {
                libere.await(DELAI_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        attendre(gare);
        try {
            pendant.run();
        } finally {
            libere.countDown();
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void attendre(CountDownLatch conclu) {
        try {
            assertThat(conclu.await(DELAI_MS, TimeUnit.MILLISECONDS))
                    .as("l'attente doit aboutir dans le délai")
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrompu en attendant la tâche", e);
        }
    }
}
