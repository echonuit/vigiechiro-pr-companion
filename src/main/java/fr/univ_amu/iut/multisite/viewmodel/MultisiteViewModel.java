package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.SavedView;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Multisite** (vue agrégée des passages de tous les sites de
/// l'utilisateur, parcours P5, story E5, statut **SHOULD**).
///
/// Expose le tableau des [lignes][LignePassage], les critères de **filtre** (numéro de carré,
/// statut, verdict, année) et de **tri** ([TriMultisite]), et l'**export CSV**. Tout changement de
/// filtre ou de tri **ré-interroge le service** et rafraîchit le tableau ([#rafraichir()]) : le
/// service filtre et trie côté métier, le VM ne fait que relayer.
///
/// Gère aussi les **vues sauvegardées** ([SavedView]) : enregistrer la combinaison de filtres
/// courante sous un nom, lister, appliquer (rejouer ses filtres), mettre à jour ou supprimer.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls
/// `javafx.beans`/`javafx.collections`. Non-singleton (un VM frais par chargement de vue).
public class MultisiteViewModel {

    private final ServiceMultisite service;
    private final String idUtilisateur;

    /// Vrai pendant l'application groupée de plusieurs filtres (réinitialisation, vue sauvegardée) :
    /// les listeners ne rafraîchissent pas à chaque propriété, un seul rafraîchissement suit le lot.
    private boolean chargementGroupe;

    private final StringProperty filtreNumeroCarre = new SimpleStringProperty(this, "filtreNumeroCarre", "");
    private final ObjectProperty<StatutWorkflow> filtreStatut = new SimpleObjectProperty<>(this, "filtreStatut");
    private final ObjectProperty<Verdict> filtreVerdict = new SimpleObjectProperty<>(this, "filtreVerdict");
    private final ObjectProperty<Integer> filtreAnnee = new SimpleObjectProperty<>(this, "filtreAnnee");
    private final ObjectProperty<TriMultisite> tri = new SimpleObjectProperty<>(this, "tri", TriMultisite.PAR_SITE);

    private final ObservableList<LignePassage> lignes = FXCollections.observableArrayList();
    private final ObservableList<SavedView> vues = FXCollections.observableArrayList();
    /// Agrégat des carrés pour la carte (#152) : vue d'ensemble **non filtrée** (carrés + points + statut).
    private final ObservableList<CarreAgrege> carresCarte = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper nonVide = new ReadOnlyBooleanWrapper(this, "nonVide", false);
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public MultisiteViewModel(ServiceMultisite service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        // Tout changement interactif de filtre ou de tri ré-interroge le service et rafraîchit le
        // tableau (sauf pendant une application groupée : un seul rafraîchissement la conclut).
        filtreNumeroCarre.addListener((obs, ancien, nouveau) -> rafraichirSiInteractif());
        filtreStatut.addListener((obs, ancien, nouveau) -> rafraichirSiInteractif());
        filtreVerdict.addListener((obs, ancien, nouveau) -> rafraichirSiInteractif());
        filtreAnnee.addListener((obs, ancien, nouveau) -> rafraichirSiInteractif());
        tri.addListener((obs, ancien, nouveau) -> rafraichirSiInteractif());
    }

    /// (Re)charge le tableau selon les filtres et le tri courants. À appeler à l'ouverture de
    /// l'écran ; ensuite déclenché automatiquement par tout changement de filtre ou de tri.
    public void rafraichir() {
        lignes.setAll(service.listerPassages(idUtilisateur, filtresCourants(), tri.get()));
        // Carte (#152) : vue d'ensemble non filtrée des carrés/points (indépendante des filtres du tableau).
        carresCarte.setAll(service.agregerPourCarte(idUtilisateur));
        nonVide.set(!lignes.isEmpty());
        resume.set(lignes.size() + " passage(s) affiché(s).");
        message.set("");
    }

    private void rafraichirSiInteractif() {
        if (!chargementGroupe) {
            rafraichir();
        }
    }

    private FiltresMultisite filtresCourants() {
        return new FiltresMultisite(
                texteOuNull(filtreNumeroCarre.get()), filtreStatut.get(), filtreVerdict.get(), filtreAnnee.get());
    }

    /// Applique un jeu de filtres d'un seul tenant (réinitialisation, vue sauvegardée) : les quatre
    /// critères sont posés sous garde, puis un unique rafraîchissement suit (au lieu de quatre).
    private void appliquerFiltres(FiltresMultisite filtres) {
        chargementGroupe = true;
        try {
            filtreNumeroCarre.set(filtres.numeroCarre() == null ? "" : filtres.numeroCarre());
            filtreStatut.set(filtres.statut());
            filtreVerdict.set(filtres.verdict());
            filtreAnnee.set(filtres.annee());
        } finally {
            chargementGroupe = false;
        }
        rafraichir();
    }

    /// Réinitialise tous les filtres (le tri est conservé), puis recharge le tableau une fois.
    public void reinitialiserFiltres() {
        appliquerFiltres(FiltresMultisite.aucun());
    }

    /// Exporte le tableau courant (lignes déjà filtrées et triées) en CSV vers `destination`
    /// (P5-CA5). Sans dossier, l'appel est ignoré ; le bilan (ou l'erreur) va dans
    /// [#messageProperty()].
    ///
    /// @param destination fichier cible choisi par l'observateur
    /// @return `true` si le fichier a été écrit
    public boolean exporter(Path destination) {
        if (destination == null) {
            return false;
        }
        try {
            service.exporterCsvVers(destination, lignes);
            message.set("Tableau exporté vers " + destination.getFileName() + " (" + lignes.size() + " ligne(s)).");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    // --- Vues sauvegardées (story E5.S3) ---

    /// Recharge la liste des vues sauvegardées (à appeler à l'ouverture de la modale de gestion).
    public void chargerVues() {
        vues.setAll(service.listerVues());
    }

    /// Enregistre la combinaison de filtres courante sous `nom`. Un nom vide est refusé.
    ///
    /// @return `true` si la vue a été enregistrée
    public boolean enregistrerVue(String nom) {
        if (nom == null || nom.isBlank()) {
            message.set("Donnez un nom à la vue avant de l'enregistrer.");
            return false;
        }
        try {
            service.enregistrerVue(nom.trim(), filtresCourants());
            chargerVues();
            message.set("Vue « " + nom.trim() + " » enregistrée.");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Applique les filtres d'une vue sauvegardée (rejoue la combinaison) puis recharge le tableau.
    ///
    /// @return `true` si la vue a été appliquée
    public boolean appliquerVue(SavedView vue) {
        if (vue == null) {
            return false;
        }
        try {
            appliquerFiltres(service.chargerVue(vue.id()));
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Met à jour une vue existante : son nom et la combinaison de filtres courante.
    ///
    /// @return `true` si la vue a été mise à jour
    public boolean mettreAJourVue(SavedView vue, String nom) {
        if (vue == null || nom == null || nom.isBlank()) {
            return false;
        }
        try {
            service.mettreAJourVue(vue.id(), nom.trim(), filtresCourants());
            chargerVues();
            message.set("Vue « " + nom.trim() + " » mise à jour.");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Supprime une vue sauvegardée.
    ///
    /// @return `true` si la vue a été supprimée
    public boolean supprimerVue(SavedView vue) {
        if (vue == null) {
            return false;
        }
        try {
            service.supprimerVue(vue.id());
            chargerVues();
            message.set("Vue supprimée.");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private static String texteOuNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    public ObservableList<LignePassage> lignes() {
        return lignes;
    }

    /// Agrégat des carrés pour la **carte** (#152) : carrés + points (GPS, statut dominant) de l'utilisateur,
    /// vue d'ensemble non filtrée. La couche `view` le traduit en marqueurs/emprises.
    public ObservableList<CarreAgrege> carresCarte() {
        return carresCarte;
    }

    public ObservableList<SavedView> vues() {
        return vues;
    }

    public StringProperty filtreNumeroCarreProperty() {
        return filtreNumeroCarre;
    }

    public ObjectProperty<StatutWorkflow> filtreStatutProperty() {
        return filtreStatut;
    }

    public ObjectProperty<Verdict> filtreVerdictProperty() {
        return filtreVerdict;
    }

    public ObjectProperty<Integer> filtreAnneeProperty() {
        return filtreAnnee;
    }

    public ObjectProperty<TriMultisite> triProperty() {
        return tri;
    }

    public ReadOnlyBooleanProperty nonVideProperty() {
        return nonVide.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
