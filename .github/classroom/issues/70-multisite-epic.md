# 🗂 [multisite] Construire l'écran M-Multisite (vue agrégée + vues sauvegardées)

**Issue chapeau**. Détail dans les 5 issues « fichier » qui suivent. Particularité : un **écran
principal** + une **modale** (les vues de filtres sauvegardées).

## L'écran à construire

**M-Multisite** (parcours P5, SHOULD) agrège **tous les passages** de l'utilisateur dans un tableau
filtrable (carré, statut, verdict, année) et triable, avec **export CSV** et **double-clic** pour
ouvrir un passage. Une **modale « Vues sauvegardées »** permet d'enregistrer/rejouer une combinaison de
filtres. On l'atteint depuis la carte « Vue multi-sites » de l'accueil.

## Architecture

```
MultisiteController ─┬─lie──> MultisiteViewModel ──> ServiceMultisite (fourni)
  (Multisite.fxml)   └─ouvre─> ModaleVuesController (ModaleVues.fxml), branché sur le MÊME ViewModel
```

## Sous-tâches (dans l'ordre)

- [ ] **1/5** — Implémenter `MultisiteViewModel`.
- [ ] **2/5** — Construire `Multisite.fxml`.
- [ ] **3/5** — Câbler `MultisiteController` (table + filtres + double-clic + export).
- [ ] **4/5** — Construire `ModaleVues.fxml` (la modale).
- [ ] **5/5** — Câbler `ModaleVuesController`.

## Tests d'acceptation de la feature

`MultisiteViewTest` (écran) **et** `ModaleVuesViewTest` (modale), livrés `@Disabled`. Feature
**terminée quand les deux sont verts**.

## Critères d'acceptation (feature)

- [ ] Sous-tâches mergées ; `MultisiteViewModelTest`, `MultisiteViewTest`, `ModaleVuesViewTest` verts.
- [ ] Dans l'appli, la carte « Vue multi-sites » ouvre le tableau ; filtres/tri/export marchent ; le
      double-clic ouvre un passage ; la modale enregistre/rejoue une vue.

## Definition of Done (feature)

- [ ] Suite verte sans régression ; ArchUnit, `spotless:check`, `-Pquality-gate verify` verts.
- [ ] Chaque sous-tâche passée par une **PR relue**.
