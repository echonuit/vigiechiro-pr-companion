# ✅ [validation] 2/3 — Construire la vue Validation.fxml

Deuxième sous-tâche : la mise en page (placeholder → vraie vue), avec table, filtres et composant audio.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/validation/view/Validation.fxml`

## Ce qu'il faut faire

1. Étudiez `sites/view/SiteDetail.fxml` (table + colonnes) ; pour l'`AudioView`, inspirez-vous de la
   feature `bibliotheque` que vous venez de construire.
2. Construisez la vraie vue avec les `fx:id` attendus : `lblProgression`, `btnImporter`, `choixFiltre`
   (ComboBox), `tableObservations` (TableView) + colonnes `colEspece`, `colStatut`, `lblDetail`,
   `audioView`, `choixMode` (ComboBox), `btnValider`, `choixTaxon` (ComboBox), `btnCorriger`,
   `chkInclureMode` (CheckBox), `btnExporter`, `lblMessage`.
3. Reliez les boutons à leurs `onAction` (noms définis dans le controleur : `importer`, `valider`,
   `corriger`, `exporter`). Gardez `fx:controller` vers `ValidationController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type.

## Definition of Done

- [ ] **Un seul fichier modifié** : `Validation.fxml`.
- [ ] Câblage controleur en 3/3 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
