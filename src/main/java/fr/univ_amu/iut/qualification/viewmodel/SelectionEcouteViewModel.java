package fr.univ_amu.iut.qualification.viewmodel;

import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.qualification.model.ContexteVerification;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de la **sélection d'écoute** de l'écran M-Qualification (vérification par
/// échantillonnage, P3).
///
/// Porte l'identité de la nuit affichée dans le bandeau (carré/point/passage, plage horaire,
/// volumétrie), la **liste de la sélection** échantillonnée (séquences retenues, R12), la
/// progression d'écoute (R10) et les paramètres de (re)génération (méthode + taille, R12). Le
/// verdict est porté à part par [QualificationViewModel] : le controller câble les deux sur le
/// même `idPassage`.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans` et
/// `javafx.collections` sont importés, jamais `javafx.scene`. Non-singleton (un VM frais par FXML).
///
/// TODO (M-Qualification) : implémentez les corps des méthodes publiques (ouvrirSur, selectionner,
/// marquerCouranteEcoutee, regenerer) ; les propriétés observables sont fournies. Patron de
/// référence : SiteDetailViewModel (feature sites).
public class SelectionEcouteViewModel {

    private final ServiceQualification service;
    private Long idPassage;
    private Long idSelection;
    private ContexteSite contexteSite;

    // Bandeau identité de la nuit (lecture seule, dérivé de ContexteVerification).
    private final ReadOnlyStringWrapper titreContexte = new ReadOnlyStringWrapper(this, "titreContexte", "");
    private final ReadOnlyStringWrapper plageHoraire = new ReadOnlyStringWrapper(this, "plageHoraire", "");
    private final ReadOnlyStringWrapper volumetrie = new ReadOnlyStringWrapper(this, "volumetrie", "");
    private final ReadOnlyStringWrapper filAriane = new ReadOnlyStringWrapper(this, "filAriane", "");

    // Liste de la sélection + progression d'écoute + séquence courante.
    private final ObservableList<SequenceEnSelection> lignes = FXCollections.observableArrayList();
    private final ObjectProperty<SequenceEnSelection> sequenceCourante =
            new SimpleObjectProperty<>(this, "sequenceCourante");
    private final ReadOnlyObjectWrapper<Path> cheminSequenceCourante =
            new ReadOnlyObjectWrapper<>(this, "cheminSequenceCourante");
    private final ReadOnlyDoubleWrapper progression = new ReadOnlyDoubleWrapper(this, "progression", 0.0);
    private final ReadOnlyStringWrapper progressionTexte = new ReadOnlyStringWrapper(this, "progressionTexte", "");
    private final ObjectProperty<MethodeSelection> methode =
            new SimpleObjectProperty<>(this, "methode", MethodeSelection.REPARTITION_TEMPORELLE);
    private final IntegerProperty taille = new SimpleIntegerProperty(this, "taille", GenerateurSelection.TAILLE_DEFAUT);

    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public SelectionEcouteViewModel(ServiceQualification service) {
        this.service = Objects.requireNonNull(service, "service");
        // --solution--
        sequenceCourante.addListener((obs, ancien, nouveau) -> majCheminCourant(nouveau));
        // --end-solution--
    }

    /// Ouvre la sélection d'écoute du passage `idPassage` : bandeau de contexte et liste de la
    /// sélection (constituée à la volée si absente, R12). Appelée par la navigation après le
    /// chargement du FXML. Une erreur (passage introuvable, sans séquence) est restituée dans
    /// [#messageProperty()] sans lever.
    public void ouvrirSur(Long idPassage) {
        this.idPassage = idPassage;
        // TODO (M-Qualification) : chargez le contexte (bandeau) et la sélection d'écoute (créée à la
        //   volée si absente, R12), peuplez lignes et la progression ; en cas d'erreur, réinitialisez
        //   et publiez le message.
        // --solution--
        reinitialiser();
        try {
            appliquerContexte(service.chargerContexte(idPassage));
            SelectionDEcoute selection = service.ouvrirVerification(idPassage);
            this.idSelection = selection.id();
            lignes.setAll(service.detaillerSelection(idSelection));
            recalculerProgression();
            message.set("");
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
        // --end-solution--
    }

    // --solution--
    /// Remet la sélection à vide avant chaque (ré)ouverture et après un échec : ni la liste, ni le
    /// bandeau, ni le chemin du fichier courant d'un passage précédent ne doivent subsister (le VM
    /// est non-singleton, mais rien n'empêche une réouverture sur un autre passage).
    private void reinitialiser() {
        idSelection = null;
        contexteSite = null;
        titreContexte.set("");
        plageHoraire.set("");
        volumetrie.set("");
        filAriane.set("");
        sequenceCourante.set(null);
        lignes.clear();
        recalculerProgression();
    }
    // --end-solution--

    /// Sélectionne une ligne de la liste (met à jour le chemin du fichier courant pour l'écoute).
    public void selectionner(SequenceEnSelection ligne) {
        // TODO (M-Qualification) : mémorisez la séquence courante (sequenceCourante).
        // --solution--
        sequenceCourante.set(ligne);
        // --end-solution--
    }

    /// Marque la séquence courante comme écoutée (flag `listened`). Appelée au début de la lecture
    /// (R10). Sans effet si aucune séquence n'est sélectionnée ou si elle est déjà écoutée.
    public void marquerCouranteEcoutee() {
        // TODO (M-Qualification) : marquez la séquence courante écoutée (service.marquerSequenceEcoutee),
        //   mettez à jour la ligne et recalculez la progression (R10).
        // --solution--
        SequenceEnSelection courante = sequenceCourante.get();
        if (courante == null || idSelection == null || courante.ecoutee()) {
            return;
        }
        service.marquerSequenceEcoutee(idSelection, courante.sequence().id());
        int index = lignes.indexOf(courante);
        if (index >= 0) {
            SequenceEnSelection ecoutee = new SequenceEnSelection(courante.sequence(), courante.position(), true);
            lignes.set(index, ecoutee);
            sequenceCourante.set(ecoutee);
        }
        recalculerProgression();
        // --end-solution--
    }

    /// Régénère la sélection avec la méthode et la taille choisies (R12). Recharge la liste et remet
    /// la progression à zéro. Erreur restituée dans le message.
    public void regenerer() {
        // TODO (M-Qualification) : régénérez la sélection (service.creerSelection) avec methode+taille,
        //   rechargez la liste, remettez la progression à zéro.
        // --solution--
        try {
            SelectionDEcoute selection = service.creerSelection(idPassage, methode.get(), taille.get());
            this.idSelection = selection.id();
            lignes.setAll(service.detaillerSelection(idSelection));
            sequenceCourante.set(null);
            recalculerProgression();
            message.set("");
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
        }
        // --end-solution--
    }

    // --solution--
    private void appliquerContexte(ContexteVerification contexte) {
        String quadruplet = quadrupletLisible(contexte);
        titreContexte.set(quadruplet + " (" + contexte.annee() + ")");
        plageHoraire.set(contexte.date() + "  " + contexte.heureDebut() + " → " + contexte.heureFin());
        volumetrie.set(contexte.sequencesTotales()
                + " séquences · durée audible "
                + Formats.dureeLisible(contexte.dureeAudibleSecondes()));
        filAriane.set("‹ Mes sites › " + quadruplet + " › Vérifier l'enregistrement");
        contexteSite = new ContexteSite(contexte.numeroCarre(), contexte.codePoint(), contexte.nomSite());
    }

    /// Identité lisible du passage partagée par le bandeau et le fil d'Ariane : `Carré X / Point / N°
    /// K`.
    private static String quadrupletLisible(ContexteVerification contexte) {
        return "Carré " + contexte.numeroCarre() + " / " + contexte.codePoint() + " / N° " + contexte.numeroPassage();
    }

    private void majCheminCourant(SequenceEnSelection ligne) {
        String chemin = ligne == null ? null : ligne.sequence().cheminFichier();
        cheminSequenceCourante.set(chemin == null ? null : Path.of(chemin));
    }

    private void recalculerProgression() {
        int total = lignes.size();
        long ecoutees = lignes.stream().filter(SequenceEnSelection::ecoutee).count();
        progression.set(total == 0 ? 0.0 : (double) ecoutees / total);
        progressionTexte.set(
                total == 0
                        ? "Aucune séquence"
                        : ecoutees + " / " + total + " écoutées (" + Math.round(progression.get() * 100) + " %)");
    }
    // --end-solution--

    /// Titre de contexte du bandeau (ex. `Carré 640380 / A1 / N° 2 (2026)`).
    public ReadOnlyStringProperty titreContexteProperty() {
        return titreContexte.getReadOnlyProperty();
    }

    /// Plage horaire de la nuit (`date  début → fin`).
    public ReadOnlyStringProperty plageHoraireProperty() {
        return plageHoraire.getReadOnlyProperty();
    }

    /// Volumétrie de la nuit (`N séquences · durée audible Xh Ymin`).
    public ReadOnlyStringProperty volumetrieProperty() {
        return volumetrie.getReadOnlyProperty();
    }

    /// Fil d'Ariane affiché en tête : `‹ Mes sites › Carré X / Point › N° K › Vérifier
    /// l'enregistrement`.
    public ReadOnlyStringProperty filArianeProperty() {
        return filAriane.getReadOnlyProperty();
    }

    /// Contexte site (carré, code point, nom) du passage courant, pour la navigation de retour vers
    /// M-Passage. `null` tant qu'aucun passage n'est chargé.
    public ContexteSite contexteSite() {
        return contexteSite;
    }

    /// Liste observable de la sélection d'écoute (séquences retenues, ordonnées par position).
    public ObservableList<SequenceEnSelection> lignes() {
        return lignes;
    }

    /// Séquence sélectionnée dans la liste (écoute en cours).
    public ObjectProperty<SequenceEnSelection> sequenceCouranteProperty() {
        return sequenceCourante;
    }

    /// Chemin du fichier WAV de la séquence courante (pour le composant audio), `null` si aucune.
    public ReadOnlyObjectProperty<Path> cheminSequenceCouranteProperty() {
        return cheminSequenceCourante.getReadOnlyProperty();
    }

    /// Progression d'écoute de la sélection, de 0 à 1 (pilote la barre).
    public ReadOnlyDoubleProperty progressionProperty() {
        return progression.getReadOnlyProperty();
    }

    /// Libellé de progression (`12 / 30 écoutées (40 %)`).
    public ReadOnlyStringProperty progressionTexteProperty() {
        return progressionTexte.getReadOnlyProperty();
    }

    /// Méthode d'échantillonnage choisie pour la (re)génération (R12).
    public ObjectProperty<MethodeSelection> methodeProperty() {
        return methode;
    }

    /// Taille de sélection choisie pour la (re)génération.
    public IntegerProperty tailleProperty() {
        return taille;
    }

    /// Message d'erreur (passage introuvable, sans séquence), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
