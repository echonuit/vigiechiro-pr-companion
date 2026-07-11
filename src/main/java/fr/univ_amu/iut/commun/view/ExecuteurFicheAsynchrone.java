package fr.univ_amu.iut.commun.view;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/// Exécution **asynchrone** (production) : la résolution (réseau GBIF) tourne hors du fil JavaFX via le
/// pool commun, puis l'ouverture est reprogrammée sur le fil JavaFX avec [Platform#runLater]. L'UI reste
/// réactive pendant l'appel réseau.
public final class ExecuteurFicheAsynchrone implements ExecuteurFiche {

    @Override
    public void resoudrePuisOuvrir(Supplier<String> resolution, Consumer<String> ouverture) {
        CompletableFuture.supplyAsync(resolution).thenAccept(url -> Platform.runLater(() -> ouverture.accept(url)));
    }
}
