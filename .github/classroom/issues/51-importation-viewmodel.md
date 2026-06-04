# 📥 [importation] 1/3 — Implémenter ImportationViewModel

Première sous-tâche : le **ViewModel** de l'assistant (inspection, rattachement, exécution de l'import).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/importation/viewmodel/ImportationViewModel.java`

## Contexte

Toutes les propriétés observables, le binding « peut importer » et les getters sont fournis (le
constructeur garde aussi des écouteurs à compléter, balisés). Vous écrivez le corps des méthodes
publiques (repérées par des `// TODO …`) : `chargerSites`, `inspecter`, `importer`, `preparerImport`,
`executerImport`, et les `marquer…` (état EN_COURS / TERMINE / ECHEC + progression). `preparerImport`
et `executerImport` renvoient une valeur (amorce fournie).

## Démarche (TDD)

1. **Activez** `ImportationViewModelTest`, **constatez le rouge**.
2. Implémentez en **déléguant à `ServiceImport`** : inspection en lecture seule (alimente les
   propriétés d'inspection + les avertissements via les utilitaires fournis), capture du rattachement
   courant (`preparerImport`), exécution de l'import (`executerImport`, qui relaie la progression), et
   les transitions d'état (`marquer…`). Comprenez bien la séparation : `preparerImport` capture l'état
   sur le fil JavaFX, `executerImport` fait le travail lourd **sans** toucher aux propriétés.
3. Patron : `SitesViewModel`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `ImportationViewModelTest` réactivé et **vert**.

## Definition of Done

- [ ] **Un seul fichier modifié** : `ImportationViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
