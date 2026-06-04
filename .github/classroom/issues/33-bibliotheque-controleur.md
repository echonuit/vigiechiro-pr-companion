# 🔊 [bibliotheque] 3/3 — Câbler BibliothequeController

Dernière sous-tâche : relier la vue au ViewModel. Cet écran n'a pas de méthode `ouvrirSur` : il se
charge **tout seul** à l'ouverture (dans la méthode d'initialisation FXML).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/bibliotheque/view/BibliothequeController.java`

## Démarche (TDD)

1. **Activez** `BibliothequeViewTest`, **constatez le rouge**.
2. Patron : `SiteDetailController` (table + cellules) et la façon dont `sites` reconstruit ses cartes.
3. Dans le controleur : déclarez les champs `@FXML` (mêmes noms que les `fx:id`, dont l'`AudioView`),
   câblez la table et ses colonnes, la sélection, le détail, l'écoute (lier la source audio au son
   sélectionné), l'état du bouton d'export, et **déclenchez le chargement initial** (appel à `charger`
   du ViewModel) en fin d'initialisation. Ajoutez le **handler** `@FXML` d'export.
4. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`BibliothequeViewTest` réactivé et vert** — critère d'acceptation de la feature.
- [ ] Dans l'appli, l'écran liste les sons, lit l'audio sélectionné et propose l'export.

## Definition of Done

- [ ] **Un seul fichier modifié** : `BibliothequeController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit `view_sans_jdbc` vert) ; feature **verte** sans
      régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico.
