package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Anticipe l'espace disque nécessaire aux archives de dépôt, **au chargement** : compare la taille
/// estimée (compression comprise) à l'espace disponible, pour désactiver « Générer » et l'expliquer
/// **avant** le clic plutôt qu'après un échec.
///
/// Ce qu'elle produit est un **avertissement d'état**, pas un compte rendu d'opération : il décrit une
/// contrainte de la machine, il reste tant qu'elle dure, et il ne se ferme pas. C'est pourquoi il ne
/// rejoint pas le bandeau de retour de l'écran lors du chantier #1890, contrairement aux messages qui
/// rendaient compte d'une action.
///
/// Extraite de [LotViewModel] (#1890) : le calcul ne dépend que du service et de l'état chargé, et
/// l'orchestration du dépôt n'a pas à le porter (cohésion, plafond `GodClass` du portail qualité).
/// Agnostique de l'IHM (seuls `javafx.beans`).
///
/// **Toujours dimensionnée sur le lot entier, et c'est voulu** (#1996). Le dépôt, lui, est passé à un
/// pipeline qui ne matérialise jamais plus que sa fenêtre : son seuil à lui a donc été rebaissé
/// (`ChoixSourceDepot`). Mais cette classe garde l'étape ② « Générer les archives », qui écrit
/// réellement **tout** le lot d'un coup pour le dépôt manuel. Lui appliquer le seuil du pipeline la
/// laisserait démarrer puis échouer à mi-parcours en laissant des archives partielles : exactement ce
/// que #769 avait créé ce garde-fou pour empêcher. Deux opérations différentes, deux seuils différents.
final class AnticipationEspaceDisque {

    private final ServiceLot service;

    private final ReadOnlyBooleanWrapper suffisant = new ReadOnlyBooleanWrapper(this, "suffisant", true);
    private final ReadOnlyStringWrapper raison = new ReadOnlyStringWrapper(this, "raison", "");

    AnticipationEspaceDisque(ServiceLot service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// `true` si l'espace estimé suffit ; faux → « Générer » désactivé et [#raisonProperty] explique.
    ReadOnlyBooleanProperty suffisantProperty() {
        return suffisant.getReadOnlyProperty();
    }

    /// Explication (non vide) quand l'espace est jugé insuffisant. Vide s'il suffit ou est indéterminé.
    ReadOnlyStringProperty raisonProperty() {
        return raison.getReadOnlyProperty();
    }

    /// Recalcule depuis l'état chargé. Indéterminé (génération non pertinente, volume ou chemin inconnu,
    /// disque illisible) → on ne bloque pas.
    void majDepuis(EtatLot etat) {
        boolean generationPertinente =
                etat.statut() == StatutWorkflow.PRET_A_DEPOSER || etat.statut() == StatutWorkflow.DEPOSE;
        Long volume = etat.volumeSequencesOctets();
        long disponible = service.espaceDisqueDisponible(etat.cheminDossier());
        long requis = volume == null ? 0L : service.estimationTailleDepotOctets(volume);
        boolean insuffisant = generationPertinente && volume != null && disponible > 0 && disponible < requis;
        suffisant.set(!insuffisant);
        raison.set(insuffisant ? FormatsLot.messageEspaceInsuffisant(requis, disponible) : "");
    }

    /// Remet l'anticipation à son état neutre (changement de passage) : on ne bloque pas.
    void reinitialiser() {
        suffisant.set(true);
        raison.set("");
    }
}
