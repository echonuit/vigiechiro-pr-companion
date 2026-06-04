# 🔧 [diagnostic] 2/3 — Construire la vue Diagnostic.fxml

Deuxième sous-tâche de **M-Diagnostic** : la **mise en page** de l'écran. Aujourd'hui le fichier ne
contient qu'un *placeholder* « à construire » ; vous écrivez la vraie vue.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/diagnostic/view/Diagnostic.fxml`

## Ce qu'il faut faire

1. Ouvrez la vue de référence **`src/main/java/fr/univ_amu/iut/sites/view/SiteDetail.fxml`** pour voir
   comment on structure un écran (conteneurs `VBox`/`HBox`, `Label`, `styleClass`, `fx:id`).
2. Remplacez le contenu placeholder par la **vraie mise en page** de M-Diagnostic, qui doit déclarer
   les **`fx:id`** que le controleur attend (les noms sont visibles dans `DiagnosticController` et
   dans le test) :
   - `lblEnregistreur` (Label) — l'enregistreur de la nuit ;
   - `lblReleveAbsent` (Label) — alerte « relevé climatique absent » (R20) ;
   - `lblResumeClimat` (Label) — résumé de la série climatique ;
   - `grapheClimat` (**LineChart**) — la courbe T°/hygrométrie ;
   - `listeAnomalies`, `listeEvenements` (**ListView**) — anomalies (R19) et évènements du journal ;
   - `lblGps` (Label) et `lblMessage` (Label).
3. Gardez `fx:controller="fr.univ_amu.iut.diagnostic.view.DiagnosticController"` sur l'élément racine.
4. Conseil : vous pouvez prototyper la mise en page avec **SceneBuilder**, mais le rendu final doit
   être ce fichier FXML versionné.

## Critères d'acceptation

- [ ] Le FXML se **charge sans erreur** (aucune exception `LoadException` au lancement de l'appli).
- [ ] Tous les `fx:id` ci-dessus sont présents et du **bon type** (un `LineChart` pour `grapheClimat`,
      des `ListView` pour les listes).
- [ ] L'élément racine porte bien `fx:controller` vers `DiagnosticController`.

## Definition of Done

- [ ] **Un seul fichier modifié** : `Diagnostic.fxml`.
- [ ] `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` ne régresse pas (le placeholder
      n'existe plus, mais le câblage controleur arrive à la sous-tâche suivante : si un test reste
      rouge à cause du controleur, c'est normal à ce stade — il deviendra vert en 3/3).
- [ ] Modification livrée via une **branche + PR relue** ; la PR référence cette issue.
