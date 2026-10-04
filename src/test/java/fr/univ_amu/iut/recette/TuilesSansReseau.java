package fr.univ_amu.iut.recette;

import com.gluonhq.maps.tile.TileRetriever;
import java.util.concurrent.CompletableFuture;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/// Le fond de carte des bancs : une tuile blanche, servie sans réseau (#5789).
///
/// `GluonMapSpikeTest` déclare depuis #152 que le fond de tuiles est **best-effort** et qu'on ne
/// l'asserte jamais. Il ne l'était pas : à défaut de service, `TileRetrieverProvider.load()`
/// construit un `CachedOsmTileRetriever` qui interroge `tile.openstreetmap.org`, et son échec
/// remonte dans TestFX puis ressort à la prochaine attente du banc.
///
/// **Ce qu'il coûtait**, mesuré sans réseau sur deux classes : quatre erreurs, toutes enracinées
/// dans `Error tile.openstreetmap.org`, et sur des cas **différents** d'une exécution à l'autre -
/// l'échec s'attribue à l'attente qui tourne quand il remonte, jamais à la carte.
///
/// **Pourquoi une image blanche** plutôt qu'un futur jamais complété : le second remplacerait un
/// rouge réseau par une attente sans fin, ce qui est pire à lire. Et plutôt qu'un futur échoué, qui
/// reproduirait le défaut qu'on retire.
public final class TuilesSansReseau implements TileRetriever {

    /// Le côté d'une tuile OSM, et la taille que `MapView` suppose de ce qu'on lui rend.
    private static final int COTE = 256;

    /// Construite une seule fois : `MapView` demande des centaines de tuiles par écran, et en
    /// allouer une par appel ferait payer aux bancs ce que ce service existe pour leur épargner.
    private static volatile WritableImage blanche;

    @Override
    public CompletableFuture<Image> loadTile(int zoom, long i, long j) {
        return CompletableFuture.completedFuture(blanche());
    }

    /// La tuile, partagée. Construite à la demande et non au chargement de la classe : le
    /// `ServiceLoader` instancie ce service tôt, et `WritableImage` veut le runtime JavaFX amorcé.
    private static WritableImage blanche() {
        WritableImage vue = blanche;
        if (vue == null) {
            synchronized (TuilesSansReseau.class) {
                vue = blanche;
                if (vue == null) {
                    vue = new WritableImage(COTE, COTE);
                    blanche = vue;
                }
            }
        }
        return vue;
    }
}
