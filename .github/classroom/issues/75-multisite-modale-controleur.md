# 🗂 [multisite] 5/5 — Câbler ModaleVuesController

Dernière sous-tâche : relier la modale au ViewModel **partagé** avec l'écran principal (de sorte
qu'appliquer une vue met à jour les filtres et le tableau dessous).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/multisite/view/ModaleVuesController.java`

## Contexte

La méthode publique `demarrer` (fournie, appelée par la navigation) reçoit le `MultisiteViewModel` de
l'écran : son corps est à compléter (balisé) pour brancher la liste + charger les vues.

## Démarche (TDD)

1. **Activez** `ModaleVuesViewTest`, **constatez le rouge**.
2. Déclarez les champs `@FXML`, câblez la liste des vues (cellules par nom), l'activation des boutons
   selon la sélection, la pré-saisie du nom, et le message ; dans `demarrer`, branchez la liste sur le
   ViewModel partagé et chargez les vues. Ajoutez les **handlers** `@FXML` (enregistrer, appliquer,
   mettre à jour, supprimer, fermer).
3. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`ModaleVuesViewTest` réactivé et vert** — avec `MultisiteViewTest`, c'est l'acceptation de la
      feature.
- [ ] Dans l'appli, « Vues… » ouvre la modale ; enregistrer puis appliquer une vue met à jour le
      tableau de l'écran principal.

## Definition of Done

- [ ] **Un seul fichier modifié** : `ModaleVuesController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit vert) ; **toute la feature multisite verte** sans
      régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico.
