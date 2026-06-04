# 📥 [importation] 2/3 — Construire la vue Importation.fxml

Deuxième sous-tâche : la mise en page de l'assistant (4 sections), placeholder → vraie vue.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/importation/view/Importation.fxml`

## Ce qu'il faut faire

1. Étudiez `sites/view/SiteDetail.fxml` pour les conteneurs et les sections.
2. Construisez les 4 sections avec les `fx:id` attendus : `champDossier`, `boutonParcourir` ;
   `sectionInspection`, `labelJournal`, `labelReleve`, `labelOriginaux`, `labelNommage`,
   `labelMelange`, `labelIncoherence` ; `comboSites`, `comboPoints`, `champAnnee`, `champPassage`,
   `labelApercu`, `boutonImporter` ; `zoneProgression`, `barreProgression`, `labelProgression`,
   `labelStatut`, `labelMessage`.
3. Reliez les boutons à leurs `onAction` (`parcourir`, `importer`). Gardez `fx:controller` vers
   `ImportationController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type (combos, champs, barre
      de progression…).

## Definition of Done

- [ ] **Un seul fichier modifié** : `Importation.fxml`.
- [ ] Câblage controleur en 3/3 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
