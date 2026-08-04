package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.ClesCriteres;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.MemoireFiltres;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

/// Assemblage de la **barre de filtres, des vues mémorisées et de la mémoire de session** d'« Activité
/// de la nuit ».
///
/// Extrait de [ActiviteController] (#3098) pour la même raison que [FiltresVuesAudio] l'avait été du
/// contrôleur audio : garder le contrôleur sous le plafond de concentration du portail qualité
/// (`GodClass`). Les nœuds restent injectés par le FXML dans le contrôleur, qui les passe ici
/// regroupés.
final class FiltresVuesActivite {

    /// Clé de l'écran, partagée par les vues mémorisées et la mémoire de session.
    static final String FEATURE = "activite";

    /// Nœuds de la barre, regroupés en objet-paramètre.
    record Barre(
            TextField champRecherche,
            MenuButton menuAjoutFiltre,
            FlowPane pucesFiltres,
            FlowPane barreOnglets,
            Button boutonToutEffacer) {}

    private FiltresVuesActivite() {
        // Câblage statique : jamais instanciée.
    }

    /// Ce que l'assemblage rend au contrôleur : les **deux** gestionnaires, qu'il garde pour ses
    /// propres gestes (rejouer une vue par défaut, piloter les filtres).
    record Gestionnaires(GestionnaireFiltres<ContactHoraire> filtres, GestionnaireVues<ContactHoraire> vues) {}

    /// Installe la barre de filtres, les onglets de vues et la mémoire de session.
    static Gestionnaires installer(
            Barre barre,
            ActiviteViewModel viewModel,
            MemoireFiltres memoire,
            DepotVues depotVues,
            MarqueurEspecesAEnjeu marqueurEnjeu) {
        GestionnaireFiltres<ContactHoraire> gestionnaireFiltres = new GestionnaireFiltres<>(
                barre.champRecherche(),
                barre.menuAjoutFiltre(),
                barre.pucesFiltres(),
                viewModel.filtres(),
                List.of(
                        // Cascadage (#3095) : le domaine se calcule sur les lignes que les AUTRES
                        // critères laissent passer. Lire la liste déjà filtrée ferait s'auto-effondrer
                        // la puce, qui n'offrirait plus que la valeur déjà retenue.
                        CriteresActivite.lieu(() -> viewModel.filtres().saufLui(ClesCriteres.LIEU)),
                        // « Nuit » n'est PAS cascadée, et c'est délibéré : c'est un SÉLECTEUR, pas une
                        // facette. Restreindre la liste des nuits à celles qui passent les autres
                        // filtres retirerait du menu la nuit vers laquelle on veut aller, et il
                        // faudrait défaire un filtre pour naviguer. Même raison qui garde année et
                        // campagne en contrôles fixes sur Ma saison (#3103). Cf. ADR 3095.
                        CriteresActivite.nuit(viewModel::nuitsDisponibles),
                        CriteresActivite.groupe(() ->
                                CriteresActivite.groupesDe(viewModel.filtres().saufLui(ClesCriteres.GROUPE))),
                        CriteresActivite.natureNuit(viewModel::nuitsOpportunistes),
                        CriteresActivite.aEnjeu(contact -> marqueurEnjeu.aEnjeu(contact.taxon()))),
                CriteresActivite.rechercheTexte());

        // Onglets de vues (#623) : les vues par défaut partitionnent par catégorie du référentiel, et
        // l'écran s'ouvre sur « Chiroptères » : Tadarida détecte aussi orthoptères et micromammifères,
        // qui n'ont rien à faire dans la présélection des cinq taxons les plus contactés.
        GestionnaireVues<ContactHoraire> gestionnaireVues = GestionnaireVues.avecDialogue(
                        barre.barreOnglets(), gestionnaireFiltres, depotVues, FEATURE, CriteresActivite.vuesParDefaut())
                // Une vue rejouée amputée de valeurs disparues filtre moins large qu'annoncé (#3056).
                .surRestauration(viewModel::signalerVueAmputee);

        // Mémoire de session (#3098). Cet écran est un GRAPHE : il n'a aucune table, donc aucun tri à
        // mémoriser - seuls ses filtres le sont. C'est le cas qui a fait séparer les deux mémoires du
        // socle.
        memoire.installer(
                FEATURE, barre.barreOnglets(), gestionnaireFiltres, viewModel::signalerFiltresDeSessionAmputes);

        // « Tout effacer » (#3098) est câblé ICI plutôt que par un `onAction` du FXML : le geste
        // appartient à l'assemblage qui sait ce qu'il y a à vider, et le contrôleur n'a pas à gagner
        // une méthode de plus - il est déjà au plafond de concentration du portail qualité.
        //
        // Oublier la mémoire n'est pas accessoire : sans cela le geste viderait l'écran et la mémoire
        // remettrait tout à la visite suivante, donnant l'impression de n'avoir pas pris.
        barre.boutonToutEffacer().setOnAction(evenement -> {
            gestionnaireFiltres.reinitialiser();
            memoire.oublier(FEATURE);
        });
        return new Gestionnaires(gestionnaireFiltres, gestionnaireVues);
    }
}
