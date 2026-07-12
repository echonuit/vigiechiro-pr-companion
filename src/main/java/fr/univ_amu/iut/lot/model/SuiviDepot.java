package fr.univ_amu.iut.lot.model;

import java.util.List;

/// Suivi **par unité** du dépôt VigieChiro (#982) : permet à l'IHM d'afficher une ligne par fichier
/// téléversé (à déposer → en cours → déposé / échec), en complément du bilan final. Patron des suivis
/// de la génération d'archives ([SuiviArchives], #820) et de l'import (#947).
///
/// Le dépôt est **séquentiel** (une unité à la fois, réseau) mais émis **hors du fil JavaFX** :
/// l'implémentation IHM doit relayer ses mutations via `Platform.runLater`. Chaque événement cible son
/// unité par son **identifiant** (nom du fichier, unique par passage — la clé de `depot_unite`, #981).
public interface SuiviDepot {

    /// Plan (ré)établi **avant tout téléversement** : l'état persisté de toutes les unités du passage,
    /// statuts de reprise compris (une unité déjà `depose` d'un dépôt précédent arrive telle quelle).
    void planEtabli(List<DepotUnite> unites);

    /// Le téléversement de l'unité `identifiant` commence.
    void uniteDemarree(String identifiant);

    /// L'unité est téléversée et finalisée : `unite` porte son statut `depose` et l'id distant.
    void uniteDeposee(DepotUnite unite);

    /// Le téléversement de l'unité `identifiant` a échoué (raison persistée et affichable).
    void uniteEchouee(String identifiant, String raison);

    /// Avancement (fraction 0 à 1, #984) du téléversement de l'unité `identifiant`, remontée octet par
    /// octet pour une barre de progression par archive. No-op par défaut : seuls les suivis IHM
    /// l'exploitent (les suivis inerte / console l'ignorent).
    default void uniteProgresse(String identifiant, double fraction) {}

    /// Suivi **inerte** (aucun affichage) : valeur par défaut des appels sans IHM et des tests qui
    /// n'observent pas le détail par unité.
    static SuiviDepot inerte() {
        return new SuiviDepot() {
            @Override
            public void planEtabli(List<DepotUnite> unites) {}

            @Override
            public void uniteDemarree(String identifiant) {}

            @Override
            public void uniteDeposee(DepotUnite unite) {}

            @Override
            public void uniteEchouee(String identifiant, String raison) {}
        };
    }
}
