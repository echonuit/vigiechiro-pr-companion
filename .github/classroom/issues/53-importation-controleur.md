# 📥 [importation] 3/3 — Câbler ImportationController (import en arrière-plan)

Dernière sous-tâche : relier la vue au ViewModel **et** lancer l'import lourd hors du fil JavaFX pour
ne pas figer l'IHM.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/importation/view/ImportationController.java`

## Démarche (TDD)

1. **Activez** `ImportationViewTest`, **constatez le rouge**.
2. Patron : `SiteDetailController`. Cet écran n'a pas de `ouvrirSur` : il **charge les sites tout
   seul** en fin d'initialisation FXML.
3. Dans le controleur : déclarez les champs `@FXML`, câblez les 4 sections (affichage du chemin,
   visibilité de l'inspection, libellés et avertissements, combos site/point, champs année/passage,
   aperçu du préfixe, activation du bouton importer, zone de progression). Handlers `@FXML` :
   - **parcourir** : ouvrir le sélecteur de dossier natif puis lancer l'inspection ;
   - **importer** : capturer la demande, passer en « en cours », puis exécuter l'import **sur un fil
     d'arrière-plan** (Java propose les *virtual threads*) en relayant la progression et le résultat
     **sur le fil JavaFX**. Étudiez bien la doc des méthodes `preparerImport` / `executerImport` /
     `marquer…` du ViewModel : elles sont conçues pour ce découpage thread-safe.
4. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`ImportationViewTest` réactivé et vert** — critère d'acceptation de la feature.
- [ ] Pendant un import, l'IHM **ne gèle pas** ; la barre de progression avance ; le formulaire est gelé.

## Definition of Done

- [ ] **Un seul fichier modifié** : `ImportationController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit `view_sans_jdbc` vert) ; feature **verte** sans
      régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico.
