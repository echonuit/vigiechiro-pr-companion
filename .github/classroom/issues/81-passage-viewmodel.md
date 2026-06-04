# 🗂️ [passage] 1/6 — Implémenter PassageViewModel

Première sous-tâche : le **ViewModel** de la fiche pivot (identité, stepper, statistiques,
disponibilités des actions).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/passage/viewmodel/PassageViewModel.java`

## Contexte

Propriétés et getters fournis (constructeur complet). Vous écrivez le corps de `ouvrirSur` et
`supprimer` (repérés par `// TODO …`). `ouvrirSur` reçoit un identifiant de passage **et** un contexte
de site (carré/point/nom) fourni par la navigation.

## Démarche (TDD)

1. **Activez** `PassageViewModelTest`, **constatez le rouge**.
2. `ouvrirSur` : chargez le détail du passage auprès de `ServicePassage` et alimentez identité,
   statut/verdict, volumes, **étapes du stepper**, et les disponibilités (vérification possible, dépôt
   possible, validation verrouillée tant que non déposé). En cas d'erreur, réinitialisez + message.
   `supprimer` : délègue au service (la règle « passage déposé non supprimable » remonte à la vue).
3. Patron : `SiteDetailViewModel`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `PassageViewModelTest` réactivé et **vert**.

## Definition of Done

- [ ] **Un seul fichier modifié** : `PassageViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
