package fr.univ_amu.iut.audio.view;

import com.google.inject.Singleton;
import fr.univ_amu.iut.audio.view.GestionnaireFiltres.EtatFiltres;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.control.TableView;

/// Mémoire **de session** de la vue audio « Sons & validation » (#484) : reprendre la revue là où on l'a
/// laissée, sans tout re-régler. Conserve le **tri** de la table et l'**état des filtres** (recherche texte +
/// puces actives avec leurs valeurs) **entre deux ouvertures** de la vue. Le controller / ViewModel étant
/// **recréés à chaque ouverture** (VM non-singleton), cet état vit dans un **singleton de session** (Guice).
///
/// Le tri est mémorisé **par en-tête de colonne** (clé stable, insensible à l'ordre des colonnes ou à un
/// masquage), et réappliqué à l'ouverture ; les colonnes introuvables (ex. « Heure » masquée) sont ignorées.
/// Les filtres sont photographiés/restitués par [GestionnaireFiltres] (critères disparus du catalogue ignorés).
@Singleton
public class MemoireRevueAudio {

    private List<TriColonne> tri = List.of();
    private EtatFiltres filtres;

    /// Branche la mémoire sur `table` et son `gestionnaireFiltres` (peut être `null` : mémoire du tri seul) :
    /// **restaure** immédiatement le tri et les filtres mémorisés, puis les **re-mémorise** quand la vue quitte
    /// la scène (fermeture / navigation).
    public void installer(TableView<LigneObservationAudio> table, GestionnaireFiltres gestionnaireFiltres) {
        restaurerTri(table);
        if (gestionnaireFiltres != null && filtres != null) {
            gestionnaireFiltres.restaurer(filtres);
        }
        table.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                memoriserTri(table);
                if (gestionnaireFiltres != null) {
                    filtres = gestionnaireFiltres.capturer();
                }
            }
        });
    }

    private void memoriserTri(TableView<LigneObservationAudio> table) {
        tri = table.getSortOrder().stream()
                .map(colonne -> new TriColonne(colonne.getText(), colonne.getSortType()))
                .toList();
    }

    private void restaurerTri(TableView<LigneObservationAudio> table) {
        table.getSortOrder().clear();
        for (TriColonne memorise : tri) {
            colonneParEnTete(table, memorise.enTete()).ifPresent(colonne -> {
                colonne.setSortType(memorise.sens());
                table.getSortOrder().add(colonne);
            });
        }
    }

    private static Optional<TableColumn<LigneObservationAudio, ?>> colonneParEnTete(
            TableView<LigneObservationAudio> table, String enTete) {
        return table.getColumns().stream()
                .filter(colonne -> enTete.equals(colonne.getText()))
                .findFirst();
    }

    /// Une colonne triée mémorisée : son **en-tête** (clé stable) et le **sens** du tri.
    private record TriColonne(String enTete, SortType sens) {}
}
