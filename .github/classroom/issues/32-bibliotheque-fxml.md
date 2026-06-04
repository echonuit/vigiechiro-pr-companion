# 🔊 [bibliotheque] 2/3 — Construire la vue Bibliotheque.fxml

Deuxième sous-tâche : la mise en page (placeholder → vraie vue), avec le composant audio fourni.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/bibliotheque/view/Bibliotheque.fxml`

## Ce qu'il faut faire

1. Étudiez `sites/view/MesSites.fxml` et `sites/view/SiteDetail.fxml`.
2. Construisez la vraie vue avec les `fx:id` attendus : `lblResume`, `tableEntrees` (TableView) et ses
   colonnes `colTaxon`, `colSequence`, `colFrequence`, `lblDetail`, `audioView`, `btnExporter`,
   `lblMessage`.
3. `audioView` est le **composant fourni** `AudioView` (réutilisé de M-Vision-Tadarida) : déclarez-le
   comme un nœud du même type que dans la vue d'origine. Reliez le bouton d'export à son `onAction`.
4. Gardez `fx:controller` vers `BibliothequeController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type (TableView, colonnes,
      AudioView, Button).

## Definition of Done

- [ ] **Un seul fichier modifié** : `Bibliotheque.fxml`.
- [ ] Câblage controleur en 3/3 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
