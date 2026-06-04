# 📦 [lot] 1/3 — Implémenter LotViewModel

Première sous-tâche de **M-Lot** : le **ViewModel** (statut du lot, récapitulatif, alertes, et les
deux actions du dépôt).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/lot/viewmodel/LotViewModel.java`

## Contexte

Les propriétés observables et leurs getters sont fournis. Vous écrivez le corps des méthodes publiques
`ouvrirSur`, `preparer` et `deposer` (chacune repérée par un `// TODO (M-Lot) …`). `preparer` et
`deposer` renvoient un booléen (succès) : une amorce de retour est déjà fournie pour vous, à compléter.

## Démarche (TDD)

1. **Activez** `LotViewModelTest` (retirez `@Disabled`), lancez-le, **constatez le rouge**. Les
   assertions décrivent l'état attendu après chaque action.
2. **`ouvrirSur`** : chargez l'état du lot auprès de `ServiceLot` et renseignez les propriétés (statut,
   récapitulatif, dossier, alertes, disponibilités des actions, message). En cas d'erreur, réinitialisez
   et publiez un message, sans laisser remonter d'exception.
3. **`preparer`** / **`deposer`** : déléguez l'action métier au service, rechargez l'état, renvoyez le
   succès ; sans passage ouvert, l'appel est ignoré.
4. Patron de référence : `SitesViewModel` (feature `sites`). Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `LotViewModelTest` est réactivé et **vert**.
- [ ] Les actions ne laissent remonter aucune exception ; les erreurs métier passent par la propriété
      `message`.

## Definition of Done

- [ ] **Un seul fichier modifié** : `LotViewModel.java` (+ retrait du `@Disabled` de son test).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert).
- [ ] `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` sans régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** référençant l'issue.
