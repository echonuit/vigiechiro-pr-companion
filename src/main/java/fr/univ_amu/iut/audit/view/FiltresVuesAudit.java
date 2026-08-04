package fr.univ_amu.iut.audit.view;

import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.MemoireFiltres;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

/// Assemblage de la **barre de filtres et des vues mémorisées** de l'« Audit de cohérence » (#3100),
/// cinquième et dernier écran posé sur le socle « à la Notion » (chantier #3092).
///
/// Même découpage que ses quatre jumelles ([fr.univ_amu.iut.audio.view.FiltresVuesAudio],
/// [fr.univ_amu.iut.analyse.view.FiltresVuesActivite]) : le contrôleur reçoit les nœuds du FXML et les
/// passe ici regroupés, ce qui garde le contrôleur sous le plafond de concentration (`NcssCount`).
final class FiltresVuesAudit {

    /// Clé de l'écran pour les vues sauvegardées et la mémoire de session.
    private static final String FEATURE = "audit";

    /// Nœuds de la barre, regroupés en objet-paramètre.
    record Barre(
            TextField champRecherche,
            MenuButton menuAjoutFiltre,
            FlowPane pucesFiltres,
            FlowPane barreOnglets,
            Button boutonToutEffacer) {}

    private FiltresVuesAudit() {
        // Câblage statique : jamais instanciée.
    }

    /// Installe la barre de filtres, les onglets de vues et la mémoire de session (filtres **et** tri de
    /// la table des constats).
    static void installer(
            Barre barre,
            TableView<ConstatAudit> table,
            AuditViewModel viewModel,
            MemoireFiltres memoire,
            DepotVues depotVues,
            List<GestionnaireColonnes.Colonne> colonnes) {
        GestionnaireFiltres<ConstatAudit> gestionnaireFiltres = new GestionnaireFiltres<>(
                barre.champRecherche(),
                barre.menuAjoutFiltre(),
                barre.pucesFiltres(),
                viewModel.filtres(),
                List.of(
                        CriteresAudit.gravite(),
                        CriteresAudit.categorie(),
                        // Cascadage (#3095) : le domaine se calcule sur les constats que les **autres**
                        // puces laissent passer. Lire la liste déjà filtrée ferait s'auto-effondrer la
                        // puce Passage - une fois la nuit 42 cochée, le menu n'offrirait plus que 42.
                        CriteresAudit.passage(() -> viewModel.filtres().saufLui(CriteresAudit.PASSAGE))),
                CriteresAudit.rechercheTexte());

        // « Tout effacer » (#3097) : présent sur les cinq écrans depuis l'uniformisation, et partout le
        // même geste **complet** - les filtres, le tri de la table, et l'oubli de ce que la mémoire de
        // session s'apprêtait à remettre. N'effacer que les filtres les ferait revenir à la réouverture.
        barre.boutonToutEffacer().setOnAction(evenement -> {
            gestionnaireFiltres.reinitialiser();
            table.getSortOrder().clear();
            memoire.oublier(FEATURE);
        });

        // Aucune vue par défaut ici, contrairement aux quatre autres écrans : une vue « Bloquants
        // seulement » se dessine sur une distribution de gravités réelle, que la base de démonstration
        // ne produit pas encore (#3169). La proposer sans l'avoir vue à l'œuvre serait deviner.
        GestionnaireVues.avecDialogue(
                        barre.barreOnglets(),
                        gestionnaireFiltres,
                        depotVues,
                        FEATURE,
                        List.of(),
                        GestionnaireColonnes.adaptateurMonoTable("constats", table, colonnes))
                // Une vue rejouée amputée de valeurs disparues filtre moins large qu'annoncé (#3056).
                .surRestauration(viewModel::signalerVueAmputee);

        // Mémoire de session (#3098). Le cas est particulier : relancer l'audit renouvelle les constats,
        // donc les passages offerts changent d'une visite à l'autre. C'est précisément quand la valeur
        // mémorisée n'existe plus que le compte rendu doit parler (#3093).
        memoire.installer(FEATURE, table, gestionnaireFiltres, viewModel::signalerFiltresDeSessionAmputes);
        memoire.memoriserTri(FEATURE, table);
    }
}
