package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran transverse **« Espèces & observations »** (feature `analyse`, prisme
/// biodiversité). Pilote l'**inventaire** des observations de l'utilisateur, regroupé **par espèce** ou
/// **par carré** ([Regroupement]) et filtré par **statut** de revue (`null` = toutes).
///
/// Tout changement de regroupement ou de filtre **ré-interroge** [ServiceAnalyse] et recharge la liste
/// active. Le VM ne fait que relayer (le service agrège côté SQL). Agnostique de l'IHM (ArchUnit
/// `viewmodel_sans_javafx_ui`). Non-singleton (un VM frais par chargement d'écran).
public class AnalyseViewModel {

    private final ServiceAnalyse service;
    private final String idUtilisateur;

    private final ObjectProperty<Regroupement> regroupement =
            new SimpleObjectProperty<>(this, "regroupement", Regroupement.PAR_ESPECE);
    /// Filtre de statut de revue ; `null` = toutes les observations.
    private final ObjectProperty<StatutObservation> filtreStatut = new SimpleObjectProperty<>(this, "filtreStatut");

    private final ObservableList<EspeceAgregee> especes = FXCollections.observableArrayList();
    private final ObservableList<CarreEspeces> carres = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");

    public AnalyseViewModel(ServiceAnalyse service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        regroupement.addListener((obs, ancien, nouveau) -> rafraichir());
        filtreStatut.addListener((obs, ancien, nouveau) -> rafraichir());
    }

    /// (Re)charge la liste **active** (selon le regroupement) avec le filtre de statut courant, et met à
    /// jour le résumé. À appeler à l'ouverture de l'écran ; ensuite déclenché par tout changement.
    public void rafraichir() {
        StatutObservation statut = filtreStatut.get();
        if (regroupement.get() == Regroupement.PAR_CARRE) {
            carres.setAll(service.inventaireParCarre(idUtilisateur, statut));
            especes.clear();
            int detections =
                    carres.stream().mapToInt(CarreEspeces::nbObservations).sum();
            resume.set(quantite(carres.size(), "carré") + " · " + quantite(detections, "détection"));
        } else {
            especes.setAll(service.inventaireParEspece(idUtilisateur, statut));
            carres.clear();
            int detections =
                    especes.stream().mapToInt(EspeceAgregee::nbObservations).sum();
            resume.set(quantite(especes.size(), "espèce") + " · " + quantite(detections, "détection"));
        }
    }

    /// Accord en nombre : `quantite(3, "espèce")` → `3 espèces` ; `quantite(1, "carré")` → `1 carré`.
    private static String quantite(int nombre, String unite) {
        return nombre + " " + unite + (nombre > 1 ? "s" : "");
    }

    public ObjectProperty<Regroupement> regroupementProperty() {
        return regroupement;
    }

    public ObjectProperty<StatutObservation> filtreStatutProperty() {
        return filtreStatut;
    }

    /// Inventaire par espèce (alimenté quand le regroupement est [Regroupement#PAR_ESPECE]).
    public ObservableList<EspeceAgregee> especes() {
        return especes;
    }

    /// Inventaire par carré (alimenté quand le regroupement est [Regroupement#PAR_CARRE]).
    public ObservableList<CarreEspeces> carres() {
        return carres;
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }
}
