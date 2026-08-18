package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
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
    @DisplayName("#3956 : un incident dont le MESSAGE explose laisse quand même sa cause au journal")
    void un_message_empoisonne_laisse_quand_meme_sa_cause() {
        List<String> rendus = new ArrayList<>();
        Logger journal = journalQuiFormate(rendus);
        SignalementIncident filet = new SignalementIncident(journal, IMMEDIAT, erreur -> {});

        Throwable incident =
                new MessageEmpoisonne(new IllegalStateException("Le dossier de travail est en lecture seule"));

        assertThatCode(() -> filet.uncaughtException(Thread.currentThread(), incident))
                .as("le filet est la DERNIÈRE ligne : s'il lève à son tour, plus rien ne rapporte rien")
                .doesNotThrowAnyException();

        assertThat(rendus)
                .as("la cause réelle doit atteindre le journal, sinon le lecteur cherchera du côté"
                        + " d'ASM pendant que son défaut est à trois « Caused by » de là")
                .anySatisfy(rendu -> assertThat(rendu).contains("Le dossier de travail est en lecture seule"));

        assertThat(rendus)
                .as("et il doit dire POURQUOI la pile est courte : sans cela, un lecteur croirait le"
                        + " rapport tronqué par accident")
                .anySatisfy(rendu -> assertThat(rendu)
                        .contains("n'a pas pu être formaté")
                        .contains("Unsupported class file major version 69"));
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

    /// Un journal qui FORMATE, comme un vrai. Le journal de test ordinaire se contente de retenir
    /// l'enregistrement : il ne touche jamais au message de l'exception, donc il ne peut pas voir un
    /// message qui explose - c'est le formatage qui le lit.
    private static Logger journalQuiFormate(List<String> rendus) {
        Logger journal = Logger.getLogger("filet-qui-formate-" + System.nanoTime());
        journal.setUseParentHandlers(false);
        SimpleFormatter formateur = new SimpleFormatter();
        journal.addHandler(new Handler() {
            @Override
            public void publish(LogRecord enregistrement) {
                rendus.add(formateur.format(enregistrement));
            }

            @Override
            public void flush() {
                // Rien à vider.
            }

            @Override
            public void close() {
                // Rien à fermer.
            }
        });
        journal.setLevel(Level.ALL);
        return journal;
    }

    /// Ce que Guice 7.0.0 rend sous Java 25 : le message n'est pas absent, il **explose**. Guice
    /// cherche les numéros de ligne pour formater son rapport, lit du bytecode major 69 avec son ASM
    /// embarqué, et lève. Le vrai défaut est dessous, et n'atteint jamais le journal.
    private static final class MessageEmpoisonne extends RuntimeException {
        private static final long serialVersionUID = 1L;

        MessageEmpoisonne(Throwable cause) {
            super(null, cause);
        }

        @Override
        public String getMessage() {
            throw new IllegalArgumentException("Unsupported class file major version 69");
        }
    }
}
