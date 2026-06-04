# 🎧 [qualification] 3/4 — Construire la vue Qualification.fxml

Troisième sous-tâche : la mise en page (placeholder → vraie vue) en deux colonnes (liste d'écoute à
gauche, détail + verdict à droite).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/qualification/view/Qualification.fxml`

## Ce qu'il faut faire

1. Étudiez `sites/view/SiteDetail.fxml` (bandeau + table) ; pour l'`AudioView`, réutilisez ce que vous
   avez fait en `bibliotheque` / `validation`.
2. Construisez la vue avec les `fx:id` attendus (visibles dans `QualificationController`), notamment :
   `racine`, fil d'Ariane et bandeau (`lblFilAriane`, `lblTitreContexte`, `lblPlageHoraire`,
   `lblVolumetrie`, `lblVerdictActuel`, `lblStatut`), les 3 feux (`feuCouverture`, `feuNombre`,
   `feuRenommage`, `lblAnomalie`), la sélection (`tableSequences` + colonnes `colPosition`,
   `colFichier`, `colDuree`, `colEcoute`, `barreProgression`, `lblProgression`), le détail
   (`lblSeqNumero`, `lblSeqMeta`, `audioView`), le verdict (`boutonOk`, `boutonDouteux`, `boutonAJeter`,
   `champCommentaire`, `lblApercuR14`, `lblAvertissement`, `boutonEnregistrer`), `lblMessage`.
3. Reliez les boutons à leurs `onAction` (noms dans le controleur). Gardez `fx:controller` vers
   `QualificationController`.

## Critères d'acceptation

- [ ] Le FXML se charge sans erreur ; tous les `fx:id` présents et du bon type.

## Definition of Done

- [ ] **Un seul fichier modifié** : `Qualification.fxml`.
- [ ] Câblage controleur en 4/4 ; un test rouge à cause du controleur est normal ici.
- [ ] Livré via **branche + PR relue**.
