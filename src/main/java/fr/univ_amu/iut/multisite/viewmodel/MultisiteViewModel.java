package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.multisite.model.FiltresMultisite;
import fr.univ_amu.iut.multisite.model.LignePassage;
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
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls
/// `javafx.beans`/`javafx.collections`. Non-singleton (un VM frais par chargement de vue). Les
/// **vues sauvegardées** (CRUD) sont ajoutées dans un incrément ultérieur.
public class MultisiteViewModel {

    private final ServiceMultisite service;
    private final String idUtilisateur;

    private final StringProperty filtreNumeroCarre = new SimpleStringProperty(this, "filtreNumeroCarre", "");
    private final ObjectProperty<StatutWorkflow> filtreStatut = new SimpleObjectProperty<>(this, "filtreStatut");
    private final ObjectProperty<Verdict> filtreVerdict = new SimpleObjectProperty<>(this, "filtreVerdict");
    private final ObjectProperty<Integer> filtreAnnee = new SimpleObjectProperty<>(this, "filtreAnnee");
    private final ObjectProperty<TriMultisite> tri = new SimpleObjectProperty<>(this, "tri", TriMultisite.PAR_SITE);

    private final ObservableList<LignePassage> lignes = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper nonVide = new ReadOnlyBooleanWrapper(this, "nonVide", false);
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public MultisiteViewModel(ServiceMultisite service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        // Tout changement de filtre ou de tri ré-interroge le service et rafraîchit le tableau.
        filtreNumeroCarre.addListener((obs, ancien, nouveau) -> rafraichir());
        filtreStatut.addListener((obs, ancien, nouveau) -> rafraichir());
        filtreVerdict.addListener((obs, ancien, nouveau) -> rafraichir());
        filtreAnnee.addListener((obs, ancien, nouveau) -> rafraichir());
        tri.addListener((obs, ancien, nouveau) -> rafraichir());
    }

    /// (Re)charge le tableau selon les filtres et le tri courants. À appeler à l'ouverture de
    /// l'écran ; ensuite déclenché automatiquement par tout changement de filtre ou de tri.
    public void rafraichir() {
        FiltresMultisite filtres = new FiltresMultisite(
                texteOuNull(filtreNumeroCarre.get()), filtreStatut.get(), filtreVerdict.get(), filtreAnnee.get());
        lignes.setAll(service.listerPassages(idUtilisateur, filtres, tri.get()));
        nonVide.set(!lignes.isEmpty());
        resume.set(lignes.size() + " passage(s) affiché(s).");
        message.set("");
    }

    /// Réinitialise tous les filtres (le tableau se recharge via les listeners ; le tri est conservé).
    public void reinitialiserFiltres() {
        filtreNumeroCarre.set("");
        filtreStatut.set(null);
        filtreVerdict.set(null);
        filtreAnnee.set(null);
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

    private static String texteOuNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    public ObservableList<LignePassage> lignes() {
        return lignes;
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
