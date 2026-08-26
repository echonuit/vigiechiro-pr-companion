package fr.univ_amu.iut.commun.outils;

import fr.nedjar.vigiechiro.audio.AudioView;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/// Attente du chargement asynchrone d'une [AudioView], pour les **outils de capture** d'écran.
///
/// L'`AudioView` lit son WAV sur un **thread de fond** (`javax.sound.sampled`) puis publie la durée
/// et le spectrogramme via `Platform.runLater`. Pour capturer un spectrogramme **peint** (et non un
/// cadre vide), il faut attendre la fin de ce chargement, mais **sans bloquer le thread JavaFX**,
/// sinon le `runLater` de publication ne s'exécuterait jamais (interblocage).
///
/// On utilise donc une **boucle d'évènements imbriquée** ([Platform#enterNestedEventLoop]) qui
/// continue de pomper les évènements FX : le chargement aboutit pendant l'attente. Un secours de 8 s
/// sort de la boucle si l'audio ne se charge jamais (WAV illisible), pour ne pas bloquer.
///
/// **À n'appeler que pour la DERNIÈRE capture d'un outil** : la boucle imbriquée déstabilise la
/// Headless Platform JavaFX 26 (ré-entrée du toolkit via `NestedRunnableProcessor`) et fait crasher
/// un `new Stage()` rendu **ultérieurement** dans le même JVM. Tant que plus aucune scène n'est
/// rendue après l'attente, c'est sans danger (cf. `CaptureValidation`, `CaptureBibliotheque`).
public final class AttenteAudio {

    private AttenteAudio() {}

    /// Attend que `audio` ait chargé son WAV (durée &gt; 0 → samples lus, spectrogramme calculé) et
    /// l'ait peint, en pompant les évènements FX. Retourne immédiatement si le chargement est déjà
    /// terminé. À appeler **sur le thread JavaFX**.
    public static void attendreChargement(AudioView audio) {
        if (audio.getDuration() > 0) {
            return;
        }
        Object cle = new Object();
        AtomicBoolean sorti = new AtomicBoolean(false);
        Runnable sortir = () -> {
            if (sorti.compareAndSet(false, true)) {
                Platform.exitNestedEventLoop(cle, null);
            }
        };
        audio.durationProperty().addListener((obs, ancien, nouveau) -> {
            if (nouveau.doubleValue() > 0) {
                sortirApres(sortir, 700); // laisse quelques pulses pour peindre le spectrogramme
            }
        });
        sortirApres(sortir, 8000); // secours anti-blocage
        Platform.enterNestedEventLoop(cle);
    }

    /// Planifie `sortir` sur le thread JavaFX après `delaiMs` (depuis un thread démon). Exécuté
    /// pendant la boucle d'évènements imbriquée, il en provoque la sortie.
    private static void sortirApres(Runnable sortir, long delaiMs) {
        Thread minuterie = new Thread(() -> {
            try {
                Thread.sleep(delaiMs);
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
                return;
            }
            Platform.runLater(sortir);
        });
        minuterie.setDaemon(true);
        minuterie.start();
    }
}
