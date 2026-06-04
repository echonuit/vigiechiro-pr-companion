# 📦 [lot] 3/3 — Câbler LotController

Dernière sous-tâche de **M-Lot** : relier la vue au ViewModel et brancher les deux boutons.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/lot/view/LotController.java`

## Démarche (TDD)

1. **Activez** `LotViewTest` (retirez `@Disabled`), **constatez le rouge**.
2. Patron : `SiteDetailController`.
3. Dans `LotController` : déclarez les champs `@FXML` (mêmes noms que les `fx:id`), liez-les aux
   propriétés du ViewModel dans la méthode d'initialisation FXML (statut, récap, dossier, liste
   d'alertes, état activé/désactivé des boutons selon les disponibilités, message), et écrivez les
   **handlers** `@FXML` des deux boutons qui délèguent à `preparer` / `deposer` du ViewModel. La zone
   d'alertes ne doit apparaître qu'en présence d'alertes.
4. **Aucune logique métier** ici. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`LotViewTest` réactivé et vert** — critère d'acceptation de la feature.
- [ ] Dans l'appli, les boutons s'activent/désactivent selon le statut et font progresser le dépôt.

## Definition of Done

- [ ] **Un seul fichier modifié** : `LotController.java` (+ retrait du `@Disabled` du test).
- [ ] Controleur sans logique métier ni accès base (ArchUnit `view_sans_jdbc` vert).
- [ ] Toute la feature lot **verte**, sans régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico (`Closes #…`).
