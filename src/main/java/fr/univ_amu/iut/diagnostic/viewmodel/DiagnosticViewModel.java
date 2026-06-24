package fr.univ_amu.iut.diagnostic.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Diagnostic** (état matériel d'une nuit, parcours P6).
///
/// Ouvert sur un `idPassage`, il lit [ServiceDiagnostic#diagnostiquer(Long)] et expose : la série
/// climatique T°/hygrométrie (pour un graphe), les anomalies et évènements du journal (R19), et
/// l'absence éventuelle de relevé climatique (R20). VM agnostique de l'IHM (règle ArchUnit
/// `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`. Non-singleton.
public class DiagnosticViewModel {

    private final ServiceDiagnostic service;

    private final ReadOnlyStringWrapper enregistreur = new ReadOnlyStringWrapper(this, "enregistreur", "");
    private final ReadOnlyStringWrapper resumeClimat = new ReadOnlyStringWrapper(this, "resumeClimat", "");
    private final ReadOnlyBooleanWrapper releveClimatiqueAbsent =
            new ReadOnlyBooleanWrapper(this, "releveClimatiqueAbsent", false);
    private final ReadOnlyBooleanWrapper gpsDisponible = new ReadOnlyBooleanWrapper(this, "gpsDisponible", false);
    private final ObservableList<MesureClimatique> mesures = FXCollections.observableArrayList();
    private final ObservableList<String> anomalies = FXCollections.observableArrayList();
    private final ObservableList<String> evenements = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Température en début de nuit (#106) : libellé d'affichage (`8,5 °C` / `—`).
    private final ReadOnlyStringWrapper temperature = new ReadOnlyStringWrapper(this, "temperature", "—");

    public DiagnosticViewModel(ServiceDiagnostic service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// Ouvre le diagnostic du passage `idPassage`. Une erreur (passage/session introuvable) est
    /// restituée dans [#messageProperty()] sans lever, l'écran restant vide.
    public void ouvrirSur(Long idPassage) {
        // TODO (M-Diagnostic) : peuplez les propriétés à partir de service.diagnostiquer(idPassage).
        //   - succès : alimentez enregistreur, releveClimatiqueAbsent, gpsDisponible, mesures,
        //     anomalies, evenements et resumeClimat, puis videz message ;
        //   - échec (RuntimeException) : réinitialisez tout et publiez le message d'erreur, sans lever.
        // Patron de référence : le ViewModel de la feature sites (SiteDetailViewModel).
        // --solution--
        reinitialiser();
        try {
            appliquer(service.diagnostiquer(idPassage));
            message.set("");
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
        // --end-solution--
    }

    // --solution--
    private void appliquer(Diagnostic diagnostic) {
        enregistreur.set("PR " + diagnostic.numeroSerieEnregistreur());
        releveClimatiqueAbsent.set(diagnostic.releveClimatiqueAbsent());
        gpsDisponible.set(diagnostic.coordonneesGpsDisponibles());
        mesures.setAll(diagnostic.climat().mesures());
        anomalies.setAll(diagnostic.anomalies().anomalies());
        evenements.setAll(diagnostic.anomalies().evenements());
        resumeClimat.set(
                diagnostic.climat().present()
                        ? diagnostic.climat().nombreMesures() + " mesures T°/hygrométrie"
                        : "Relevé climatique absent (R20)");
        temperature.set(Formats.temperatureLisible(diagnostic.temperatureDebutNuit()));
    }

    private void reinitialiser() {
        enregistreur.set("");
        resumeClimat.set("");
        releveClimatiqueAbsent.set(false);
        gpsDisponible.set(false);
        mesures.clear();
        anomalies.clear();
        evenements.clear();
        temperature.set("—");
    }
    // --end-solution--

    /// Enregistreur de la nuit (`PR <n° de série>`).
    public ReadOnlyStringProperty enregistreurProperty() {
        return enregistreur.getReadOnlyProperty();
    }

    /// Résumé de la série climatique (`N mesures T°/hygrométrie`, ou absence R20).
    public ReadOnlyStringProperty resumeClimatProperty() {
        return resumeClimat.getReadOnlyProperty();
    }

    /// Température en début de nuit, libellé d'affichage (`8,5 °C` / `—`, #106).
    public ReadOnlyStringProperty temperatureProperty() {
        return temperature.getReadOnlyProperty();
    }

    /// `true` si aucun relevé climatique n'est rattaché (R20, à signaler).
    public ReadOnlyBooleanProperty releveClimatiqueAbsentProperty() {
        return releveClimatiqueAbsent.getReadOnlyProperty();
    }

    /// `true` si les coordonnées GPS du point sont disponibles (précondition de l'encart horaires).
    public ReadOnlyBooleanProperty gpsDisponibleProperty() {
        return gpsDisponible.getReadOnlyProperty();
    }

    /// Série temporelle T°/hygrométrie de la nuit (points du graphe).
    public ObservableList<MesureClimatique> mesures() {
        return mesures;
    }

    /// Anomalies détectées dans le journal du capteur (R19).
    public ObservableList<String> anomalies() {
        return anomalies;
    }

    /// Évènements remarquables du journal du capteur (R19).
    public ObservableList<String> evenements() {
        return evenements;
    }

    /// Message d'erreur (passage/session introuvable), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
