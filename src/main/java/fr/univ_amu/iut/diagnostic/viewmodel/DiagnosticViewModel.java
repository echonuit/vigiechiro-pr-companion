package fr.univ_amu.iut.diagnostic.viewmodel;

import fr.univ_amu.iut.commun.model.Completude;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
    /// Retour de la dernière opération, avec sa sévérité, rendu dans le bandeau partagé (ADR 0023 :
    /// le bandeau est le véhicule par défaut, le modal reste réservé à l'irréversible).
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Température en début de nuit (#106) : libellé d'affichage (`8,5 °C` / `—`).
    private final ReadOnlyStringWrapper temperature =
            new ReadOnlyStringWrapper(this, "temperature", Formats.VALEUR_ABSENTE);

    /// Cohérence horaires (#548) : fenêtre nocturne calculable au point d'écoute.
    private final ReadOnlyBooleanWrapper coherenceHoraireDisponible =
            new ReadOnlyBooleanWrapper(this, "coherenceHoraireDisponible", false);

    /// Signale un **export réussi**, en nommant le fichier écrit : un export muet est indiscernable d'un
    /// clic sans effet.
    public void signalerExport(String nomFichier) {
        retour.set(RetourOperation.succes("Graphe exporté vers " + nomFichier + "."));
    }

    /// Signale un **échec d'export** (disque plein, dossier en lecture seule, rendu refusé).
    public void signalerEchecExport(String motif) {
        retour.set(RetourOperation.erreur("L'export du graphe a échoué : " + motif));
    }

    /// La fenêtre nocturne **exploitable** (heures de coucher et de lever), pour situer les mesures sur
    /// le graphe. `null` tant qu'aucun diagnostic n'est chargé ; indisponible quand le point n'a pas de
    /// coordonnées.
    /// Les deux plages, exigée et enregistrée, ou la chaîne vide (#4988).
    public ReadOnlyStringProperty plagesHorairesProperty() {
        return plagesHoraires.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<CoherenceHoraire> coherenceHoraireProperty() {
        return coherence.getReadOnlyProperty();
    }

    /// Libellé de la fenêtre nocturne (`Nuit : coucher 21:58 · lever 05:48`), vide si indisponible.
    /// L'icône lune est posée par la vue (FontIcon), pas ici (le VM ignore l'IHM).
    private final ReadOnlyStringWrapper fenetreNuit = new ReadOnlyStringWrapper(this, "fenetreNuit", "");

    /// Ce que le protocole attendait et ce qui a été enregistré, sous la fenêtre nocturne (#4988).
    ///
    /// Vide quand la vérification est indisponible : un attendu sans son obtenu laisserait croire à
    /// une mesure qui n'a pas eu lieu.
    private final ReadOnlyStringWrapper plagesHoraires = new ReadOnlyStringWrapper(this, "plagesHoraires", "");

    /// La cohérence horaire brute du diagnostic courant, ou `null` tant que rien n'est chargé. Le libellé
    /// n'en dit que le texte ; l'aplat de la nuit sur le graphe a besoin des **heures** (#2617).
    ///
    /// **Observable**, et non un simple champ : le graphe se reconstruit dès que les mesures changent,
    /// ce qui arrive **avant** que la cohérence ne soit posée. Un champ obligerait l'appelant à respecter
    /// un ordre implicite entre deux affectations : le genre de couplage qui se recasse en silence au
    /// premier remaniement.
    private final ReadOnlyObjectWrapper<CoherenceHoraire> coherence =
            new ReadOnlyObjectWrapper<>(this, "coherence", null);

    /// Ce que l'écart au protocole vaut, [RetourOperation#AUCUN] si les horaires sont
    /// cohérents. Un [RetourOperation] plutôt qu'une chaîne : sa sévérité (AVERTISSEMENT) est portée par la
    /// **donnée** et non par une classe CSS figée dans le FXML (#2050). Le label inline la rend via
    /// [LibelleRetour][fr.univ_amu.iut.commun.view.LibelleRetour] ; la barre de statut, neutre (ADR 0039),
    /// n'en consomme que le texte.
    /// Ce que l'encart annonce de la fin de la nuit (#5093). Second axe, distinct de la couverture.
    private final ReadOnlyObjectWrapper<RetourOperation> nuitInterrompue =
            new ReadOnlyObjectWrapper<>(this, "nuitInterrompue", RetourOperation.AUCUN);

    private final ReadOnlyObjectWrapper<RetourOperation> alerteHorsNuit =
            new ReadOnlyObjectWrapper<>(this, "alerteHorsNuit", RetourOperation.AUCUN);

    public DiagnosticViewModel(ServiceDiagnostic service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// Ouvre le diagnostic du passage `idPassage`. Une erreur (passage/session introuvable) est
    /// restituée dans [#retourProperty()] sans lever, l'écran restant vide.
    public void ouvrirSur(Long idPassage) {
        reinitialiser();
        try {
            appliquer(service.diagnostiquer(idPassage));
            retour.set(RetourOperation.AUCUN);
        } catch (RuntimeException echec) {
            reinitialiser();
            retour.set(RetourOperation.erreur(echec));
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
        nuitInterrompue.set(libelleCompletude(diagnostic.completude()));
    }

    /// Ce que l'encart annonce de la **fin** de la nuit (#5093).
    ///
    /// La gravité se décide ici, comme celle de la couverture : [Completude] nomme un état du domaine
    /// (ADR 0038, et ADR 4984 qui l'applique).
    ///
    /// Visible pour le banc de parité : le terminal porte le même verdict.
    public static RetourOperation libelleCompletude(Completude completude) {
        // INCONNUE se TAIT. Elle ne passe ni pour une interruption - on accuserait une nuit sur une
        // absence de trace - ni pour une nuit entière, ce que la prose ci-dessous dit explicitement.
        // C'est la règle de #5071 au calcul et de #5084 au report, tenue ici une troisième fois.
        return switch (completude) {
            case TRONQUEE ->
                RetourOperation.avertissement("Le journal du capteur montre que cette nuit s'est"
                        + " interrompue avant son terme : les enregistrements s'arrêtent là.");
            case COMPLETE -> RetourOperation.info("Le journal du capteur atteste une fin de nuit normale.");
            case INCONNUE -> RetourOperation.AUCUN;
        };
    }

    private void appliquerCoherence(CoherenceHoraire coherence) {
        this.coherence.set(coherence);
        coherenceHoraireDisponible.set(coherence.disponible());
        if (!coherence.disponible()) {
            fenetreNuit.set("");
            plagesHoraires.set("");
            alerteHorsNuit.set(RetourOperation.AUCUN);
            return;
        }
        fenetreNuit.set("Nuit : coucher " + HEURE.format(coherence.coucherSoleil()) + " · lever "
                + HEURE.format(coherence.leverSoleil()));
        plagesHoraires.set(PlagesHoraires.lisible(coherence));
        alerteHorsNuit.set(libelleEcart(coherence));
    }

    /// Ce que l'encart annonce du verdict de couverture.
    ///
    /// **La gravité se décide ICI**, et non dans le modèle (#4984) : [CoherenceHoraire.Couverture]
    /// nomme un état du domaine, cette méthode en fait une information ou un avertissement. C'est ce
    /// que l'ADR 0038 demande, une seule échelle de sévérité.
    ///
    /// Visible pour le banc de parité : le terminal porte le même verdict par
    /// `Diagnostiquer.coherenceLisible`, et deux surfaces qui trancheraient différemment la même
    /// nuit ne se contrediraient nulle part ailleurs (ADR 0014).
    public static RetourOperation libelleEcart(CoherenceHoraire coherence) {
        // Une couverture tenue est une INFORMATION : le protocole est un plancher, et le dépasser
        // n'est pas un défaut. La rendre comme un défaut reproduirait le mal qu'on corrige.
        return switch (coherence.couverture()) {
            case INCOMPLETE ->
                // La phrase dit le FAIT, et non plus seulement la règle (#5200). Elle énonçait ce que
                // le protocole exige, et laissait chercher ailleurs ce qui s'était passé : un verdict
                // qu'il fallait croire sur parole, exactement ce que #4988 avait entrepris de faire
                // cesser en posant les deux plages sous la fenêtre.
                //
                // Le terminal, lui, disait déjà le fait. Cette ligne rapproche l'écran de lui.
                RetourOperation.avertissement("Les enregistrements ne couvrent pas toute la fenêtre du"
                        + " protocole : elle va de " + PlagesHoraires.plageExigee(coherence)
                        + ", ils vont de " + PlagesHoraires.plageEnregistree(coherence) + ".");
            case COUVERTE -> RetourOperation.info("L'enregistrement couvre la fenêtre du protocole, et la dépasse.");
            case INDISPONIBLE -> RetourOperation.AUCUN;
        };
    }

    private void reinitialiser() {
        enregistreur.set("");
        releveClimatiqueAbsent.set(false);
        gpsDisponible.set(false);
        mesures.clear();
        anomalies.clear();
        evenements.clear();
        temperature.set(Formats.VALEUR_ABSENTE);
        coherenceHoraireDisponible.set(false);
        fenetreNuit.set("");
        alerteHorsNuit.set(RetourOperation.AUCUN);
        nuitInterrompue.set(RetourOperation.AUCUN);
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

    /// Libellé de la fenêtre nocturne au point (`Nuit : coucher 21:58 · lever 05:48`), vide si
    /// indisponible.
    public ReadOnlyStringProperty fenetreNuitProperty() {
        return fenetreNuit.getReadOnlyProperty();
    }

    /// Ce que l'encart dit de la fin de la nuit, [RetourOperation#AUCUN] quand le journal ne permet
    /// pas de conclure (#5093).
    public ReadOnlyObjectProperty<RetourOperation> nuitInterrompueProperty() {
        return nuitInterrompue.getReadOnlyProperty();
    }

    /// Ce que l'écart à la fenêtre exigée vaut pour l'observateur, [RetourOperation#AUCUN]
    /// sinon (#548).
    public ReadOnlyObjectProperty<RetourOperation> alerteHorsNuitProperty() {
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
    /// Retour de la **dernière opération** avec sa sévérité, pour le bandeau de l'écran.
    /// [RetourOperation#AUCUN] en nominal.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }
}
