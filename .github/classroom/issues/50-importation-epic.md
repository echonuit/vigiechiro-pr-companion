# 📥 [importation] Construire l'écran M-Import (assistant « Importer une nuit »)

**Issue chapeau**. Détail dans les 3 issues « fichier » qui suivent. C'est l'écran le plus riche : la
logique d'import tourne **en arrière-plan** pour ne pas figer l'IHM.

## L'écran à construire

**M-Import** (parcours P2) est un assistant en 4 sections : (1) choisir un dossier source ; (2)
l'**inspecter** en lecture seule ; (3) **rattacher** la nuit (site / point / année / n° de passage) et
prévisualiser le préfixe ; (4) **lancer l'import** avec une barre de progression. On l'atteint depuis
la carte « Importer une nuit » de l'accueil.

## Architecture

```
ImportationController  ──lie──>  ImportationViewModel  ──délègue──>  ServiceImport (fourni)
  (Importation.fxml)            (propriétés + inspection + import)
```

## Sous-tâches (dans l'ordre)

- [ ] **1/3** — Implémenter `ImportationViewModel`.
- [ ] **2/3** — Construire `Importation.fxml`.
- [ ] **3/3** — Câbler `ImportationController` (dont l'import en arrière-plan).

## Test d'acceptation de la feature

`ImportationViewTest`, livré `@Disabled`. Feature **terminée quand il est vert**.

## Critères d'acceptation (feature)

- [ ] Sous-tâches mergées ; `ImportationViewModelTest` et `ImportationViewTest` verts.
- [ ] Dans l'appli, la carte « Importer une nuit » ouvre l'assistant ; choisir un dossier l'inspecte ;
      le rattachement complété active l'import ; l'import affiche une progression puis un résumé.

## Definition of Done (feature)

- [ ] Suite verte sans régression ; ArchUnit, `spotless:check`, `-Pquality-gate verify` verts.
- [ ] Chaque sous-tâche passée par une **PR relue**.
