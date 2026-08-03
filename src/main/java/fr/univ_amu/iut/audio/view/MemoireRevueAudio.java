package fr.univ_amu.iut.audio.view;

import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
/// Les filtres sont photographiés/restitués par [GestionnaireFiltres] ; ce qu'il n'a pas su replacer est
/// **rapporté** à l'écran hôte (#3093) au lieu d'être perdu en silence.
@Singleton
public class MemoireRevueAudio {

    private List<TriColonne> tri = List.of();
    private DescripteurFiltre filtres;

    /// Branche la mémoire sur `table` et son `gestionnaireFiltres` (peut être `null` : mémoire du tri seul) :
    /// **restaure** immédiatement le tri et les filtres mémorisés, puis les **re-mémorise** quand la vue quitte
    /// la scène (fermeture / navigation).
    ///
    /// `compteRendu` reçoit ce que la restauration n'a pas su replacer (#3093). C'est le chemin le plus
    /// discret des trois : personne n'a rien demandé, la vue se rouvre simplement, et les données ont pu
    /// changer entre-temps. Le taire laissait donc croire qu'on reprenait exactement là où on s'était
    /// arrêté.
    public void installer(
            TableView<LigneObservationAudio> table,
            GestionnaireFiltres<LigneObservationAudio> gestionnaireFiltres,
            Consumer<ResteDeRestauration> compteRendu) {
        restaurerTri(table);
        if (gestionnaireFiltres != null && filtres != null) {
            ResteDeRestauration reste = gestionnaireFiltres.restaurer(filtres);
            if (!reste.estVide()) {
                compteRendu.accept(reste);
            }
        }
        table.sceneProperty().addListener((obs, avant, scene) -> {
            if (scene == null) {
                memoriserTri(table);
                if (gestionnaireFiltres != null) {
                    filtres = gestionnaireFiltres.decrire();
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
