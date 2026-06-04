# 📦 [lot] Construire l'écran M-Lot (préparer et déposer)

**Issue chapeau** : vue d'ensemble. Le détail est dans les 3 issues « fichier » qui suivent (à faire
**dans l'ordre**).

## L'écran à construire

**M-Lot** prépare et marque le **dépôt** d'un passage sur Vigie-Chiro (parcours P4). On l'atteint
depuis M-Passage (« Préparer le dépôt »). Il affiche le statut, un récapitulatif du lot, le dossier à
téléverser manuellement, d'éventuelles **alertes de cohérence (R14)**, et deux actions en deux temps :
**Préparer le lot** (Vérifié → Prêt à déposer) puis **Marquer déposé**.

## Architecture

```
LotController  ──lie──>  LotViewModel  ──délègue──>  ServiceLot (fourni)
  (Lot.fxml)              (propriétés + 2 actions)
```

## Sous-tâches (dans l'ordre)

- [ ] **1/3** — Implémenter `LotViewModel` (corps de `ouvrirSur`, `preparer`, `deposer`).
- [ ] **2/3** — Construire `Lot.fxml`.
- [ ] **3/3** — Câbler `LotController`.

## Test d'acceptation de la feature

Le test d'intégration **`LotViewTest`** est livré `@Disabled`. La feature est **terminée quand il est
vert**.

## Critères d'acceptation (feature)

- [ ] Les 3 sous-tâches sont mergées.
- [ ] `LotViewModelTest` **et** `LotViewTest` sont réactivés et **verts**.
- [ ] Dans l'appli, « Préparer le dépôt » d'un passage Vérifié affiche le vrai écran ; le bouton
      « Préparer » puis « Marquer déposé » font progresser le statut.

## Definition of Done (feature)

- [ ] Toute la suite passe sans régression ; ArchUnit vert (MVVM respecté).
- [ ] `./mvnw spotless:check` et `./mvnw -Pquality-gate verify` (PMD) verts.
- [ ] Chaque sous-tâche est passée par une **PR relue**.
