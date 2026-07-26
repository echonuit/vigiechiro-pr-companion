package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.AgregationActivite;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.commun.model.PlageNuit;
import java.util.LinkedHashSet;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;

/// ViewModel de l'écran **Activité de la nuit** (#2352, lot 2 du chantier #2348). Porte l'état réactif
/// de la courbe : la largeur de tranche, la liste des espèces (triée par total, telle que la rend
/// [AgregationActivite]), la sélection courante et les courbes effectivement affichées, plus la fenêtre
/// nocturne à matérialiser.
///
/// Répartition des rôles calquée sur la feature `analyse` : [ServiceActivite] **lit et projette**, le
/// ViewModel **agrège et rend réactif**. Changer la tranche ré-agrège sans toucher la sélection (les
/// espèces présentes ne changent pas, seul leur découpage change) ; cocher/décocher une espèce ne fait
/// que recalculer les courbes affichées.
public class ActiviteViewModel {

    /// Nombre d'espèces sélectionnées par défaut : les plus contactées. Au-delà, le graphe devient un plat
    /// de spaghettis (cf. #2352).
    private static final int ESPECES_PAR_DEFAUT = 5;

    private final ServiceActivite service;

    private final ObjectProperty<LargeurTranche> tranche = new SimpleObjectProperty<>(LargeurTranche.DEMI_HEURE);
    private final ObservableList<CourbeEspece> especes = FXCollections.observableArrayList();
    private final ObservableSet<String> especesSelectionnees = FXCollections.observableSet(new LinkedHashSet<>());
    private final ObservableList<CourbeEspece> courbesAffichees = FXCollections.observableArrayList();
    private final ObjectProperty<PlageNuit> plageNuit = new SimpleObjectProperty<>();

    private List<ContactHoraire> contacts = List.of();

    public ActiviteViewModel(ServiceActivite service) {
        this.service = service;
        tranche.addListener((observable, ancienne, nouvelle) -> reagreger());
        especesSelectionnees.addListener((SetChangeListener<String>) changement -> majCourbesAffichees());
    }

    /// Charge un passage : récupère ses contacts datés et sa fenêtre nocturne, agrège avec la tranche
    /// courante, puis présélectionne les cinq espèces les plus contactées.
    public void chargerPassage(long idPassage) {
        contacts = service.contactsDuPassage(idPassage);
        plageNuit.set(service.plageNuit(idPassage).orElse(null));
        reagreger();
        selectionnerLesPlusContactees();
    }

    private void reagreger() {
        especes.setAll(AgregationActivite.parEspece(contacts, tranche.get()));
        majCourbesAffichees();
    }

    private void selectionnerLesPlusContactees() {
        especesSelectionnees.clear();
        especes.stream().limit(ESPECES_PAR_DEFAUT).map(CourbeEspece::taxon).forEach(especesSelectionnees::add);
    }

    private void majCourbesAffichees() {
        courbesAffichees.setAll(especes.stream()
                .filter(courbe -> especesSelectionnees.contains(courbe.taxon()))
                .toList());
    }

    /// Largeur de tranche courante (15, 30 ou 60 min) ; la modifier ré-agrège la courbe.
    public ObjectProperty<LargeurTranche> trancheProperty() {
        return tranche;
    }

    /// Toutes les espèces de la nuit, triées par total décroissant : la matière du sélecteur et de la
    /// légende.
    public ObservableList<CourbeEspece> especes() {
        return especes;
    }

    /// Codes des taxons sélectionnés (modifiable par la vue) ; toute modification recalcule les courbes
    /// affichées.
    public ObservableSet<String> especesSelectionnees() {
        return especesSelectionnees;
    }

    /// Les courbes effectivement tracées : les espèces sélectionnées, dans l'ordre par total décroissant.
    public ObservableList<CourbeEspece> courbesAffichees() {
        return courbesAffichees;
    }

    /// Fenêtre nocturne (coucher → lever) à matérialiser sous la courbe, ou `null` si elle n'est pas
    /// calculable (passage sans GPS, nuit polaire) : la vue trace alors sans aplat.
    public ObjectProperty<PlageNuit> plageNuitProperty() {
        return plageNuit;
    }
}
