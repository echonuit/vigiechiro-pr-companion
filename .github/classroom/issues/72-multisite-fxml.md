# 🗂 [multisite] 2/5 — Construire la vue Multisite.fxml

Deuxième sous-tâche : la mise en page de l'écran principal (placeholder → vraie vue).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/multisite/view/Multisite.fxml`

## Ce qu'il faut faire

1. Étudiez `sites/view/SiteDetail.fxml` (table + barre d'outils).
2. Construisez la vue avec les `fx:id` attendus : `lblResume` ; barre de filtres `champCarre`,
   `choixStatut`, `choixVerdict`, `champAnnee`, `boutonReinitialiser`, `boutonGererVues`, `choixTri`,
   `boutonExporter` ; le tableau `tableLignes` + colonnes `colCarre`, `colPoint`, `colAnnee`,
   `colNumero`, `colDate`, `colStatut`, `colVerdict` ; `lblMessage`.
3. Reliez les boutons à leurs `onAction` (noms dans le controleur : `reinitialiser`, `gererVues`,
   `exporter`). Gardez `fx:controller` vers `MultisiteController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type.

## Definition of Done

- [ ] **Un seul fichier modifié** : `Multisite.fxml`.
- [ ] Câblage controleur en 3/5 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
