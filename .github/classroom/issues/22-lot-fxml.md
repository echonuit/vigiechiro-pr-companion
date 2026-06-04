# 📦 [lot] 2/3 — Construire la vue Lot.fxml

Deuxième sous-tâche de **M-Lot** : la mise en page (placeholder → vraie vue).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/lot/view/Lot.fxml`

## Ce qu'il faut faire

1. Étudiez la vue de référence `sites/view/SiteDetail.fxml`.
2. Construisez la vraie mise en page de M-Lot, en déclarant les `fx:id` attendus par le controleur :
   `lblStatut`, `lblRecap`, `lblCheminDossier`, `zoneAlertes` (le conteneur des alertes),
   `listeAlertes` (ListView), `btnPreparer`, `btnDeposer`, `lblMessage`.
3. Reliez les deux boutons à leurs actions via `onAction` (les noms d'actions sont définis dans le
   controleur : `preparer`, `deposer`).
4. Gardez `fx:controller` vers `LotController` sur l'élément racine.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` ci-dessus sont présents et du bon type.
- [ ] Les boutons « Préparer » / « Marquer déposé » déclarent bien leur `onAction`.

## Definition of Done

- [ ] **Un seul fichier modifié** : `Lot.fxml`.
- [ ] Le câblage controleur arrive en 3/3 : un test encore rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue** référençant l'issue.
