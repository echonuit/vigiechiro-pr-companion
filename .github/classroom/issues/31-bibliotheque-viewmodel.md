# 🔊 [bibliotheque] 1/3 — Implémenter BibliothequeViewModel

Première sous-tâche : le **ViewModel** (liste des sons, sélection courante, export).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/bibliotheque/viewmodel/BibliothequeViewModel.java`

## Contexte

Propriétés et getters fournis. Vous écrivez le corps de `charger` (chargement initial) et `exporter`
(matérialisation disque), repérés par des `// TODO (M-Bibliotheque) …`. Le constructeur installe un
écouteur de sélection : sa partie à compléter est déjà balisée.

## Démarche (TDD)

1. **Activez** `BibliothequeViewModelTest`, **constatez le rouge**.
2. **`charger`** : interrogez `ServiceBibliotheque`, peuplez la liste des entrées, mettez à jour
   l'indicateur « non vide » et le résumé, réinitialisez la sélection.
3. **`exporter`** : matérialisez la bibliothèque vers le dossier choisi, publiez le bilan ou l'erreur
   dans le message, renvoyez le succès.
4. Patron : `SitesViewModel`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `BibliothequeViewModelTest` réactivé et **vert**.
- [ ] Une bibliothèque vide produit un résumé adapté (aucun crash, export inactif côté vue ensuite).

## Definition of Done

- [ ] **Un seul fichier modifié** : `BibliothequeViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
