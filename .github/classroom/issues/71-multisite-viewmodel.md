# 🗂 [multisite] 1/5 — Implémenter MultisiteViewModel

Première sous-tâche : le **ViewModel** (tableau agrégé, filtres, tri, export, vues sauvegardées).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/multisite/viewmodel/MultisiteViewModel.java`

## Contexte

Propriétés (filtres, tri, lignes, vues) et getters fournis ; le constructeur garde des écouteurs à
compléter (balisés). Vous écrivez le corps des méthodes publiques (repérées par `// TODO …`) :
`rafraichir`, `reinitialiserFiltres`, `exporter`, `chargerVues`, `enregistrerVue`, `appliquerVue`,
`mettreAJourVue`, `supprimerVue`. Plusieurs renvoient un booléen (amorce fournie).

## Démarche (TDD)

1. **Activez** `MultisiteViewModelTest`, **constatez le rouge** — il couvre filtres, tri, export et
   les opérations sur les vues sauvegardées.
2. Implémentez en **déléguant à `ServiceMultisite`** : `rafraichir` ré-interroge le service avec les
   filtres + le tri courants et met à jour le tableau + le résumé ; les opérations « vues » listent /
   enregistrent / appliquent / mettent à jour / suppriment, puis rechargent. Erreurs via le message.
3. Patron : `SitesViewModel`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `MultisiteViewModelTest` réactivé et **vert**.

## Definition of Done

- [ ] **Un seul fichier modifié** : `MultisiteViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
