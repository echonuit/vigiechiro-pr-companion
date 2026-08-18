package fr.univ_amu.iut.commun.view;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Le **filet global** : journalise un incident, puis tente de le montrer à l'utilisateur (#795, #1523).
///
/// ## Pourquoi cette pièce existe, plutôt qu'une lambda dans `App`
///
/// Parce qu'elle a bouclé sur elle-même en production (#3700), et qu'une lambda posée dans `start` ne
/// s'éprouve pas. Le scénario, relevé sur une exécution réelle :
///
/// | | |
/// |---|---|
/// | 1 | un `target/classes` périmé fait échouer le chargement d'un écran, avec un message **juste**, qui nomme la
/// cause et le remède |
/// | 2 | le filet l'attrape et demande l'affichage d'une alerte |
/// | 3 | habiller cette alerte lit une feuille de style **absente pour la même raison** : `NullPointerException` |
/// | 4 | cette exception-là est **non capturée sur le fil FX**, donc le filet la reprend |
/// | 5 | retour à l'étape 2 - **16 217 fois** |
///
/// ⚠️ L'utilisateur ne voyait **aucune** alerte : chacune mourait avant d'être affichée. Le dispositif
/// censé rendre l'incident visible le rendait **invisible**, et noyait le seul message utile sous quatre
/// fichiers de journal.
///
/// ## Les deux règles qui en découlent
///
/// **Un échec pendant le signalement ne se signale pas par le même chemin.** Il est journalisé, et là
/// s'arrête : sinon on rejoue exactement ce qui vient d'échouer.
///
/// **Un signalement à la fois.** Le drapeau évite qu'une rafale d'incidents empile autant de fenêtres
/// modales - chacune bloquante - devant un utilisateur qui n'en fermera jamais la fin.
public final class SignalementIncident implements Thread.UncaughtExceptionHandler {

    private final Logger journal;
    private final Consumer<Runnable> differe;
    private final Consumer<Throwable> montrer;
    private final AtomicBoolean enCours = new AtomicBoolean();

    /// Au-delà, on tronque : voir `descriptionDeSecours`.
    private static final int PROFONDEUR_MAXIMALE = 12;

    /// @param journal où la trace est écrite, **toujours**, y compris quand l'affichage renonce
    /// @param differe ce qui porte l'affichage sur le fil de l'interface (`Platform::runLater`)
    /// @param montrer l'affichage lui-même, injecté pour que cette classe s'éprouve sans JavaFX
    public SignalementIncident(Logger journal, Consumer<Runnable> differe, Consumer<Throwable> montrer) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.differe = Objects.requireNonNull(differe, "differe");
        this.montrer = Objects.requireNonNull(montrer, "montrer");
    }

    @Override
    public void uncaughtException(Thread fil, Throwable erreur) {
        journaliser(fil, erreur);
        if (!enCours.compareAndSet(false, true)) {
            return;
        }
        differe.accept(() -> {
            try {
                montrer.accept(erreur);
            } catch (RuntimeException echecDAffichage) {
                // ⚠️ Journalisé, et **pas** relancé : relancer ferait reprendre ce même filet, qui
                // redemanderait le même affichage, qui échouerait pour la même raison. C'est la boucle
                // de #3700. Deux lignes dans le journal valent mieux que seize mille.
                journal.log(
                        Level.SEVERE,
                        echecDAffichage,
                        () -> "L'alerte d'incident n'a pas pu s'afficher : l'incident ci-dessus reste"
                                + " consultable ici, mais l'utilisateur n'en a rien vu");
            } finally {
                enCours.set(false);
            }
        });
    }

    /// Écrit l'incident, **sans faire confiance à celui qui le raconte**.
    ///
    /// ⚠️ Le message d'une exception peut lever à la lecture, et c'est le cas courant ici : sous
    /// Java 25, `ProvisionException.getMessage()` cherche les numéros de ligne, lit du bytecode
    /// major 69 avec l'ASM de Guice 7.0.0, et rend `IllegalArgumentException` (#3956). Le formateur
    /// du journal appelle ce message ; la panne remonte donc **dans le filet**, qui est la dernière
    /// ligne : s'il lève à son tour, plus rien ne rapporte rien.
    ///
    /// Le chemin riche est gardé - il porte la pile entière et sert dans tous les autres cas. Quand
    /// il échoue, on retombe sur une description construite à la main, qui lit chaque cause
    /// défensivement. On perd la pile complète ; on garde **le défaut**, qui était sinon à trois
    /// « Caused by » d'un message parlant d'ASM.
    private void journaliser(Thread fil, Throwable erreur) {
        try {
            journal.log(Level.SEVERE, erreur, () -> "Exception non capturée sur le fil « " + fil.getName() + " »");
        } catch (RuntimeException | LinkageError formatageImpossible) {
            journal.log(Level.SEVERE, () -> descriptionDeSecours(fil, erreur, formatageImpossible));
        }
    }

    /// La chaîne des causes, lue sans le formateur qui vient d'échouer.
    ///
    /// Le parcours est **borné** : une chaîne de causes n'est pas garantie acyclique une fois que
    /// des `initCause` s'en mêlent, et un filet qui bouclerait ici referait #3700 par un autre
    /// chemin.
    static String descriptionDeSecours(Thread fil, Throwable erreur, Throwable panneDuFormatage) {
        StringBuilder texte = new StringBuilder(512);
        texte.append("Exception non capturée sur le fil « ")
                .append(fil.getName())
                .append(" ». Son rapport n'a pas pu être formaté (")
                .append(nomEtMessage(panneDuFormatage))
                .append("). La chaîne des causes, lue sans le formateur :");

        Throwable courant = erreur;
        for (int rang = 0; courant != null && rang < PROFONDEUR_MAXIMALE; rang++) {
            texte.append(System.lineSeparator())
                    .append("    ")
                    .append(rang == 0 ? "→ " : "causé par ")
                    .append(nomEtMessage(courant));
            StackTraceElement[] pile = courant.getStackTrace();
            if (pile.length > 0) {
                texte.append(System.lineSeparator()).append("        à ").append(pile[0]);
            }
            courant = courant.getCause();
        }
        if (courant != null) {
            texte.append(System.lineSeparator())
                    .append("    (chaîne tronquée à ")
                    .append(PROFONDEUR_MAXIMALE)
                    .append(" causes)");
        }
        return texte.toString();
    }

    /// Le nom et le message d'une exception, le message étant lu **défensivement** : c'est
    /// précisément lui qui peut lever.
    private static String nomEtMessage(Throwable erreur) {
        String message;
        try {
            message = erreur.getMessage();
        } catch (RuntimeException | LinkageError illisible) {
            message = "message illisible (" + illisible.getClass().getName() + ")";
        }
        return message == null ? erreur.getClass().getName() : erreur.getClass().getName() + " : " + message;
    }
}
