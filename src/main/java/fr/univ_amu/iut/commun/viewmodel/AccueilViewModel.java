package fr.univ_amu.iut.commun.viewmodel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// État observable du **tableau de bord d'accueil** (#1376) : la liste des compteurs, recalculée à
/// chaque révision des données.
///
/// ## Ce que ce ViewModel corrige
///
/// Le bandeau était bâti par le chrome à l'initialisation, puis **au retour sur l'accueil**. Une
/// mutation survenue sans changement d'écran (la synchronisation d'une connexion ouverte par-dessus
/// l'accueil) ne déclenchait donc ni l'un ni l'autre, et les compteurs gardaient l'instantané d'avant.
///
/// Ici, la source du rafraîchissement n'est plus la navigation mais la donnée : [RevisionDonnees]
/// avance, la liste se recalcule, et la vue n'a plus qu'à la restituer.
///
/// ## Pourquoi les compteurs sont relus tous ensemble
///
/// La révision est un compteur **global** (#3541) : elle dit qu'une mutation structurelle a eu lieu,
/// pas laquelle. Les quatre indicateurs sont donc relus à chaque avancée. C'est délibéré : un signal
/// typé par domaine ajouterait une façon d'avoir tort en silence (annoncer « sites » en ayant aussi
/// touché les points), pour une économie de trois `COUNT(*)` au rythme d'une mutation humaine.
@Singleton
public final class AccueilViewModel {

    private final Set<IndicateurAccueil> indicateurs;
    private final ObservableList<CompteurAccueil> compteurs = FXCollections.observableArrayList();

    @Inject
    public AccueilViewModel(Set<IndicateurAccueil> indicateurs, RevisionDonnees revision) {
        this.indicateurs = Objects.requireNonNull(indicateurs, "indicateurs");
        Objects.requireNonNull(revision, "revision").revisionProperty().addListener((obs, avant, apres) -> relire());
        relire();
    }

    /// Les compteurs à afficher, triés par `ordre()`. Liste observable : la vue s'y lie une fois.
    public ObservableList<CompteurAccueil> compteurs() {
        return compteurs;
    }

    /// Vrai quand au moins un compteur est non nul. Le bandeau reste **masqué** tant que la base est
    /// vide (premier lancement) : l'accueil reste épuré plutôt que d'afficher une rangée de « 0 ».
    public boolean aDesDonnees() {
        return compteurs.stream().mapToLong(CompteurAccueil::valeur).sum() > 0;
    }

    /// Relit chaque indicateur **une seule fois** par révision : `valeur()` déclenche un `COUNT(*)`,
    /// et le rappeler pour décider de la visibilité puis pour l'affichage le paierait deux fois.
    private void relire() {
        List<CompteurAccueil> releve = indicateurs.stream()
                .sorted(Comparator.comparingInt(IndicateurAccueil::ordre))
                .map(indicateur -> new CompteurAccueil(indicateur, indicateur.valeur()))
                .toList();
        compteurs.setAll(releve);
    }

    /// Un indicateur et la valeur qu'il portait au moment du relevé.
    public record CompteurAccueil(IndicateurAccueil indicateur, long valeur) {}
}
