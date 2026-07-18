package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.view.AdaptateurColonnes;
import fr.univ_amu.iut.commun.view.DescripteurColonnes;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;

/// Sélecteur de colonnes de l'écran « Espèces & observations » (EPIC #914, #994), **extrait** du controller
/// (God-class) pour concentrer le câblage des colonnes de ses **trois** tables : l'inventaire par espèce,
/// l'inventaire par carré (exclusifs, même emplacement) et le détail des observations.
///
/// - **Clic droit « Colonnes… »** sur chaque table (celui des espèces reçoit en plus « Fiche de l'espèce »).
/// - L'unique **☰ « outils »** ouvre le panneau de la table maître **visible** (espèces ou carrés selon le
///   regroupement courant).
/// - Un [AdaptateurColonnes] capture/rejoue la disposition des trois tables dans les **vues mémorisées**.
///
/// Les descripteurs (en-tête = libellé, colonne de tête = identité) sont **figés à la construction**, pour
/// que clic droit, ☰ et vues mémorisées règlent exactement les mêmes colonnes, indépendamment des
/// réordonnancements ultérieurs.
final class SelecteurColonnesAnalyse {

    /// Clés stables des tables dans le descripteur de vue (une entrée de map par table).
    private static final String ESPECES = "especes";

    private static final String CARRES = "carres";

    private static final String OBSERVATIONS = "observations";

    private final TableView<?> tableEspeces;
    private final TableView<?> tableCarres;
    private final TableView<?> tableObservations;
    private final MenuButton menuOutils;
    private final Supplier<Regroupement> regroupementCourant;

    private final List<GestionnaireColonnes.Colonne> colonnesEspeces;
    private final List<GestionnaireColonnes.Colonne> colonnesCarres;
    private final List<GestionnaireColonnes.Colonne> colonnesObservations;

    SelecteurColonnesAnalyse(
            TableView<?> tableEspeces,
            TableView<?> tableCarres,
            TableView<?> tableObservations,
            MenuButton menuOutils,
            Supplier<Regroupement> regroupementCourant) {
        this.tableEspeces = tableEspeces;
        this.tableCarres = tableCarres;
        this.tableObservations = tableObservations;
        this.menuOutils = menuOutils;
        this.regroupementCourant = regroupementCourant;
        this.colonnesEspeces = GestionnaireColonnes.colonnesParDefaut(tableEspeces);
        this.colonnesCarres = GestionnaireColonnes.colonnesParDefaut(tableCarres);
        this.colonnesObservations = GestionnaireColonnes.colonnesParDefaut(tableObservations);
    }

    /// Câble le clic droit des trois tables et l'item « Colonnes… » du ☰, qui ouvre le panneau de la table
    /// maître **visible**. La table des espèces (`itemsEspeces` : fiche #1795, copier #1798) et le détail
    /// des observations (`itemsObservations` : écouter, ouvrir le passage, fiche #1796, copier #1798)
    /// reçoivent leurs items de ligne, dans l'ordre donné, devant « Colonnes… ». La table des carrés
    /// n'affiche pas d'espèce, elle garde le seul « Colonnes… ».
    void installer(List<MenuItem> itemsEspeces, List<MenuItem> itemsObservations) {
        GestionnaireColonnes.installerClicDroit(tableEspeces, colonnesEspeces, itemsEspeces.toArray(MenuItem[]::new));
        GestionnaireColonnes.installerClicDroit(tableCarres, colonnesCarres);
        GestionnaireColonnes.installerClicDroit(
                tableObservations, colonnesObservations, itemsObservations.toArray(MenuItem[]::new));
        MenuItem itemColonnes = new MenuItem("Colonnes…");
        itemColonnes.setOnAction(e -> ouvrirMaitre());
        // Séparateur seulement si le ☰ porte déjà des actions (#995) : ici il est dédié aux colonnes, donc pas
        // de trait parasite en tête.
        if (!menuOutils.getItems().isEmpty()) {
            menuOutils.getItems().add(new SeparatorMenuItem());
        }
        menuOutils.getItems().add(itemColonnes);
    }

    /// Rend la disposition des trois tables **persistante par écran** (#994, couche « défaut par écran ») :
    /// chaque table est retenue sous sa propre clé (`especes` / `carres` / `observations`).
    void persister(DepotDispositionColonnes depot, String feature) {
        GestionnaireColonnes.persister(tableEspeces, colonnesEspeces, depot, feature, ESPECES);
        GestionnaireColonnes.persister(tableCarres, colonnesCarres, depot, feature, CARRES);
        GestionnaireColonnes.persister(tableObservations, colonnesObservations, depot, feature, OBSERVATIONS);
    }

    private void ouvrirMaitre() {
        boolean parEspece = regroupementCourant.get() == Regroupement.PAR_ESPECE;
        GestionnaireColonnes.ouvrir(
                parEspece ? tableEspeces : tableCarres, parEspece ? colonnesEspeces : colonnesCarres, menuOutils);
    }

    /// Adaptateur pour les vues mémorisées (#994) : décrit / rejoue la disposition des trois tables (une
    /// entrée de map par table).
    AdaptateurColonnes adaptateur() {
        return new AdaptateurColonnes() {
            @Override
            public Map<String, DescripteurColonnes> decrire() {
                return Map.of(
                        ESPECES, GestionnaireColonnes.decrire(tableEspeces, colonnesEspeces),
                        CARRES, GestionnaireColonnes.decrire(tableCarres, colonnesCarres),
                        OBSERVATIONS, GestionnaireColonnes.decrire(tableObservations, colonnesObservations));
            }

            @Override
            public void restaurer(Map<String, DescripteurColonnes> dispositions) {
                appliquer(dispositions, ESPECES, tableEspeces, colonnesEspeces);
                appliquer(dispositions, CARRES, tableCarres, colonnesCarres);
                appliquer(dispositions, OBSERVATIONS, tableObservations, colonnesObservations);
            }
        };
    }

    private static void appliquer(
            Map<String, DescripteurColonnes> dispositions,
            String cle,
            TableView<?> table,
            List<GestionnaireColonnes.Colonne> colonnes) {
        DescripteurColonnes disposition = dispositions.get(cle);
        if (disposition != null) {
            GestionnaireColonnes.restaurer(table, colonnes, disposition);
        }
    }
}
