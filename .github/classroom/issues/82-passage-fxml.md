# 🗂️ [passage] 2/6 — Construire la vue Passage.fxml

Deuxième sous-tâche : la mise en page de l'écran pivot (placeholder → vraie vue).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/passage/view/Passage.fxml`

## Ce qu'il faut faire

1. Étudiez `sites/view/SiteDetail.fxml` (bandeau + sections + boutons).
2. Construisez la fiche avec les `fx:id` attendus par `PassageController` : `racine`, `lblFilAriane`,
   `lblTitre`, `boutonRattachement`, `boutonSupprimer`, `lblPlageHoraire`, `lblEnregistreur`,
   `lblStatut`, `lblVerdict`, `stepper` (le conteneur du stepper de statut), `lblMessage` ; statistiques
   `lblVolBruts`, `lblVolTransformes`, `lblDureeAudible`, `lblNbSequences` ; actions `boutonVerifier`,
   `boutonDiagnostic`, `boutonDepot`, `boutonValidation` (+ variantes `boutonOuvrirDiagnostic`,
   `boutonOuvrirValidation`), `lblIndiceAction`, `lblValidation`.
3. Reliez chaque bouton à son `onAction` (noms dans le controleur). Gardez `fx:controller` vers
   `PassageController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type.

## Definition of Done

- [ ] **Un seul fichier modifié** : `Passage.fxml`.
- [ ] Câblage controleur en 3/6 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
