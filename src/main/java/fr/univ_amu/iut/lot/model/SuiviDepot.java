package fr.univ_amu.iut.lot.model;

import java.util.List;

/// Suivi **par unité** du dépôt VigieChiro (#982) : permet à l'IHM d'afficher une ligne par fichier
/// téléversé (à déposer → en cours → déposé / échec), en complément du bilan final. Patron des suivis
/// de la génération d'archives ([SuiviArchives], #820) et de l'import (#947).
///
/// Le dépôt est **séquentiel** (une unité à la fois, réseau) mais émis **hors du fil JavaFX** :
/// l'implémentation IHM doit relayer ses mutations sur le fil JavaFX (fourni par le socle,
/// `ExecuteurTache#surFilJavaFx()`). Chaque événement cible son
/// unité par son **identifiant** (nom du fichier, unique par passage : la clé de `depot_unite`, #981).
public interface SuiviDepot {

    /// Plan (ré)établi **avant tout téléversement** : l'état persisté de toutes les unités du passage,
    /// statuts de reprise compris (une unité déjà `depose` d'un dépôt précédent arrive telle quelle).
    void planEtabli(List<DepotUnite> unites);

    /// Le téléversement de l'unité `identifiant` commence.
    void uniteDemarree(String identifiant);

    /// L'unité est téléversée et finalisée : `unite` porte son statut `depose` et l'id distant.
    void uniteDeposee(DepotUnite unite);

    /// Le téléversement de l'unité `identifiant` a échoué (raison persistée et affichable).
    ///
    /// `definitif` dit qu'un nouvel essai est **inutile** (#3687) : URL signée expirée, jeton mort,
    /// corps refusé. Il vient de `ReponseApi.estReessayable()`, décidé à l'émission - jamais d'une
    /// lecture du texte de `raison`, où la même panne s'écrit de trop de façons.
    void uniteEchouee(String identifiant, String raison, boolean definitif);

    /// La réconciliation n'a **pas pu lire** ce que la plateforme porte (#4631).
    ///
    /// Ce n'est pas « la plateforme ne porte rien ». La lecture rendait un vide sur quatre issues
    /// distinctes, et le vide se lisait comme une absence de données : une réconciliation menée sur
    /// cette prémisse conclut que rien n'a été déposé là-bas, alors qu'elle n'a rien pu demander.
    ///
    /// Distincte d'[#uniteEchouee] à dessein : aucune unité n'a échoué, c'est l'étape qui n'a pas
    /// tourné. Les confondre afficherait un échec de dépôt là où il n'y en a pas.
    ///
    /// @param raison ce que la réponse a dit d'elle-même
    /// @param definitif `true` quand un nouvel essai est inutile, depuis `ReponseApi.estReessayable()`
    void reconciliationImpossible(String raison, boolean definitif);

    /// Avancement (fraction 0 à 1, #984) du téléversement de l'unité `identifiant`, remontée octet par
    /// octet pour une barre de progression par archive. No-op par défaut : seuls les suivis IHM
    /// l'exploitent (les suivis inerte / console l'ignorent).
    default void uniteProgresse(String identifiant, double fraction) {}

    /// Le téléversement de l'unité `identifiant` a rencontré une coupure momentanée : une **nouvelle
    /// tentative** est prévue dans `delai` (#2354). No-op par défaut : seuls les suivis IHM en font une
    /// mention discrète (« nouvelle tentative dans N s »), ni silence trompeur ni alarme (#2350).
    default void uniteReprise(String identifiant, java.time.Duration delai) {}

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
            public void uniteEchouee(String identifiant, String raison, boolean definitif) {}

            @Override
            public void reconciliationImpossible(String raison, boolean definitif) {}
        };
    }
}
