package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/// État **dérivé de la sélection** de la table audio, extrait du [AudioViewModel] (cohésion, seuil PMD
/// GodClass). Cinq drapeaux observables, recalculés à chaque changement de ligne sélectionnée, qui pilotent
/// l'**activation** et les **libellés** des boutons de la barre d'actions :
///
/// - `presente` : une ligne est sélectionnée ;
/// - `avecObservation` : la ligne est une **observation** (`idObservation` non nul, ≠ séquence non identifiée) ;
/// - `avecTadarida` : l'observation porte une **proposition Tadarida** (pilote « Valider ») ;
/// - `reference` : l'observation est en **référence** (libellé du bouton bascule) ;
/// - `douteux` : l'observation est marquée **douteuse** (#160 ; libellé du bouton bascule).
public final class EtatSelectionAudio {

    private final ReadOnlyBooleanWrapper presente = new ReadOnlyBooleanWrapper(this, "presente", false);
    private final ReadOnlyBooleanWrapper avecObservation = new ReadOnlyBooleanWrapper(this, "avecObservation", false);
    private final ReadOnlyBooleanWrapper avecTadarida = new ReadOnlyBooleanWrapper(this, "avecTadarida", false);
    private final ReadOnlyBooleanWrapper reference = new ReadOnlyBooleanWrapper(this, "reference", false);
    private final ReadOnlyBooleanWrapper douteux = new ReadOnlyBooleanWrapper(this, "douteux", false);

    /// Recalcule les cinq drapeaux depuis la ligne `courant` (ou `null` = aucune sélection).
    void maj(LigneObservationAudio courant) {
        boolean present = courant != null;
        boolean obs = present && courant.idObservation() != null;
        presente.set(present);
        avecObservation.set(obs);
        avecTadarida.set(obs && courant.taxonTadarida() != null);
        reference.set(present && courant.reference());
        douteux.set(present && courant.douteux());
    }

    public ReadOnlyBooleanProperty presenteProperty() {
        return presente.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty avecObservationProperty() {
        return avecObservation.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty avecTadaridaProperty() {
        return avecTadarida.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty referenceProperty() {
        return reference.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty douteuxProperty() {
        return douteux.getReadOnlyProperty();
    }
}
