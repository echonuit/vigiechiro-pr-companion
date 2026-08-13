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
        journal.log(Level.SEVERE, erreur, () -> "Exception non capturée sur le fil « " + fil.getName() + " »");
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
}
