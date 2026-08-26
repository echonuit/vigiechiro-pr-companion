package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.MemoireFiltres;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

/// Assemblage de la **barre de filtres et des vues mémorisées** de « Sons & validation » (#1194) :
/// le catalogue de critères de l'écran (#470/#471), la mémoire de session (#484) et les onglets de
/// vues (#623) avec leur capture de colonnes (#994). Les nœuds restent injectés par le FXML dans le
/// contrôleur, qui les passe ici regroupés.
final class FiltresVuesAudio {

    /// Nœuds de la barre (recherche, « + Filtre », puces, onglets), regroupés en objet-paramètre
    /// (patron [ColonnesAudio.Colonnes]).
    record Barre(TextField champRecherche, MenuButton menuAjoutFiltre, FlowPane pucesFiltres, FlowPane barreOnglets) {}

    private FiltresVuesAudio() {
        // Câblage statique : jamais instanciée.
    }

    /// Installe la barre de filtres « à la Notion », la mémoire de session puis les onglets de vues, et
    /// renvoie le gestionnaire de filtres (gardé par le contrôleur pour les navigations ciblées et le
    /// transport des filtres vers l'analyse).
    static GestionnaireFiltres<LigneObservationAudio> installer(
            Barre barre,
            TableView<LigneObservationAudio> table,
            AudioViewModel viewModel,
            MemoireFiltres memoire,
            DepotVues depotVues,
            String feature,
            MarqueurEspecesAEnjeu marqueurEnjeu,
            Supplier<List<GestionnaireColonnes.Colonne>> colonnes) {
        // Barre de filtres « à la Notion » (#470/#471) : recherche texte permanente + « + Filtre » + puces,
        // pilotant les filtres composables du view-model. Le catalogue est celui qui suit : l'énumérer ici
        // ferait une seconde liste à tenir à jour, et c'est ainsi que ce commentaire a fini par annoncer
        // deux critères là où il y en a dix (#3105). Le décompte, lui, est ancré dans
        // `docs/ecrans/validation.md` et vérifié par `DocumentationAJourTest`.
        GestionnaireFiltres<LigneObservationAudio> gestionnaireFiltres = new GestionnaireFiltres<>(
                barre.champRecherche(),
                barre.menuAjoutFiltre(),
                barre.pucesFiltres(),
                viewModel.filtres(),
                List.of(
                        CriteresAudio.statut(),
                        // Cascadage (#3095) : chaque domaine se calcule sur les lignes que les **autres**
                        // critères laissent passer. Passer `observationsFiltrees` ferait s'auto-effondrer
                        // la puce, puisque cette liste est déjà filtrée par le critère qu'on peuple.
                        CriteresAudio.groupe(
                                () -> viewModel.filtres().saufLui(ClesCriteres.GROUPE),
                                viewModel::signalerChoixRemplace),
                        CriteresAudio.taxon(() -> viewModel.filtres().saufLui(ClesCriteres.TAXON)),
                        CriteresAudio.lieu(() -> viewModel.filtres().saufLui(ClesCriteres.LIEU)),
                        CriteresAudio.certitude(),
                        CriteresAudio.references(),
                        CriteresAudio.douteux(),
                        CriteresAudio.nonIdentifie(),
                        CriteresAudio.aEnjeu(ligne -> marqueurEnjeu.aEnjeu(ligne.taxonRetenu())),
                        CriteresAudio.probabilite(),
                        CriteresAudio.heure(viewModel::plageNuitParDefaut)),
                CriteresAudio.rechercheTexte());
        // Mémoire de session (#484) : restaure le tri et l'état des filtres de la dernière ouverture, et les
        // re-mémorise à la fermeture. Placée après le gestionnaire de filtres (dont elle restitue l'état).
        memoire.installer(feature, table, gestionnaireFiltres, viewModel::signalerFiltresDeSessionAmputes);
        // Onglets de vues mémorisées (#623) : enregistrent/rejouent l'état de la barre de filtres. Les vues
        // par défaut sont en lecture seule et déclarées par `CriteresAudio.vuesParDefaut()` : au chargement,
        // « Tout » (sans filtre) est active, d'où toujours un contexte modifiable, sans masquer
        // d'observations.
        GestionnaireVues.avecDialogue(
                        barre.barreOnglets(),
                        gestionnaireFiltres,
                        depotVues,
                        feature,
                        CriteresAudio.vuesParDefaut(),
                        GestionnaireColonnes.adaptateurMonoTable("principale", table, colonnes))
                // Une vue rejouée amputée de valeurs disparues filtre moins large qu'annoncé (#3056).
                .surRestauration(viewModel::signalerVueAmputee);
        return gestionnaireFiltres;
    }
}
