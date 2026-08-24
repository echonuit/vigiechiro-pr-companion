package fr.univ_amu.iut.commun.view;

import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableView;

/// Mémoire **de session** des écrans à barre de filtres (#484, généralisée en #3098) : reprendre le
/// travail là où on l'a laissé. Les controllers étant recréés à chaque ouverture, cet état vit dans
/// un singleton Guice, rangé par **clé d'écran** - le tri de l'inventaire ne s'applique pas au
/// tableau des passages.
///
/// **Deux entrées, parce que les écrans n'ont pas tous une table** : Espèces & observations en a
/// trois, Activité de la nuit aucune. Les filtres valent pour tous, le tri se mémorise par table,
/// d'où [#installer] et [#memoriserTri]. **Une restauration peut échouer en partie** - une valeur
/// mémorisée peut avoir disparu -, et `compteRendu` reçoit ce qui n'a pas été replacé (#3093).
@Singleton
public class MemoireFiltres {

    /// Le tri mémorisé d'une table, par **en-tête** de colonne : clé stable, insensible à l'ordre des
    /// colonnes ou à un masquage.
    private record TriColonne(String enTete, SortType sens) {}

    /// Ce qu'un écran retient d'une visite à l'autre : ses filtres, et le tri de **chacune** de ses
    /// tables, repérée par son `fx:id`.
    private static final class Etat {
        private DescripteurFiltre filtres;
        private final Map<String, List<TriColonne>> triParTable = new LinkedHashMap<>();
    }

    private final Map<String, Etat> parEcran = new LinkedHashMap<>();

    /// Branche la mémoire des **filtres** de `ecran` : restaure immédiatement l'état mémorisé, puis le
    /// re-mémorise quand `ancrage` quitte la scène (fermeture / navigation).
    ///
    /// @param ecran clé de l'écran, la même que celle de ses vues sauvegardées
    /// @param ancrage n'importe quel nœud de l'écran : il sert d'horloge de sortie, pas de contenu
    /// @param compteRendu reçoit ce que la restauration n'a pas su replacer (#3093)
    public <T> void installer(
            String ecran,
            Node ancrage,
            GestionnaireFiltres<T> gestionnaireFiltres,
            Consumer<ResteDeRestauration> compteRendu) {
        Etat etat = parEcran.computeIfAbsent(ecran, cle -> new Etat());
        if (etat.filtres != null) {
            ResteDeRestauration reste = gestionnaireFiltres.restaurer(etat.filtres);
            if (!reste.estVide()) {
                compteRendu.accept(reste);
            }
        }
        ancrage.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                etat.filtres = gestionnaireFiltres.decrire();
            }
        });
    }

    /// Branche la mémoire du **tri** d'une table. Un écran en appelle autant que de tables ; un écran
    /// sans table n'en appelle aucune.
    ///
    /// @param table repérée par son `fx:id`, pour que trois tables d'un même écran ne se confondent pas
    public <T> void memoriserTri(String ecran, TableView<T> table) {
        Etat etat = parEcran.computeIfAbsent(ecran, cle -> new Etat());
        String cleTable = table.getId() == null ? "table" : table.getId();
        restaurerTri(table, etat.triParTable.getOrDefault(cleTable, List.of()));
        table.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                etat.triParTable.put(cleTable, triCourant(table));
            }
        });
    }

    /// Oublie l'état de `ecran` : le prochain retour repart à neuf.
    ///
    /// Appelé par « Tout effacer » (#3098). Sans cela, le bouton viderait les filtres **à l'écran** et
    /// la mémoire les remettrait à la visite suivante : le geste paraîtrait ne pas avoir pris.
    public void oublier(String ecran) {
        parEcran.remove(ecran);
    }

    private static <T> List<TriColonne> triCourant(TableView<T> table) {
        List<TriColonne> tri = new ArrayList<>();
        for (TableColumn<T, ?> colonne : table.getSortOrder()) {
            tri.add(new TriColonne(colonne.getText(), colonne.getSortType()));
        }
        return List.copyOf(tri);
    }

    /// Réapplique le tri mémorisé. Les colonnes **introuvables** (masquées depuis) sont ignorées : le
    /// tri se mémorise par en-tête, et un en-tête peut avoir disparu entre deux ouvertures.
    private static <T> void restaurerTri(TableView<T> table, List<TriColonne> memorise) {
        table.getSortOrder().clear();
        for (TriColonne colonneMemorisee : memorise) {
            colonneParEnTete(table, colonneMemorisee.enTete()).ifPresent(colonne -> {
                colonne.setSortType(colonneMemorisee.sens());
                table.getSortOrder().add(colonne);
            });
        }
    }

    private static <T> Optional<TableColumn<T, ?>> colonneParEnTete(TableView<T> table, String enTete) {
        return table.getColumns().stream()
                .filter(colonne -> enTete.equals(colonne.getText()))
                .findFirst();
    }
}
