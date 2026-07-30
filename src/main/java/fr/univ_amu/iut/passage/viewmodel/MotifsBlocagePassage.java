package fr.univ_amu.iut.passage.viewmodel;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// **Pourquoi** chacun des gestes de la fiche est fermé, quand il l'est (#789).
///
/// Le gating de M-Passage est **en amont** : on grise avec une explication plutôt que de laisser
/// découvrir le refus après confirmation. Chaque geste gatable a donc son motif, et ces motifs forment
/// un groupe cohérent : ils répondent tous à la même question, posée sur quatre objets différents.
///
/// Les tenir ensemble sort du [PassageViewModel] quatre propriétés et quatre accesseurs qui n'y
/// disaient rien de plus qu'ici, et rend visible ce qu'ils ont en commun. L'écran s'y lie de la même
/// façon : `viewModel.motifs().depot()` au lieu de `viewModel.motifBlocageDepotProperty()`.
///
/// Chaîne **vide** = le geste est ouvert, il n'y a rien à expliquer.
public final class MotifsBlocagePassage {

    private final ReadOnlyStringWrapper verification = new ReadOnlyStringWrapper(this, "verification", "");
    private final ReadOnlyStringWrapper reactivation = new ReadOnlyStringWrapper(this, "reactivation", "");
    private final ReadOnlyStringWrapper depot = new ReadOnlyStringWrapper(this, "depot", "");
    private final ReadOnlyStringWrapper annulationDepot = new ReadOnlyStringWrapper(this, "annulationDepot", "");

    /// Pourquoi « Vérifier l'enregistrement » est fermé.
    public ReadOnlyStringProperty verification() {
        return verification.getReadOnlyProperty();
    }

    /// Pourquoi « Réactiver ce passage » est fermé.
    public ReadOnlyStringProperty reactivation() {
        return reactivation.getReadOnlyProperty();
    }

    /// Pourquoi « Préparer le dépôt » est fermé.
    public ReadOnlyStringProperty depot() {
        return depot.getReadOnlyProperty();
    }

    /// Pourquoi « Annuler le dépôt » est fermé.
    public ReadOnlyStringProperty annulationDepot() {
        return annulationDepot.getReadOnlyProperty();
    }

    /// Pose les quatre motifs d'un coup : ils se calculent au même moment, depuis le même détail.
    void appliquer(String verification, String reactivation, String depot, String annulationDepot) {
        this.verification.set(verification);
        this.reactivation.set(reactivation);
        this.depot.set(depot);
        this.annulationDepot.set(annulationDepot);
    }

    /// Remet les quatre à vide : aucune fiche chargée, donc rien à expliquer.
    void effacer() {
        appliquer("", "", "", "");
    }
}
