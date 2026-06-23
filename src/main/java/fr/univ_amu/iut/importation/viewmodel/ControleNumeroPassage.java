package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Pré-contrôle d'unicité R5 **proactif** (#108) du rattachement de l'assistant M-Import.
///
/// Extrait de [ImportationViewModel] (même esprit que [ApercuPrefixe] et les sous-VM #183) pour garder
/// l'orchestrateur cohésif : détecter qu'un n° de passage est **déjà pris** pour un point et une année,
/// proposer le **prochain n° libre**, et corriger en un clic, est une préoccupation autonome. Ce
/// collaborateur **observe lui-même** le rattachement (point / année / n° de passage) et entretient son
/// état observable ; l'orchestrateur se contente de l'exposer et de composer `peutImporter`.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est importé,
/// jamais `javafx.scene`. La lecture passe par le **service** (un VM ne touche jamais le DAO).
final class ControleNumeroPassage {

    private final ServiceImport serviceImport;
    private final RattachementImportViewModel rattachement;

    /// `true` quand le quadruplet (point, année, n° de passage) courant correspond déjà à un passage : un
    /// import échouerait (R5). Bloque `peutImporter` et déclenche l'avertissement.
    private final ReadOnlyBooleanWrapper dejaUtilise = new ReadOnlyBooleanWrapper(this, "dejaUtilise", false);

    /// Avertissement lisible (vide si le n° est libre) : explique le doublon et propose le n° libre.
    private final ReadOnlyStringWrapper avertissement = new ReadOnlyStringWrapper(this, "avertissement", "");

    /// Dernier « prochain n° libre » calculé, proposé en un clic. Sur le seul fil JavaFX (renseigné par
    /// [#verifier()]) : un simple champ suffit.
    private int prochainNumeroLibre = 1;

    ControleNumeroPassage(ServiceImport serviceImport, RattachementImportViewModel rattachement) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.rattachement = Objects.requireNonNull(rattachement, "rattachement");
        // Recalcule le pré-contrôle dès qu'un champ déterminant du rattachement change.
        rattachement.pointSelectionneProperty().addListener((obs, ancien, nouveau) -> verifier());
        rattachement.anneeProperty().addListener((obs, ancien, nouveau) -> verifier());
        rattachement.numeroPassageProperty().addListener((obs, ancien, nouveau) -> verifier());
    }

    /// Recalcule le pré-contrôle pour le rattachement courant. Sans point sélectionné ou avec un n° &lt; 1
    /// (rattachement incomplet), l'avertissement est vide : rien à signaler tant que la saisie n'est pas
    /// exploitable.
    private void verifier() {
        PointDEcoute point = rattachement.pointSelectionneProperty().get();
        int annee = rattachement.anneeProperty().get();
        int numero = rattachement.numeroPassageProperty().get();
        if (point == null || numero < 1) {
            dejaUtilise.set(false);
            avertissement.set("");
            return;
        }
        if (serviceImport.numeroPassageDejaUtilise(point.id(), annee, numero)) {
            prochainNumeroLibre = serviceImport.prochainNumeroPassageLibre(point.id(), annee);
            dejaUtilise.set(true);
            avertissement.set("⚠ Le passage n° " + numero + " existe déjà pour ce point en " + annee
                    + ". Prochain n° libre : " + prochainNumeroLibre + ".");
        } else {
            dejaUtilise.set(false);
            avertissement.set("");
        }
    }

    /// Renseigne le n° de passage du rattachement avec le **prochain n° libre** proposé : corrige un
    /// doublon en un clic. Sans effet si aucun doublon n'est signalé.
    void utiliserProchainNumeroLibre() {
        if (dejaUtilise.get()) {
            rattachement.numeroPassageProperty().set(prochainNumeroLibre);
        }
    }

    /// `true` si le n° de passage courant est déjà pris (R5).
    boolean estDejaUtilise() {
        return dejaUtilise.get();
    }

    ReadOnlyBooleanProperty dejaUtiliseProperty() {
        return dejaUtilise.getReadOnlyProperty();
    }

    ReadOnlyStringProperty avertissementProperty() {
        return avertissement.getReadOnlyProperty();
    }
}
