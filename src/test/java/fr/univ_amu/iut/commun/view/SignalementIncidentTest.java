package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le filet global doit rendre l'incident **visible**, et jamais se rejouer sur son propre échec (#3700).
class SignalementIncidentTest {

    /// Exécute l'affichage tout de suite : en production c'est `Platform.runLater`, ici c'est
    /// synchrone, sans quoi le test n'observerait rien.
    private static final java.util.function.Consumer<Runnable> IMMEDIAT = Runnable::run;

    @Test
    @DisplayName("#3700 : un affichage qui échoue est journalisé, et ne relance PAS le filet")
    void un_affichage_qui_echoue_ne_reboucle_pas() {
        List<LogRecord> traces = new ArrayList<>();
        Logger journal = journalDeTest(traces);
        int[] tentatives = {0};
        SignalementIncident filet = new SignalementIncident(journal, IMMEDIAT, erreur -> {
            tentatives[0]++;
            // Le cas réel : habiller l'alerte lit une feuille absente pour la raison même qu'on rapporte.
            throw new IllegalStateException("Feuille de style introuvable sur le classpath");
        });

        assertThatCode(() -> filet.uncaughtException(Thread.currentThread(), new IllegalStateException("écran KO")))
                .as("relancer l'échec d'affichage ferait reprendre ce filet par le gestionnaire par"
                        + " défaut, qui redemanderait le même affichage : c'est la boucle de #3700")
                .doesNotThrowAnyException();

        assertThat(tentatives[0]).as("une tentative, pas une rafale").isEqualTo(1);
        assertThat(traces)
                .as("les DEUX faits sont consignés : l'incident d'origine, et le fait que"
                        + " l'utilisateur n'en a rien vu")
                .hasSize(2);
        assertThat(traces.getFirst().getThrown()).hasMessage("écran KO");
        assertThat(traces.getLast().getMessage()).contains("n'a pas pu s'afficher");
    }

    @Test
    @DisplayName("#3700 : un incident survenu PENDANT un signalement ne rouvre pas une seconde alerte")
    void un_incident_pendant_le_signalement_ne_sempile_pas() {
        List<LogRecord> traces = new ArrayList<>();
        Logger journal = journalDeTest(traces);
        int[] affichages = {0};
        SignalementIncident[] filet = new SignalementIncident[1];
        filet[0] = new SignalementIncident(journal, IMMEDIAT, erreur -> {
            affichages[0]++;
            // Une modale est bloquante : pendant qu'elle est là, un autre incident survient.
            filet[0].uncaughtException(Thread.currentThread(), new IllegalStateException("second incident"));
        });

        filet[0].uncaughtException(Thread.currentThread(), new IllegalStateException("premier incident"));

        assertThat(affichages[0])
                .as("empiler des fenêtres modales devant quelqu'un qui n'en fermera jamais la fin n'est"
                        + " pas l'informer : le second incident est journalisé, pas affiché")
                .isEqualTo(1);
        assertThat(traces).as("les deux incidents restent consultables").hasSize(2);
    }

    @Test
    @DisplayName("#3700 : le filet redevient disponible pour l'incident suivant")
    void le_filet_se_rearme() {
        List<LogRecord> traces = new ArrayList<>();
        int[] affichages = {0};
        SignalementIncident filet = new SignalementIncident(journalDeTest(traces), IMMEDIAT, erreur -> affichages[0]++);

        filet.uncaughtException(Thread.currentThread(), new IllegalStateException("premier"));
        filet.uncaughtException(Thread.currentThread(), new IllegalStateException("second"));

        // Sans cette remise à zéro, le drapeau anti-rafale deviendrait un bâillon définitif : le premier
        // incident de la session serait le seul jamais montré.
        assertThat(affichages[0]).isEqualTo(2);
    }

    private static Logger journalDeTest(List<LogRecord> traces) {
        Logger journal = Logger.getLogger("test-" + traces.hashCode());
        journal.setUseParentHandlers(false);
        journal.addHandler(new Handler() {
            @Override
            public void publish(LogRecord enregistrement) {
                traces.add(enregistrement);
            }

            @Override
            public void flush() {
                // Rien à vider : les enregistrements sont conservés en mémoire.
            }

            @Override
            public void close() {
                // Rien à fermer.
            }
        });
        journal.setLevel(Level.ALL);
        return journal;
    }
}
