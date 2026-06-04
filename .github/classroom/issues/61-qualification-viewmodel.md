# 🎧 [qualification] 1/4 — Implémenter QualificationViewModel (verdict)

Première sous-tâche : le **ViewModel du verdict** (pré-check + choix et enregistrement du verdict).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/qualification/viewmodel/QualificationViewModel.java`

## Contexte

Propriétés, binding « peut enregistrer » et getters fournis (le constructeur est complet : n'y touchez
pas). Vous écrivez le corps de `ouvrirSur`, `choisirVerdict` et `enregistrer` (repérés par `// TODO …`).

## Démarche (TDD)

1. **Activez** `QualificationViewModelTest`, **constatez le rouge**.
2. Implémentez en **déléguant à `ServiceQualification`** : `ouvrirSur` exécute le pré-check (3 feux) et
   amorce le bandeau (statut + verdict actuels) ; `choisirVerdict` mémorise le choix ; `enregistrer`
   refuse sans verdict décisif, sinon persiste le verdict (statut → Vérifié), gère l'avertissement
   « à jeter » (R14) et l'état « enregistré ». Toute erreur passe par le message.
3. Patron : `SiteDetailViewModel`. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] `QualificationViewModelTest` réactivé et **vert**.

## Definition of Done

- [ ] **Un seul fichier modifié** : `QualificationViewModel.java` (+ retrait du `@Disabled`).
- [ ] ViewModel sans `javafx.scene` (ArchUnit vert) ; `spotless:check` OK ; pas de régression.
- [ ] Livré via **branche + PR relue**.
