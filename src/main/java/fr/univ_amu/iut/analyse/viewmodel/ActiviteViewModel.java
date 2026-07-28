package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.AgregationActivite;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.LigneActivite;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
    private final ObservableList<String> groupesDisponibles = FXCollections.observableArrayList();
    private final ObservableList<String> carresDisponibles = FXCollections.observableArrayList();
    private final ObservableList<String> pointsDisponibles = FXCollections.observableArrayList();
    private final ObservableList<String> nuitsDisponibles = FXCollections.observableArrayList();
    private final ObjectProperty<PlageNuit> plageNuit = new SimpleObjectProperty<>();

    /// Fenêtre **réellement enregistrée** du passage chargé, ou `null` en vue transverse (plusieurs nuits,
    /// donc plusieurs fenêtres). Borne la plage sur laquelle une tranche sans contact vaut zéro.
    private FenetreObserveeNuit.Bornes fenetreEnregistree;

    /// Retour de la dernière opération de l'écran (aujourd'hui l'export d'image), pour le bandeau
    /// mutualisé : un export qui réussit ou qui échoue sans rien dire se lit comme un clic sans effet.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    public ActiviteViewModel(ServiceActivite service) {
        this.service = service;
        tranche.addListener((observable, ancienne, nouvelle) -> reagreger());
        especesSelectionnees.addListener((SetChangeListener<String>) changement -> majCourbesAffichees());
    }

    /// Charge **un passage** : ses contacts datés et sa fenêtre nocturne, agrège avec la tranche courante,
    /// puis présélectionne les cinq espèces les plus contactées.
    public void chargerPassage(long idPassage) {
        fenetreEnregistree = service.fenetreEnregistree(idPassage).orElse(null);
        remplacerContacts(
                service.contactsDuPassage(idPassage),
                service.plageNuit(idPassage).orElse(null));
    }

    /// Charge **tous les passages** de l'utilisateur (vue transverse filtrable). Aucune fenêtre nocturne
    /// unique n'a de sens sur plusieurs nuits : l'aplat coucher/lever est laissé à `null` tant qu'un filtre
    /// n'a pas ramené la sélection à un seul passage.
    public void chargerUtilisateur(String idUtilisateur) {
        // Plusieurs nuits, donc plusieurs fenêtres d'enregistrement : aucune ne vaut pour l'ensemble. Les
        // zéros se borneront alors à la plage réellement observée.
        fenetreEnregistree = null;
        remplacerContacts(service.contactsDeLUtilisateur(idUtilisateur), null);
    }

    private void remplacerContacts(List<ContactHoraire> contacts, PlageNuit fenetre) {
        tous.setAll(contacts);
        groupesDisponibles.setAll(valeursDistinctes(ContactHoraire::groupe));
        carresDisponibles.setAll(valeursDistinctes(ContactHoraire::numeroCarre));
        pointsDisponibles.setAll(valeursDistinctes(ContactHoraire::codePoint));
        nuitsDisponibles.setAll(valeursDistinctes(ActiviteViewModel::libelleNuit));
        plageNuit.set(fenetre);
        reagreger();
        selectionnerLesPlusContactees();
    }

    /// Valeurs distinctes non nulles d'une dimension sur **tous** les contacts chargés (avant filtrage),
    /// triées : peuplent les listes déroulantes des critères (groupe, carré) au moment où on ajoute la puce.
    private List<String> valeursDistinctes(Function<ContactHoraire, String> dimension) {
        return tous.stream()
                .map(dimension)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /// Libellé de la nuit d'un contact (date du soir, [ContactHoraire#nuit]) pour le filtre « Nuit », ou
    /// `null` si le contact n'a pas d'heure.
    private static String libelleNuit(ContactHoraire contact) {
        return contact.nuit() == null ? null : contact.nuit().toString();
    }

    private void reagreger() {
        // Repliement sur une nuit : le sous-ensemble peut couvrir plusieurs nuits (vue transverse,
        // ou filtre laissant passer plusieurs passages). L'axe étant celui d'UNE nuit, les tranches de
        // même heure doivent être sommées, sans quoi la courbe repart en arrière à chaque nuit.
        List<CourbeEspece> repliees =
                AgregationActivite.replierSurLaNuit(AgregationActivite.parEspece(contactsFiltres, tranche.get()));
        // Comblement des creux : une tranche sans contact vaut zéro là où l'on écoutait, pour qu'un silence
        // ne se lise pas comme une droite reliant deux pics distants.
        especes.setAll(comblerLesCreux(repliees));
        majCourbesAffichees();
    }

    /// Comble les tranches sans contact par des **zéros**, sur la plage où l'on écoutait vraiment : la
    /// fenêtre enregistrée du passage quand elle est connue, sinon la **plage observée** (du premier au
    /// dernier contact du sous-ensemble, toutes espèces confondues). Hors de cette plage, l'absence de
    /// contact ne dit rien et la courbe s'abstient.
    private List<CourbeEspece> comblerLesCreux(List<CourbeEspece> courbes) {
        if (courbes.isEmpty()) {
            return courbes;
        }
        long debut;
        long fin;
        if (fenetreEnregistree != null) {
            debut = AgregationActivite.minutesSurLAxe(fenetreEnregistree.debut());
            fin = AgregationActivite.minutesSurLAxe(fenetreEnregistree.fin());
        } else {
            LongSummaryStatistics observee = courbes.stream()
                    .flatMap(courbe -> courbe.points().stream())
                    .mapToLong(point -> AgregationActivite.minutesSurLAxe(point.debutTranche()))
                    .summaryStatistics();
            debut = observee.getMin();
            fin = observee.getMax();
        }
        return AgregationActivite.comblerLesCreux(courbes, tranche.get(), debut, fin);
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

    /// Groupes taxonomiques présents dans les contacts chargés : peuple la liste déroulante du critère
    /// « Taxon parent ».
    public ObservableList<String> groupesDisponibles() {
        return groupesDisponibles;
    }

    /// Numéros de carré présents dans les contacts chargés : peuplent la liste déroulante du critère
    /// « Carré ».
    public ObservableList<String> carresDisponibles() {
        return carresDisponibles;
    }

    /// Codes de point d'écoute présents : peuplent la liste déroulante du critère « Point ».
    public ObservableList<String> pointsDisponibles() {
        return pointsDisponibles;
    }

    /// Nuits (dates du soir) présentes : peuplent la liste déroulante du critère « Nuit » (une nuit = un
    /// passage).
    public ObservableList<String> nuitsDisponibles() {
        return nuitsDisponibles;
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

    /// Message d'état vide **nommant la dimension responsable** (`""` si une courbe est tracée) : rien de
    /// chargé, filtres trop stricts, ou aucune espèce cochée. Un « aucune donnée » générique n'est pas
    /// actionnable ; nommer la cause l'est.
    public String messageEtatVide() {
        if (!courbesAffichees.isEmpty()) {
            return "";
        }
        if (tous.isEmpty()) {
            return "Aucune espèce détectée sur les nuits chargées.";
        }
        if (especes.isEmpty()) {
            return "Aucune espèce ne correspond aux filtres. Élargissez ou retirez un filtre.";
        }
        return "Aucune espèce sélectionnée. Cochez une espèce à afficher.";
    }

    /// Fenêtre nocturne (coucher → lever) à matérialiser sous la courbe, ou `null` si elle n'est pas
    /// calculable (passage sans GPS, nuit polaire) ou pas unique (vue multi-passages) : la vue trace alors
    /// sans aplat.
    public ObjectProperty<PlageNuit> plageNuitProperty() {
        return plageNuit;
    }

    /// Les lignes d'export du **sous-ensemble filtré**, à la tranche courante : ce que l'écran montre,
    /// mis à plat pour un tableur (une ligne par carré, point, nuit, espèce et tranche).
    ///
    /// Passe par [AgregationActivite#pourExport] et non par les courbes affichées : une courbe décrit le
    /// temps pour une espèce, tous lieux confondus, alors qu'un export doit se recouper (#2613). Les
    /// espèces **décochées** en sont donc absentes ? Non : l'export suit les **filtres**, pas la sélection
    /// de tracé — décocher une courbe allège le graphe, cela ne retire pas la donnée du jeu.
    public List<LigneActivite> lignesExport() {
        return AgregationActivite.pourExport(List.copyOf(contactsFiltres), tranche.get());
    }

    /// Signale un **export de données réussi**, en nommant le fichier écrit.
    public void signalerExportDonnees(String nomFichier, int lignes) {
        retour.set(RetourOperation.succes(lignes + " ligne(s) exportée(s) vers " + nomFichier + "."));
    }

    /// Retour de la **dernière opération** avec sa sévérité, pour le bandeau de feedback mutualisé
    /// ([RetourOperation#AUCUN] en nominal).
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Signale un **export réussi**, en nommant le fichier écrit : sans cela, un export qui a marché est
    /// indiscernable d'un clic sans effet.
    public void signalerExport(String nomFichier) {
        retour.set(RetourOperation.succes("Image exportée vers " + nomFichier + "."));
    }

    /// Signale un **échec d'export** (disque plein, dossier en lecture seule, rendu refusé). Le dire est le
    /// point : une exception avalée par le fil JavaFX laisse l'utilisateur devant un bouton qui ne fait rien.
    public void signalerEchecExport(String motif) {
        retour.set(RetourOperation.erreur("L'export d'image a échoué : " + motif));
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }
}
