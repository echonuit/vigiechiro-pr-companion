# 🎧 [qualification] 4/4 — Câbler QualificationController

Dernière sous-tâche : relier la vue aux **deux** ViewModels et brancher les actions (verdict, écoute,
régénération, raccourcis clavier).

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/qualification/view/QualificationController.java`

## Contexte

Le controleur reçoit les **deux** ViewModels + le contrat d'ouverture du passage. Sa méthode `ouvrirSur`
(fournie) synchronise déjà les deux ViewModels sur le même passage : ne la touchez pas.

## Démarche (TDD)

1. **Activez** `QualificationViewTest`, **constatez le rouge**.
2. Patron : `SiteDetailController`. Câblez le bandeau et les feux au ViewModel verdict ; la table, la
   progression, le détail et l'écoute au ViewModel sélection ; la surbrillance du verdict choisi,
   l'aperçu R14, l'avertissement et l'activation du bouton « Enregistrer » au ViewModel verdict.
   Ajoutez les **handlers** `@FXML` (poser O/D/J, enregistrer, régénérer, retour passage). Vous pouvez
   ajouter les raccourcis clavier (O/D/J, Entrée, Espace) si le test les couvre.
3. **Aucune logique métier**. Relancez jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`QualificationViewTest` réactivé et vert** — critère d'acceptation de la feature.

## Definition of Done

- [ ] **Un seul fichier modifié** : `QualificationController.java` (+ retrait du `@Disabled`).
- [ ] Controleur sans logique métier (ArchUnit `view_sans_jdbc` vert) ; feature **verte** sans
      régression ; `spotless:check` OK.
- [ ] Livré via **branche + PR relue** ; la PR clôt l'épico.
