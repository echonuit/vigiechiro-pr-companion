# 🗂️ [passage] 6/6 — Câbler RattachementModaleController

Dernière sous-tâche : relier la modale au ViewModel et fermer la fenêtre après succès.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/passage/view/RattachementModaleController.java`

## Contexte

La méthode publique `demarrer` (fournie, appelée par la navigation) reçoit l'identifiant du passage, le
carré/point et une action de succès : son corps est à compléter (balisé).

## Démarche (TDD)

1. **Activez** `RattachementModaleViewTest`, **constatez le rouge**.
2. Déclarez les champs `@FXML`, câblez les deux spinners (liaison bidirectionnelle avec le ViewModel),
   le récapitulatif et le message ; dans `demarrer`, initialisez le ViewModel sur le passage. Ajoutez
   les **handlers** `@FXML` : valider (qui, en cas de succès, exécute l'action de succès et ferme la
   fenêtre) et annuler (ferme la fenêtre).
3. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`RattachementModaleViewTest` réactivé et vert** — avec `PassageViewTest`, c'est l'acceptation
      de la feature.
- [ ] Dans l'appli, « Modifier le rattachement » ouvre la modale ; valider corrige l'année / le n° et
      rafraîchit la fiche.

## Definition of Done

- [ ] **Un seul fichier modifié** : `RattachementModaleController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit vert) ; **toute la feature passage verte** sans
      régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico.
