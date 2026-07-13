package fr.univ_amu.iut.lot.model;

import java.util.List;

/// Suivi **par archive** de la génération des archives de dépôt (#820) : permet à l'IHM d'afficher une
/// ligne par ZIP avec son état (en attente → en cours → terminée / échec) et sa propre barre de
/// progression, en complément de la barre globale.
///
/// La compression étant **parallèle** (#814), ces méthodes sont appelées depuis plusieurs fils et **dans
/// le désordre** ; chaque événement cible son archive par son **numéro** (1..N), donc l'ordre d'arrivée
/// n'importe pas. L'implémentation IHM **doit relayer** ses mutations au fil JavaFX
/// (fil JavaFX fourni par le socle, `ExecuteurTache#surFilJavaFx()`), comme le callback de
/// progression global.
public interface SuiviArchives {

    /// Plan établi **avant toute écriture** : une entrée par archive à produire, pour pré-remplir la table
    /// de lignes « en attente ».
    void planEtabli(List<ArchivePlanifiee> plan);

    /// L'archive `numero` commence à être compressée (la ligne passe « en cours »).
    void archiveDemarree(int numero);

    /// Progression **intra-archive** : `faits` fichiers compressés sur `total` pour l'archive `numero`.
    void archiveProgresse(int numero, int faits, int total);

    /// L'archive `numero` est écrite : `archive` porte sa taille réelle et son nombre de fichiers.
    void archiveTerminee(ArchiveDepot archive);

    /// La compression de l'archive `numero` a échoué (la ligne passe « échec »), `raison` décrit la cause.
    void archiveEchouee(int numero, String raison);

    /// Suivi **inerte** (aucun affichage) : valeur par défaut pour la variante sans IHM et les tests qui
    /// n'observent pas le détail par archive.
    static SuiviArchives inerte() {
        return new SuiviArchives() {
            @Override
            public void planEtabli(List<ArchivePlanifiee> plan) {}

            @Override
            public void archiveDemarree(int numero) {}

            @Override
            public void archiveProgresse(int numero, int faits, int total) {}

            @Override
            public void archiveTerminee(ArchiveDepot archive) {}

            @Override
            public void archiveEchouee(int numero, String raison) {}
        };
    }
}
