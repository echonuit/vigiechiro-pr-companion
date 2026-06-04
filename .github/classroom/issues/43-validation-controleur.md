# ✅ [validation] 3/3 — Câbler ValidationController

Dernière sous-tâche : relier la vue au ViewModel et brancher les quatre actions.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/validation/view/ValidationController.java`

## Démarche (TDD)

1. **Activez** `ValidationViewTest`, **constatez le rouge**.
2. Patron : `SiteDetailController`. Dans le controleur : déclarez les champs `@FXML`, câblez la table
   et ses colonnes (libellés via les utilitaires fournis), le filtre, la sélection, le détail, l'écoute
   audio, les combos (mode, taxon), l'activation des boutons selon la sélection / la disponibilité des
   résultats, la progression et le message. Ajoutez les **handlers** `@FXML` des quatre boutons
   (import / valider / corriger / export) qui délèguent au ViewModel. Les sélecteurs de fichiers natifs
   (import / export) vivent dans la vue.
3. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`ValidationViewTest` réactivé et vert** — critère d'acceptation de la feature.

## Definition of Done

- [ ] **Un seul fichier modifié** : `ValidationController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit `view_sans_jdbc` vert) ; feature **verte** sans
      régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico.
