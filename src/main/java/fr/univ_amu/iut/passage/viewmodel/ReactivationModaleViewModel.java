package fr.univ_amu.iut.passage.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import fr.univ_amu.iut.passage.model.CompteRenduReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import java.util.List;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// ViewModel de la modale **« Réactiver ce passage »** (`ReactivationModale.fxml`, #1780).
///
/// La réactivation enchaîne deux phases longues - la **régénération / le rebranchement** des séquences
/// (disque) puis, sur un passage reconstruit, l'**acquisition de l'ancrage** (réseau VigieChiro, #1571).
/// Chacune a **sa** progression ([#progressionRegeneration], [#progressionAncrage]) : la modale les montre
/// sur deux barres, pour que la barre ne reste plus **figée à 100 %** pendant l'ancrage silencieux (#1780).
///
/// Le compte rendu est **honnête** : il dit combien de séquences ont été rebranchées et **sur quelle
/// preuve**, combien ont été refusées et pourquoi, et ce qui manque encore (formatage repris de l'ancienne
/// `ActionReactivation`, désormais affiché **dans** la modale plutôt qu'en notification).
public class ReactivationModaleViewModel {

    /// Nombre d'écarts détaillés dans le compte rendu : au-delà, on résume (une nuit peut en compter des
    /// milliers, et la modale doit rester lisible).

    /// Combien d'absences on nomme avant de résumer : assez pour identifier le motif dominant, pas assez
    /// pour noyer la modale quand une nuit entière manque.

    /// Progression de la phase **disque** (régénération / rebranchement des séquences), 0 -> 1.
    private final ProgressionOperation progressionRegeneration = new ProgressionOperation();

    /// Progression de la phase **réseau** (acquisition de l'ancrage des observations, #1571), 0 -> 1. Ne
    /// bouge que sur un passage reconstruit dont l'audio est revenu : sinon la phase ne se déclenche pas.
    private final ProgressionOperation progressionAncrage = new ProgressionOperation();

    private final ReadOnlyObjectWrapper<CompteRendu> compteRendu =
            new ReadOnlyObjectWrapper<>(CompteRendu.de("", List.of()));
    private final ReadOnlyStringWrapper erreur = new ReadOnlyStringWrapper("");

    /// Vrai dès qu'une réactivation s'est **conclue** : l'écran appelant recharge alors ses volumes et
    /// boutons (l'audio a pu revenir, le passage redevenir écoutable).
    private final ReadOnlyBooleanWrapper reactive = new ReadOnlyBooleanWrapper(false);

    @Inject
    public ReactivationModaleViewModel() {
        // Aucune dépendance : le travail (réseau + base) est fourni par l'appelant (PassageViewModel), la
        // modale ne porte que la présentation. Instancié à neuf par modale (non-singleton) : état propre.
    }

    /// Progression de la phase disque (régénération / rebranchement), à lier à la première barre.
    public ProgressionOperation progressionRegeneration() {
        return progressionRegeneration;
    }

    /// Progression de la phase réseau (ancrage), à lier à la seconde barre - révélée seulement quand la
    /// phase démarre.
    public ProgressionOperation progressionAncrage() {
        return progressionAncrage;
    }

    /// Compte rendu de fin, lacunes comprises. Vide tant que l'opération n'est pas conclue.
    public ReadOnlyObjectProperty<CompteRendu> compteRenduProperty() {
        return compteRendu.getReadOnlyProperty();
    }

    /// Échec ou refus (dossier introuvable, plateforme injoignable), affiché comme tel - distinct du compte
    /// rendu, qui n'est pas un incident.
    public ReadOnlyStringProperty erreurProperty() {
        return erreur.getReadOnlyProperty();
    }

    /// Vrai dès qu'une réactivation s'est conclue : l'appelant doit recharger sa fiche.
    public ReadOnlyBooleanProperty reactiveProperty() {
        return reactive.getReadOnlyProperty();
    }

    /// Publie le compte rendu (**fil JavaFX**) et marque [#reactiveProperty] : l'opération s'est conclue,
    /// l'écran appelant se rechargera à la fermeture.
    public void restituer(RapportReactivation rapport) {
        compteRendu.set(CompteRenduReactivation.de(rapport));
        erreur.set("");
        reactive.set(true);
    }

    /// Route un échec vers le message d'erreur de la modale : un refus (dossier introuvable, plateforme
    /// injoignable) **dit quoi faire**, il ne disparaît pas dans le fil de fond.
    public void signalerErreur(Throwable echec) {
        String detail = echec.getMessage();
        erreur.set(detail != null && !detail.isBlank() ? detail : "Réactivation impossible.");
    }

    /// Annulation demandée : état **neutre**, pas une erreur. Rien n'a été supprimé (la réactivation
    /// **ajoute** de l'audio, elle n'en retire pas), et on le dit.
    public void signalerAnnulation() {
        erreur.set("");
        compteRendu.set(CompteRendu.de(
                "Réactivation annulée", List.of(Constat.de("Aucun fichier n'a été modifié.", Severite.INFO))));
    }
}
