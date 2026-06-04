# ✅ [validation] 1/3 — Implémenter ValidationViewModel

Première sous-tâche : le **ViewModel** (liste filtrée des observations + quatre actions).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/validation/viewmodel/ValidationViewModel.java`

## Contexte

Les propriétés observables, la **liste filtrée** et les getters sont fournis. Vous écrivez le corps de
`ouvrirSur`, `valider`, `corriger`, `importer`, `exporter` (repérés par des `// TODO …`). Les quatre
dernières renvoient un booléen (succès), avec une amorce de retour fournie.

## Démarche (TDD)

1. **Activez** `ValidationViewModelTest`, **constatez le rouge** — lisez les assertions, elles
   couvrent chaque action et les cas limites (sans sélection, second import refusé, etc.).
2. Implémentez chaque méthode en **déléguant à `ServiceValidation`** puis en rechargeant la vue :
   - `ouvrirSur` : charger les taxons + la vue de validation du passage ;
   - `valider` : valider l'observation sélectionnée selon le mode de revue ;
   - `corriger` : retenir le taxon de l'observateur (en refusant la proposition Tadarida elle-même) ;
   - `importer` : importer le CSV (un seul jeu par passage) ;
   - `exporter` : écrire le CSV `_Vu` selon l'option d'inclusion du mode.
   Toute erreur passe par le message, sans exception qui remonte.
3. Patron : `SiteDetailViewModel`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `ValidationViewModelTest` réactivé et **vert** (toutes les actions + cas limites).

## Definition of Done

- [ ] **Un seul fichier modifié** : `ValidationViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
