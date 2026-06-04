# 🎧 [qualification] Construire l'écran M-Qualification (vérifier par échantillonnage)

**Issue chapeau**. Détail dans les 4 issues « fichier » qui suivent. Particularité : cet écran a
**deux ViewModels** (le verdict d'un côté, la sélection d'écoute de l'autre), reliés par le controleur.

## L'écran à construire

**M-Qualification** (parcours P3) sert à **vérifier une nuit par échantillonnage** : un **pré-check**
3 feux (R13), une **sélection d'écoute** échantillonnée qu'on parcourt et écoute (R10/R12), et un
**verdict différé** (OK / douteux / à jeter, R15) qu'on enregistre. On l'atteint depuis M-Passage
(« Vérifier »).

## Architecture

```
QualificationController ──lie──> QualificationViewModel (verdict)      ──> ServiceQualification
   (Qualification.fxml)    └────> SelectionEcouteViewModel (liste/écoute) ──>   (fourni)
```

## Sous-tâches (dans l'ordre)

- [ ] **1/4** — Implémenter `QualificationViewModel` (verdict).
- [ ] **2/4** — Implémenter `SelectionEcouteViewModel` (liste + écoute).
- [ ] **3/4** — Construire `Qualification.fxml`.
- [ ] **4/4** — Câbler `QualificationController` (relie les deux ViewModels).

## Test d'acceptation de la feature

`QualificationViewTest`, livré `@Disabled`. Feature **terminée quand il est vert**.

## Critères d'acceptation (feature)

- [ ] Sous-tâches mergées ; `QualificationViewModelTest`, `SelectionEcouteViewModelTest` et
      `QualificationViewTest` verts.
- [ ] Dans l'appli, « Vérifier » d'un passage transformé ouvre l'écran ; on parcourt la sélection, on
      pose un verdict et on l'enregistre (statut → Vérifié).

## Definition of Done (feature)

- [ ] Suite verte sans régression ; ArchUnit, `spotless:check`, `-Pquality-gate verify` verts.
- [ ] Chaque sous-tâche passée par une **PR relue**.
