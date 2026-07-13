package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.SuiviLignes;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.ArchivePlanifiee;
import java.util.List;

/// Table de suivi des archives de dépôt (#820) côté ViewModel : spécialise le socle [SuiviLignes] pour
/// traduire le cycle de vie reçu du [fr.univ_amu.iut.lot.model.SuiviArchives] (plan établi → démarrée →
/// progresse → terminée / échec) en [LigneArchive] observables. Chaque événement cible sa ligne par
/// **numéro** (la compression étant parallèle #814, ils arrivent dans le désordre) ; un numéro inconnu
/// est ignoré sans erreur.
///
/// **Fil JavaFX** : ces méthodes mutent des collections/propriétés observables ; l'appelant (le
/// controller) les invoque sur le fil JavaFX fourni par le socle (`ExecuteurTache#surFilJavaFx()`), comme le
/// callback de progression global.
public final class SuiviLignesArchives extends SuiviLignes<LigneArchive> {

    /// Pré-remplit la table d'une ligne « en attente » par archive planifiée (dans l'ordre des numéros).
    public void planifier(List<ArchivePlanifiee> plan) {
        remplacerLignes(plan.stream()
                .map(a -> new LigneArchive(a.numero(), a.nombreFichiers(), a.tailleEstimeeOctets()))
                .toList());
    }

    /// Remplace les lignes par des archives **déjà écrites** (état « terminée », taille réelle) : sert à la
    /// réhydratation du disque à la réouverture d'un passage, et à la finalisation d'une génération
    /// synchrone (sans suivi événementiel).
    public void afficherTerminees(List<ArchiveDepot> archives) {
        remplacerLignes(archives.stream()
                .map(a -> {
                    LigneArchive ligne = new LigneArchive(a.numero(), a.nombreFichiers(), a.tailleOctets());
                    ligne.terminer(a.tailleOctets());
                    return ligne;
                })
                .toList());
    }

    /// L'archive est écrite : sa ligne passe « terminée » avec sa taille réelle.
    public void terminer(ArchiveDepot archive) {
        ligne(archive.numero()).ifPresent(l -> l.terminer(archive.tailleOctets()));
    }
}
