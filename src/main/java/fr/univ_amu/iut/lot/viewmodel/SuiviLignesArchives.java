package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ArchivePlanifiee;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Table de suivi des archives de dépôt (#820) côté ViewModel : détient les [LigneArchive] observables et
/// applique le cycle de vie reçu du [fr.univ_amu.iut.lot.model.SuiviArchives] (plan établi → démarrée →
/// progresse → terminée / échec). Chaque événement cible sa ligne par **numéro** (la compression étant
/// parallèle #814, ils arrivent dans le désordre) ; un numéro inconnu est ignoré sans erreur.
///
/// **Fil JavaFX** : ces méthodes mutent des collections/propriétés observables ; l'appelant (le
/// controller) les invoque via `Platform.runLater`, comme le callback de progression global.
public final class SuiviLignesArchives {

    private final ObservableList<LigneArchive> lignes = FXCollections.observableArrayList();
    private final ObservableList<LigneArchive> lignesNonModifiables = FXCollections.unmodifiableObservableList(lignes);

    /// Vue **non modifiable** des lignes, à lier directement à la `TableView`.
    public ObservableList<LigneArchive> lignes() {
        return lignesNonModifiables;
    }

    /// Vide la table (nouvelle génération ou réinitialisation de la vue).
    public void reinitialiser() {
        lignes.clear();
    }

    /// Pré-remplit la table d'une ligne « en attente » par archive planifiée (dans l'ordre des numéros).
    public void planifier(List<ArchivePlanifiee> plan) {
        lignes.clear();
        for (ArchivePlanifiee a : plan) {
            lignes.add(new LigneArchive(a.numero(), a.nombreFichiers(), a.tailleEstimeeOctets()));
        }
    }

    /// Remplace les lignes par des archives **déjà écrites** (état « terminée », taille réelle) : sert à la
    /// réhydratation du disque à la réouverture d'un passage, et à la finalisation d'une génération
    /// synchrone (sans suivi événementiel).
    public void afficherTerminees(List<ArchiveDepot> archives) {
        lignes.clear();
        for (ArchiveDepot a : archives) {
            LigneArchive ligne = new LigneArchive(a.numero(), a.nombreFichiers(), a.tailleOctets());
            ligne.terminer(a.tailleOctets());
            lignes.add(ligne);
        }
    }

    /// L'archive `numero` commence : sa ligne passe « en cours ».
    public void demarrer(int numero) {
        ligne(numero).ifPresent(LigneArchive::demarrer);
    }

    /// Avancement intra-archive : `faits` fichiers sur `total` pour l'archive `numero`.
    public void progresser(int numero, int faits, int total) {
        if (total > 0) {
            ligne(numero).ifPresent(l -> l.progresser(faits / (double) total));
        }
    }

    /// L'archive est écrite : sa ligne passe « terminée » avec sa taille réelle.
    public void terminer(ArchiveDepot archive) {
        ligne(archive.numero()).ifPresent(l -> l.terminer(archive.tailleOctets()));
    }

    /// La compression de l'archive `numero` a échoué : sa ligne passe « échec ».
    public void echouer(int numero, String raison) {
        ligne(numero).ifPresent(l -> l.echouer(raison));
    }

    private Optional<LigneArchive> ligne(int numero) {
        return lignes.stream().filter(l -> l.numero() == numero).findFirst();
    }
}
