package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.AgregationActivite;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import java.util.LinkedHashSet;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.FilteredList;

/// ViewModel de l'écran **Activité de la nuit** (#2352, lot 2 du chantier #2348). Porte l'état réactif de
/// la courbe : la largeur de tranche, la liste des espèces (triée par total, telle que la rend
/// [AgregationActivite]), la sélection courante, les courbes affichées et la fenêtre nocturne.
///
/// **Filtrable** sur le socle `analyse` : les contacts chargés (un passage, ou **tous** les passages de
/// l'utilisateur) vivent dans une [ObservableList] `tous` → [FilteredList] → [Filtres]. Un changement de
/// filtre (carré, point, passage, espèce…) recalcule le **sous-ensemble** puis **ré-agrège** ce
/// sous-ensemble en courbes — filtrer, c'est re-tracer. Répartition des rôles calquée sur `analyse` :
/// [ServiceActivite] **lit et projette**, le ViewModel **agrège et rend réactif**.
///
/// Changer la tranche ré-agrège sans toucher la sélection ; (dé)cocher une espèce ne recalcule que les
/// courbes affichées. La sélection est conservée par **code** au fil des filtres : une espèce filtrée
/// hors du sous-ensemble ne trace plus, mais réapparaît cochée quand le filtre se relâche.
public class ActiviteViewModel {

    /// Nombre d'espèces sélectionnées par défaut : les plus contactées. Au-delà, le graphe devient un plat
    /// de spaghettis (cf. #2352).
    private static final int ESPECES_PAR_DEFAUT = 5;

    private final ServiceActivite service;

    private final ObjectProperty<LargeurTranche> tranche = new SimpleObjectProperty<>(LargeurTranche.DEMI_HEURE);
    private final ObservableList<ContactHoraire> tous = FXCollections.observableArrayList();
    private final FilteredList<ContactHoraire> contactsFiltres = new FilteredList<>(tous);
    private final Filtres<ContactHoraire> filtres = new Filtres<>(contactsFiltres, this::reagreger);
    private final ObservableList<CourbeEspece> especes = FXCollections.observableArrayList();
    private final ObservableSet<String> especesSelectionnees = FXCollections.observableSet(new LinkedHashSet<>());
    private final ObservableList<CourbeEspece> courbesAffichees = FXCollections.observableArrayList();
    private final ObjectProperty<PlageNuit> plageNuit = new SimpleObjectProperty<>();

    public ActiviteViewModel(ServiceActivite service) {
        this.service = service;
        tranche.addListener((observable, ancienne, nouvelle) -> reagreger());
        especesSelectionnees.addListener((SetChangeListener<String>) changement -> majCourbesAffichees());
    }

    /// Charge **un passage** : ses contacts datés et sa fenêtre nocturne, agrège avec la tranche courante,
    /// puis présélectionne les cinq espèces les plus contactées.
    public void chargerPassage(long idPassage) {
        remplacerContacts(
                service.contactsDuPassage(idPassage),
                service.plageNuit(idPassage).orElse(null));
    }

    /// Charge **tous les passages** de l'utilisateur (vue transverse filtrable). Aucune fenêtre nocturne
    /// unique n'a de sens sur plusieurs nuits : l'aplat coucher/lever est laissé à `null` tant qu'un filtre
    /// n'a pas ramené la sélection à un seul passage.
    public void chargerUtilisateur(String idUtilisateur) {
        remplacerContacts(service.contactsDeLUtilisateur(idUtilisateur), null);
    }

    private void remplacerContacts(List<ContactHoraire> contacts, PlageNuit fenetre) {
        tous.setAll(contacts);
        plageNuit.set(fenetre);
        reagreger();
        selectionnerLesPlusContactees();
    }

    private void reagreger() {
        especes.setAll(AgregationActivite.parEspece(contactsFiltres, tranche.get()));
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

    /// Le socle de filtres cascadés (carré → point → passage → espèce), à câbler à la barre de filtres de
    /// la vue. Chaque changement ré-agrège le sous-ensemble.
    public Filtres<ContactHoraire> filtres() {
        return filtres;
    }

    /// Largeur de tranche courante (15, 30 ou 60 min) ; la modifier ré-agrège la courbe.
    public ObjectProperty<LargeurTranche> trancheProperty() {
        return tranche;
    }

    /// Toutes les espèces du sous-ensemble courant, triées par total décroissant : la matière du sélecteur
    /// et de la légende.
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
    /// calculable (passage sans GPS, nuit polaire) ou pas unique (vue multi-passages) : la vue trace alors
    /// sans aplat.
    public ObjectProperty<PlageNuit> plageNuitProperty() {
        return plageNuit;
    }
}
