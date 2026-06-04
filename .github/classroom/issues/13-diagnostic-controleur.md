# 🔧 [diagnostic] 3/3 — Câbler DiagnosticController

Dernière sous-tâche de **M-Diagnostic** : relier la vue (les `@FXML`) au ViewModel. C'est ce câblage
qui rend l'écran vivant et fait passer le test d'acceptation au vert.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/diagnostic/view/DiagnosticController.java`

## Contexte

Le controleur est une **coquille** : la classe, le constructeur `@Inject` (qui reçoit le
`DiagnosticViewModel`) et la méthode `ouvrirSur` (appelée par la navigation) sont fournis. Un
commentaire `// TODO (M-Diagnostic) …` indique ce qui reste à écrire.

## Démarche (TDD)

1. **Activez le test d'acceptation** : retirez le `@Disabled` de
   `src/test/java/.../diagnostic/view/DiagnosticViewTest.java`, lancez-le et **constatez le rouge**.
2. **Étudiez le patron** : ouvrez le controleur de référence `SiteDetailController` (feature `sites`).
   Il illustre le câblage « pur » : déclarer des champs `@FXML`, et tout lier dans une méthode
   `initialize()`.
3. Dans `DiagnosticController` :
   - déclarez les **champs `@FXML`** correspondant aux `fx:id` de votre `Diagnostic.fxml` (mêmes
     noms) ;
   - écrivez la méthode d'initialisation FXML qui **lie** chaque contrôle à la propriété
     correspondante du ViewModel : le texte des labels aux propriétés texte, les listes aux listes
     observables, et la **reconstruction du graphe** à partir de la série de mesures du ViewModel ;
   - **aucune logique métier ni accès base** : uniquement du câblage propriété ↔ contrôle.
4. Relancez `DiagnosticViewTest` jusqu'au **vert**.

## Critères d'acceptation

- [ ] **`DiagnosticViewTest` est réactivé et vert** (graphe à deux séries, listes peuplées,
      enregistreur affiché) — c'est le critère d'acceptation **de toute la feature**.
- [ ] Dans l'appli (`./mvnw javafx:run`), ouvrir un passage puis l'onglet « Diagnostic matériel »
      affiche le vrai écran (plus le placeholder).

## Definition of Done

- [ ] **Un seul fichier modifié** : `DiagnosticController.java` (+ retrait du `@Disabled` du test).
- [ ] Le controleur ne contient **aucune** logique métier ni accès base (règle ArchUnit
      `view_sans_jdbc` verte).
- [ ] `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` : **toute la feature diagnostic est
      verte**, sans régression ailleurs ; `./mvnw spotless:check` OK.
- [ ] Modification livrée via une **branche + PR relue** ; la PR clôt l'issue chapeau (`Closes #…`).
