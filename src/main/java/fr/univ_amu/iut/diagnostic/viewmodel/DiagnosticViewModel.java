package fr.univ_amu.iut.diagnostic.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import java.time.format.DateTimeFormatter;
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

    /// Format d'affichage des heures de la fenêtre nocturne (`HH:mm`).
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final ServiceDiagnostic service;

    private final ReadOnlyStringWrapper enregistreur = new ReadOnlyStringWrapper(this, "enregistreur", "");
    private final ReadOnlyBooleanWrapper releveClimatiqueAbsent =
            new ReadOnlyBooleanWrapper(this, "releveClimatiqueAbsent", false);
    private final ReadOnlyBooleanWrapper gpsDisponible = new ReadOnlyBooleanWrapper(this, "gpsDisponible", false);
    private final ObservableList<MesureClimatique> mesures = FXCollections.observableArrayList();
    private final ObservableList<String> anomalies = FXCollections.observableArrayList();
    private final ObservableList<String> evenements = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Température en début de nuit (#106) : libellé d'affichage (`8,5 °C` / `—`).
    private final ReadOnlyStringWrapper temperature = new ReadOnlyStringWrapper(this, "temperature", "—");

    /// Cohérence horaires (#548) : fenêtre nocturne calculable au point d'écoute.
    private final ReadOnlyBooleanWrapper coherenceHoraireDisponible =
            new ReadOnlyBooleanWrapper(this, "coherenceHoraireDisponible", false);

    /// Libellé de la fenêtre nocturne (`🌙 Nuit : coucher 21:58 · lever 05:48`), vide si indisponible.
    private final ReadOnlyStringWrapper fenetreNuit = new ReadOnlyStringWrapper(this, "fenetreNuit", "");

    /// Alerte « hors nuit » (démarrage/arrêt diurne), vide si les horaires sont cohérents.
    private final ReadOnlyStringWrapper alerteHorsNuit = new ReadOnlyStringWrapper(this, "alerteHorsNuit", "");

    public DiagnosticViewModel(ServiceDiagnostic service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// Ouvre le diagnostic du passage `idPassage`. Une erreur (passage/session introuvable) est
    /// restituée dans [#messageProperty()] sans lever, l'écran restant vide.
    public void ouvrirSur(Long idPassage) {
        reinitialiser();
        try {
            appliquer(service.diagnostiquer(idPassage));
            message.set("");
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
    }

    private void appliquer(Diagnostic diagnostic) {
        enregistreur.set("PR " + diagnostic.numeroSerieEnregistreur());
        releveClimatiqueAbsent.set(diagnostic.releveClimatiqueAbsent());
        gpsDisponible.set(diagnostic.coordonneesGpsDisponibles());
        mesures.setAll(diagnostic.climat().mesures());
        anomalies.setAll(diagnostic.anomalies().anomalies());
        evenements.setAll(diagnostic.anomalies().evenements());
        temperature.set(Formats.temperatureLisible(diagnostic.temperatureDebutNuit()));
        appliquerCoherence(diagnostic.coherenceHoraire());
    }

    private void appliquerCoherence(CoherenceHoraire coherence) {
        coherenceHoraireDisponible.set(coherence.disponible());
        if (!coherence.disponible()) {
            fenetreNuit.set("");
            alerteHorsNuit.set("");
            return;
        }
        fenetreNuit.set("🌙 Nuit : coucher " + HEURE.format(coherence.coucherSoleil()) + " · lever "
                + HEURE.format(coherence.leverSoleil()));
        alerteHorsNuit.set(libelleEcart(coherence));
    }

    private static String libelleEcart(CoherenceHoraire coherence) {
        if (!coherence.aUnEcart()) {
            return "";
        }
        String detail;
        if (coherence.demarrageHorsNuit() && coherence.arretHorsNuit()) {
            detail = "démarrage avant le coucher et arrêt après le lever du soleil";
        } else if (coherence.demarrageHorsNuit()) {
            detail = "démarrage avant le coucher du soleil";
        } else {
            detail = "arrêt après le lever du soleil";
        }
        return "⚠ Hors nuit : " + detail + " (une partie de l'enregistrement est diurne).";
    }

    private void reinitialiser() {
        enregistreur.set("");
        releveClimatiqueAbsent.set(false);
        gpsDisponible.set(false);
        mesures.clear();
        anomalies.clear();
        evenements.clear();
        temperature.set("—");
        coherenceHoraireDisponible.set(false);
        fenetreNuit.set("");
        alerteHorsNuit.set("");
    }

    /// Enregistreur de la nuit (`PR <n° de série>`).
    public ReadOnlyStringProperty enregistreurProperty() {
        return enregistreur.getReadOnlyProperty();
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

    /// `true` si la fenêtre nocturne a pu être calculée (GPS + horaires + latitude non polaire, #548).
    public ReadOnlyBooleanProperty coherenceHoraireDisponibleProperty() {
        return coherenceHoraireDisponible.getReadOnlyProperty();
    }

    /// Libellé de la fenêtre nocturne au point (`🌙 Nuit : coucher 21:58 · lever 05:48`), vide si
    /// indisponible.
    public ReadOnlyStringProperty fenetreNuitProperty() {
        return fenetreNuit.getReadOnlyProperty();
    }

    /// Alerte « hors nuit » quand l'enregistrement déborde de la fenêtre nocturne, vide sinon (#548).
    public ReadOnlyStringProperty alerteHorsNuitProperty() {
        return alerteHorsNuit.getReadOnlyProperty();
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
