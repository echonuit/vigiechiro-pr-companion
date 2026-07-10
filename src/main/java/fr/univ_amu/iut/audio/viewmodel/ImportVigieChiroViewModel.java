package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// ViewModel de l'**import des résultats VigieChiro** (axe 4.2), distinct de [AudioViewModel] : concern à
/// part, et ce VM était déjà volumineux. Coordonne le service [ImportVigieChiro] (réseau + import) et
/// l'état IHM (en cours / message). Le rafraîchissement de la liste d'observations après import est du
/// ressort de l'appelant (`SonsValidationController` réouvre la source).
///
/// L'import est **optionnel** : `importateur` est vide dans les injecteurs partiels de capture (feature
/// `audio` assemblée sans `connexion`, donc sans client HTTP), présent dans l'application complète (cf.
/// `ImportVigieChiroModule`). VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`).
public class ImportVigieChiroViewModel {

    private final Optional<ImportVigieChiro> importateur;

    /// Import en cours (posé pendant le travail hors fil JavaFX) : l'IHM désactive l'action et affiche un
    /// état d'activité pendant la récupération réseau, qui peut être longue (milliers de fichiers).
    private final ReadOnlyBooleanWrapper enCours = new ReadOnlyBooleanWrapper(this, "enCours", false);

    /// Message de restitution du dernier import (résumé de succès ou erreur), pour l'IHM.
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public ImportVigieChiroViewModel(Optional<ImportVigieChiro> importateur) {
        this.importateur = Objects.requireNonNull(importateur, "importateur");
    }

    /// `true` si l'import VigieChiro est **disponible** dans ce contexte (application connectée) : permet à
    /// l'IHM de masquer / désactiver l'action là où il n'a pas de sens.
    public boolean disponible() {
        return importateur.isPresent();
    }

    /// `true` si le passage est déjà **rattaché** à une participation (importable sans saisie), `false`
    /// sinon (pas encore déposé, ou import indisponible).
    public boolean rattache(Long idPassage) {
        return importateur.map(imp -> imp.estRattache(idPassage)).orElse(false);
    }

    /// Importe les résultats de la participation rattachée au passage. **Bloquant** (réseau) : à appeler
    /// **hors du fil JavaFX**. Ne mute aucun état observable ; l'appelant applique le résultat au fil JavaFX
    /// ([#appliquerBilan] / [#echec]). Lève une [RegleMetierException] si l'import est indisponible, si le
    /// passage n'est rattaché à aucune participation, ou si aucun résultat n'est disponible.
    ///
    /// @return le bilan de l'import
    public BilanImport importer(Long idPassage, boolean remplacer) {
        return importateur
                .orElseThrow(() -> new RegleMetierException("Import VigieChiro indisponible dans ce contexte."))
                .importer(idPassage, remplacer);
    }

    /// Signale le **début** de l'import (au fil JavaFX, avant de lancer [#importer] en arrière-plan).
    public void marquerEnCours() {
        message.set("Récupération des résultats Tadarida depuis VigieChiro…");
        enCours.set(true);
    }

    /// Restitue un import **réussi** (au fil JavaFX) : résumé du bilan (nombre d'observations importées).
    public void appliquerBilan(BilanImport bilan) {
        enCours.set(false);
        message.set("Résultats importés depuis VigieChiro : " + bilan.importees() + " observation(s).");
    }

    /// Restitue un **échec** d'import (au fil JavaFX) : message d'erreur métier / réseau.
    public void echec(String erreur) {
        enCours.set(false);
        message.set(erreur);
    }

    public ReadOnlyBooleanProperty enCoursProperty() {
        return enCours.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
