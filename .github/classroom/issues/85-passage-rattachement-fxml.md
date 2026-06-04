# 🗂️ [passage] 5/6 — Construire la modale RattachementModale.fxml

Cinquième sous-tâche : la **modale de rattachement** (placeholder → vraie vue).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/passage/view/RattachementModale.fxml`

## Ce qu'il faut faire

1. Construisez la modale avec les `fx:id` attendus par `RattachementModaleController` : `racine`
   (l'élément racine, pour fermer la fenêtre), `spinnerAnnee` (Spinner), `spinnerNumero` (Spinner),
   `labelRecap`, `messageErreur`.
2. Ajoutez deux boutons reliés à leurs `onAction` : valider (`valider`) et annuler (`annuler`). Gardez
   `fx:controller` vers `RattachementModaleController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type (Spinner pour les deux
      champs numériques).

## Definition of Done

- [ ] **Un seul fichier modifié** : `RattachementModale.fxml`.
- [ ] Câblage controleur en 6/6 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
