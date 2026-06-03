package fr.univ_amu.iut.bibliotheque.viewmodel;

import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Bibliotheque** (bibliothèque de sons de référence, parcours P10,
/// story E8, statut **COULD**).
///
/// À l'ouverture, [#charger()] lit [ServiceBibliotheque#exporterBibliotheque()] : il sélectionne
/// les observations marquées « référence », expose la liste triée des [entrées][EntreeBiblio] (une
/// par son de référence) et un résumé. La sélection courante alimente le chemin du fichier son à
/// écouter (réutilisation du composant `AudioView`, comme M-Vision-Tadarida). [#exporter(Path)]
/// matérialise la bibliothèque sur disque (CSV récapitulatif + copie des fichiers son).
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls
/// `javafx.beans`/`javafx.collections`. Non-singleton (un VM frais par chargement de vue).
public class BibliothequeViewModel {

    private final ServiceBibliotheque service;

    /// Dernier export calculé par [#charger()], conservé pour la matérialisation disque
    /// (CSV + copie des sons) déclenchée par [#exporter(Path)] sans relire le service.
    private ExportBiblioSons export;

    private final ObservableList<EntreeBiblio> entrees = FXCollections.observableArrayList();
    private final ObjectProperty<EntreeBiblio> selection = new SimpleObjectProperty<>(this, "selection");
    private final ReadOnlyObjectWrapper<Path> cheminAudioCourant =
            new ReadOnlyObjectWrapper<>(this, "cheminAudioCourant");
    private final ReadOnlyBooleanWrapper biblioNonVide = new ReadOnlyBooleanWrapper(this, "biblioNonVide", false);
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public BibliothequeViewModel(ServiceBibliotheque service) {
        this.service = Objects.requireNonNull(service, "service");
        selection.addListener((obs, ancien, nouveau) -> majSelection(nouveau));
    }

    /// Charge (ou recharge) la bibliothèque : sélectionne les observations de référence, peuple la
    /// table et met à jour le résumé. À appeler à l'ouverture de l'écran.
    public void charger() {
        // Reset de la sélection avant de remplacer la liste : sur un rechargement, l'ancienne entrée
        // (et donc le chemin audio) pourrait avoir disparu de la bibliothèque rafraîchie. Le listener
        // de sélection remet alors cheminAudioCourant à null : on repart d'un état neutre cohérent.
        selection.set(null);
        export = service.exporterBibliotheque();
        entrees.setAll(export.entrees());
        biblioNonVide.set(!entrees.isEmpty());
        resume.set(
                entrees.isEmpty()
                        ? "Aucun son de référence : marquez des observations « référence » pendant la validation."
                        : entrees.size() + " son(s) de référence.");
        message.set("");
    }

    /// Exporte la bibliothèque vers `dossier` (P10) : écrit le CSV récapitulatif et copie les
    /// fichiers son existants. Sans bibliothèque chargée ou sans dossier, l'appel est ignoré ; le
    /// bilan (ou l'erreur d'écriture) est restitué dans [#messageProperty()].
    ///
    /// @param dossier répertoire de destination choisi par l'observateur
    /// @return `true` si l'export a réussi
    public boolean exporter(Path dossier) {
        if (export == null || dossier == null) {
            return false;
        }
        try {
            int copies = export.exporterVers(dossier);
            message.set("Bibliothèque exportée vers " + dossier + " : " + copies + " fichier(s) son + le récapitulatif "
                    + ExportBiblioSons.NOM_CSV + ".");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private void majSelection(EntreeBiblio courante) {
        cheminAudioCourant.set(
                courante == null || courante.cheminFichier() == null ? null : Path.of(courante.cheminFichier()));
    }

    public ObservableList<EntreeBiblio> entrees() {
        return entrees;
    }

    public ObjectProperty<EntreeBiblio> selectionProperty() {
        return selection;
    }

    public ReadOnlyObjectProperty<Path> cheminAudioCourantProperty() {
        return cheminAudioCourant.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty biblioNonVideProperty() {
        return biblioNonVide.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
