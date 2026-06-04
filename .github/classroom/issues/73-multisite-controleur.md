# 🗂 [multisite] 3/5 — Câbler MultisiteController

Troisième sous-tâche : relier la vue au ViewModel, brancher filtres/tri/export et le **double-clic**
d'ouverture d'un passage.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/multisite/view/MultisiteController.java`

## Contexte

Le controleur reçoit le ViewModel, le contrat d'ouverture de passage, et la navigation (pour ouvrir la
modale). Pas de `ouvrirSur` : il **charge le tableau tout seul** en fin d'initialisation FXML.

## Démarche (TDD)

1. **Activez** `MultisiteViewTest`, **constatez le rouge**.
2. Patron : `SiteDetailController` (en particulier le **double-clic** sur une ligne de table qui ouvre
   un passage via le contrat socle `OuvrirPassage` — sans dépendre de la feature `passage`).
3. Câblez le tableau et ses colonnes, les combos/champs de filtres (en liaison bidirectionnelle avec
   le ViewModel), le tri, le résumé, le message. Ajoutez les **handlers** `@FXML` : réinitialiser,
   exporter (sélecteur de fichier natif), et « Vues… » (qui demande à la navigation d'ouvrir la modale
   branchée sur **ce même** ViewModel). Le double-clic sur une ligne ouvre le passage correspondant.
4. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`MultisiteViewTest` réactivé et vert**.
- [ ] Le double-clic sur une ligne ouvre l'écran du passage.

## Definition of Done

- [ ] **Un seul fichier modifié** : `MultisiteController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit `view_sans_jdbc` vert) ; pas de régression ;
      `spotless:check` OK.
- [ ] Livré via **branche + PR relue**.
