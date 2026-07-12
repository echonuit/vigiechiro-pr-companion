package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /// Annulation coopérative (#1044) : le drapeau atomique est **lu par le moteur hors fil JavaFX**
    /// (entre deux fichiers) ; la propriété observable alimente l'IHM (bouton « Annulation… »). Posés au
    /// fil JavaFX par [#demanderAnnulation()], réarmés par [#marquerEnCours()].
    private final AtomicBoolean annulation = new AtomicBoolean(false);

    private final ReadOnlyBooleanWrapper annulationDemandee =
            new ReadOnlyBooleanWrapper(this, "annulationDemandee", false);

    /// Message de restitution du dernier dépôt (succès résumé ou erreur), pour l'IHM.
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Table de dépôt (#983) : une [LigneDepot] par unité suivie (`depot_unite`, #981), réhydratée à
    /// l'ouverture ([#rehydrater]) et mise à jour en direct pendant un dépôt (relais du controller).
    private final SuiviLignesDepot suiviLignes = new SuiviLignesDepot();

    public DepotViewModel(ServiceLot service, Optional<DepotVigieChiro> depot) {
        this.service = Objects.requireNonNull(service, "service");
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    /// `true` si le téléversement est **disponible** dans ce contexte (application complète connectée à
    /// VigieChiro) : permet à l'IHM de masquer / désactiver l'action là où le dépôt n'a pas de sens.
    public boolean disponible() {
        return depot.isPresent();
    }

    /// Téléverse le passage sur VigieChiro : crée la participation puis envoie les **archives ZIP** par
    /// défaut (#984, une archive = une unité, comme le web), ou les **séquences WAV** en repli si le disque
    /// ne permet pas de créer les archives. **Bloquant** (réseau) : à appeler **hors du fil JavaFX** (le
    /// controller l'enveloppe dans un fil virtuel, comme la génération d'archives). Lève une
    /// [RegleMetierException] si le dépôt est indisponible, si rien n'est déposable, ou si les archives ne
    /// sont pas encore générées alors que le disque le permet (invitation à lancer l'étape 2).
    ///
    /// @param idPassage passage (nuit) à déposer
    /// @return le bilan du dépôt (participation créée, fichiers déposés / en échec)
    public BilanDepot televerser(Long idPassage) {
        return televerser(idPassage, SuiviDepot.inerte());
    }

    /// Variante avec **suivi par unité** (#983) : `suivi` est notifié hors-thread au fil du dépôt
    /// reprenable (#982) ; la vue relaie au fil JavaFX vers [#suiviLignes()].
    public BilanDepot televerser(Long idPassage, SuiviDepot suivi) {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(suivi, "suivi");
        DepotVigieChiro depotVigieChiro =
                depot.orElseThrow(() -> new RegleMetierException("Dépôt VigieChiro indisponible dans ce contexte."));
        // Dépôt ZIP par défaut (#984), comme le web : une archive = une unité. Repli WAV seulement si le
        // disque ne permet pas de créer les archives ; sinon invitation à générer d'abord (étape 2).
        List<Path> fichiers = service.fichiersDepotParDefaut(idPassage);
        return depotVigieChiro.deposer(idPassage, fichiers, annulation::get, suivi);
    }

    /// Réhydrate la table de dépôt depuis l'état persisté (`depot_unite`, #981) : à appeler sur le fil
    /// JavaFX à l'ouverture de l'écran. Table vide si aucun dépôt automatique n'a été entamé.
    public void rehydrater(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        suiviLignes.planifier(service.unitesDepot(idPassage));
    }

    /// Table de dépôt observable (#983) : lignes à lier à la `TableView`, drapeau « reste à reprendre »
    /// pour basculer le bouton en « Retenter les échecs ».
    public SuiviLignesDepot suiviLignes() {
        return suiviLignes;
    }

    /// Signale le **début** du téléversement (au fil JavaFX, avant de lancer [#televerser] en arrière-plan).
    public void marquerEnCours() {
        message.set("Téléversement en cours… (cela peut prendre quelques minutes sur une grosse nuit).");
        annulation.set(false);
        annulationDemandee.set(false);
        enCours.set(true);
    }

    /// Demande l'**annulation coopérative** du dépôt en cours (#1044) : le moteur termine le fichier en
    /// vol puis s'arrête (jamais d'arrêt au milieu d'un fichier — aucune unité fantôme). Le passage reste
    /// « Dépôt en cours », « Reprendre le dépôt » ne renverra que les fichiers manquants. Au fil JavaFX.
    public void demanderAnnulation() {
        annulation.set(true);
        annulationDemandee.set(true);
    }

    /// `true` entre la demande d'annulation et la fin effective du dépôt : l'IHM désactive le bouton et
    /// affiche « Annulation… » le temps que le fichier en cours se termine.
    public ReadOnlyBooleanProperty annulationDemandeeProperty() {
        return annulationDemandee.getReadOnlyProperty();
    }

    /// Restitue un dépôt **réussi** (au fil JavaFX, après [#televerser]) : résumé participation + fichiers
    /// déposés, et le détail des échecs éventuels (dépôt partiel relançable).
    public void appliquerBilan(BilanDepot bilan) {
        enCours.set(false);
        // Dépôt interrompu à la demande (#1044) : le bilan de la tentative peut être « sans échec » alors
        // qu'il reste des fichiers à téléverser — le message le dit explicitement (compteurs de la table).
        if (annulationDemandee.get()) {
            message.set("Dépôt interrompu : " + suiviLignes.deposeesProperty().get() + "/"
                    + suiviLignes.totalProperty().get()
                    + " fichier(s) en ligne. « Reprendre le dépôt » ne renverra que les manquants.");
            return;
        }
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
