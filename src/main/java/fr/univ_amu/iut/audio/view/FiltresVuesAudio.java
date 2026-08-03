package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

/// Assemblage de la **barre de filtres et des vues mémorisées** de « Sons & validation » (#1194) : le
/// catalogue de critères de l'écran (#470/#471), la mémoire de session (#484) et les onglets de vues
/// (#623) avec leur capture de colonnes (#994). Extrait de [SonsValidationController] (unité cohésive)
/// pour garder le contrôleur sous le plafond de concentration (`NcssCount`). Les nœuds restent injectés
/// par le FXML dans le contrôleur, qui les passe ici regroupés.
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
            MemoireRevueAudio memoire,
            DepotVues depotVues,
            String feature,
            MarqueurEspecesAEnjeu marqueurEnjeu,
            Supplier<List<GestionnaireColonnes.Colonne>> colonnes) {
        // Barre de filtres « à la Notion » (#470/#471) : recherche texte permanente + « + Filtre » + puces,
        // pilotant les filtres composables du view-model. Catalogue de critères : statut et groupe taxon.
        GestionnaireFiltres<LigneObservationAudio> gestionnaireFiltres = new GestionnaireFiltres<>(
                barre.champRecherche(),
                barre.menuAjoutFiltre(),
                barre.pucesFiltres(),
                viewModel.filtres(),
                List.of(
                        CriteresAudio.statut(),
                        CriteresAudio.groupe(viewModel::observationsFiltrees),
                        CriteresAudio.taxon(viewModel::observationsFiltrees),
                        CriteresAudio.lieu(viewModel::observationsFiltrees),
                        CriteresAudio.references(),
                        CriteresAudio.douteux(),
                        CriteresAudio.nonIdentifie(),
                        CriteresAudio.aEnjeu(ligne -> marqueurEnjeu.aEnjeu(ligne.taxonRetenu())),
                        CriteresAudio.probabilite(),
                        CriteresAudio.heure(viewModel::plageNuitParDefaut)),
                CriteresAudio.rechercheTexte());
        // Mémoire de session (#484) : restaure le tri et l'état des filtres de la dernière ouverture, et les
        // re-mémorise à la fermeture. Placée après le gestionnaire de filtres (dont elle restitue l'état).
        memoire.installer(table, gestionnaireFiltres, viewModel::signalerFiltresDeSessionAmputes);
        // Onglets de vues mémorisées (#623) : enregistrent/rejouent l'état de la barre de filtres. Trois vues
        // par défaut en lecture seule (« Tout », « À valider », « Chiroptères ») : au chargement, « Tout » (sans
        // filtre) est active, d'où toujours un contexte modifiable, sans masquer d'observations.
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
