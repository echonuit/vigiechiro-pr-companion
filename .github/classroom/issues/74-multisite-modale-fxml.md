# 🗂 [multisite] 4/5 — Construire la modale ModaleVues.fxml

Quatrième sous-tâche : la **modale des vues sauvegardées** (placeholder → vraie vue).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/multisite/view/ModaleVues.fxml`

## Ce qu'il faut faire

1. Construisez la modale avec les `fx:id` attendus par `ModaleVuesController` : `racine` (l'élément
   racine, utilisé pour fermer la fenêtre), `listeVues` (ListView), `boutonAppliquer`,
   `boutonMettreAJour`, `boutonSupprimer`, `champNom`, `boutonEnregistrer`, `lblMessage`.
2. Reliez les boutons à leurs `onAction` (noms dans le controleur : `appliquer`, `mettreAJour`,
   `supprimer`, `enregistrer`, et un bouton « Fermer » → `fermer`). Gardez `fx:controller` vers
   `ModaleVuesController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type.

## Definition of Done

- [ ] **Un seul fichier modifié** : `ModaleVues.fxml`.
- [ ] Câblage controleur en 5/5 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
