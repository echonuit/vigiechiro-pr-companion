# 🔊 [bibliotheque] Construire l'écran M-Bibliotheque (sons de référence)

**Issue chapeau**. Détail dans les 3 issues « fichier » qui suivent.

## L'écran à construire

**M-Bibliotheque** (statut COULD, parcours P10) liste les **sons de référence** marqués pendant la
validation, permet de les **écouter** (composant audio fourni) et d'**exporter** la bibliothèque (CSV +
copie des fichiers son). On l'atteint depuis la carte « Bibliothèque de sons » de l'accueil.

## Architecture

```
BibliothequeController  ──lie──>  BibliothequeViewModel  ──lit──>  ServiceBibliotheque (fourni)
   (Bibliotheque.fxml)
```

## Sous-tâches (dans l'ordre)

- [ ] **1/3** — Implémenter `BibliothequeViewModel` (`charger`, `exporter`).
- [ ] **2/3** — Construire `Bibliotheque.fxml`.
- [ ] **3/3** — Câbler `BibliothequeController`.

## Test d'acceptation de la feature

`BibliothequeViewTest`, livré `@Disabled`. Feature **terminée quand il est vert**.

## Critères d'acceptation (feature)

- [ ] Les 3 sous-tâches sont mergées ; `BibliothequeViewModelTest` et `BibliothequeViewTest` verts.
- [ ] Dans l'appli, la carte « Bibliothèque de sons » ouvre le vrai écran (table + détail + écoute).

## Definition of Done (feature)

- [ ] Suite verte sans régression ; ArchUnit vert ; `spotless:check` + `-Pquality-gate verify` verts.
- [ ] Chaque sous-tâche passée par une **PR relue**.
