# 🗂️ [passage] 3/6 — Câbler PassageController

Troisième sous-tâche : relier la vue au ViewModel et brancher les boutons qui **ouvrent les autres
écrans** via les contrats socle (déjà fournis).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/passage/view/PassageController.java`

## Contexte

Le controleur reçoit le ViewModel, les **contrats d'ouverture** (vérification, diagnostic, validation,
lot) et la navigation. Sa méthode `ouvrirSur` (fournie) mémorise le passage + le contexte et délègue au
ViewModel : ne la touchez pas.

## Démarche (TDD)

1. **Activez** `PassageViewTest`, **constatez le rouge**.
2. Patron : `SiteDetailController`. Câblez l'identité, le bandeau, le **stepper** (reconstruit depuis
   les étapes du ViewModel), les statistiques, les libellés d'indice, et l'activation des boutons selon
   les disponibilités (vérification, dépôt, validation verrouillée). Ajoutez les **handlers** `@FXML`
   qui appellent les contrats : Vérifier, Diagnostic, Préparer le dépôt, Validation Tadarida, Supprimer
   (avec confirmation), Modifier le rattachement (ouvre la modale via la navigation).
3. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`PassageViewTest` réactivé et vert**.
- [ ] Dans l'appli, les boutons ouvrent les bons écrans selon le statut du passage.

## Definition of Done

- [ ] **Un seul fichier modifié** : `PassageController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit `view_sans_jdbc` vert) ; pas de régression ;
      `spotless:check` OK.
- [ ] Livré via **branche + PR relue**.
