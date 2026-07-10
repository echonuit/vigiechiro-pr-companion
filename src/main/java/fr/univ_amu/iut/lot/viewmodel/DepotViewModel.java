package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// ViewModel du **téléversement d'une nuit sur VigieChiro** (#142), distinct de [LotViewModel] : le dépôt
/// est un concern à part (et [LotViewModel] est déjà volumineux). Coordonne la résolution des séquences
/// (via [ServiceLot]) et le dépôt lui-même (via [DepotVigieChiro]).
///
/// Le dépôt est **optionnel** : `depot` est vide dans les injecteurs partiels de capture (feature `lot`
/// sans `connexion`, donc sans client HTTP) ; dans l'application complète il est présent (cf.
/// `DepotVigieChiroModule`). VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`).
public class DepotViewModel {

    private final ServiceLot service;
    private final Optional<DepotVigieChiro> depot;

    /// Téléversement en cours (posé pendant le travail hors fil JavaFX) : l'IHM désactive le bouton et
    /// affiche un état d'activité pendant l'upload, qui peut être long sur une grosse nuit (milliers de
    /// fichiers).
    private final ReadOnlyBooleanWrapper enCours = new ReadOnlyBooleanWrapper(this, "enCours", false);

    /// Message de restitution du dernier dépôt (succès résumé ou erreur), pour l'IHM.
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public DepotViewModel(ServiceLot service, Optional<DepotVigieChiro> depot) {
        this.service = Objects.requireNonNull(service, "service");
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    /// `true` si le téléversement est **disponible** dans ce contexte (application complète connectée à
    /// VigieChiro) : permet à l'IHM de masquer / désactiver l'action là où le dépôt n'a pas de sens.
    public boolean disponible() {
        return depot.isPresent();
    }

    /// Téléverse les **séquences transformées** du passage sur VigieChiro : crée la participation puis envoie
    /// les fichiers. **Bloquant** (réseau) : à appeler **hors du fil JavaFX** (le controller l'enveloppe dans
    /// un fil virtuel, comme la génération d'archives). Lève une [RegleMetierException] si le dépôt est
    /// indisponible dans ce contexte, ou s'il n'y a aucune séquence à déposer.
    ///
    /// @param idPassage passage (nuit) à déposer
    /// @return le bilan du dépôt (participation créée, fichiers déposés / en échec)
    public BilanDepot televerser(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        DepotVigieChiro depotVigieChiro =
                depot.orElseThrow(() -> new RegleMetierException("Dépôt VigieChiro indisponible dans ce contexte."));
        List<Path> fichiers = service.sequencesADeposer(idPassage);
        if (fichiers.isEmpty()) {
            throw new RegleMetierException("Aucune séquence transformée à déposer pour ce passage.");
        }
        return depotVigieChiro.deposer(idPassage, fichiers);
    }

    /// Signale le **début** du téléversement (au fil JavaFX, avant de lancer [#televerser] en arrière-plan).
    public void marquerEnCours() {
        message.set("Téléversement en cours… (cela peut prendre quelques minutes sur une grosse nuit).");
        enCours.set(true);
    }

    /// Restitue un dépôt **réussi** (au fil JavaFX, après [#televerser]) : résumé participation + fichiers
    /// déposés, et le détail des échecs éventuels (dépôt partiel relançable).
    public void appliquerBilan(BilanDepot bilan) {
        enCours.set(false);
        String resume = "Nuit déposée sur VigieChiro : " + bilan.deposees() + " fichier(s) téléversé(s).";
        message.set(bilan.estComplet() ? resume : resume + " " + bilan.echecs().size() + " en échec (à relancer).");
    }

    /// Restitue un **échec** de dépôt (au fil JavaFX) : message d'erreur métier / réseau.
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
