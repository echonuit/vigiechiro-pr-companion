# 🔧 [diagnostic] 1/3 — Implémenter DiagnosticViewModel.ouvrirSur

Première sous-tâche de **M-Diagnostic**. On commence par le **ViewModel** (la logique), avant la vue :
c'est lui qui transforme les données du service en propriétés observables que la vue affichera.

## Fichier à modifier (un seul)

`src/main/java/fr/univ_amu/iut/diagnostic/viewmodel/DiagnosticViewModel.java`

## Contexte

Les **propriétés observables** (enregistreur, série climatique, anomalies, évènements, GPS, message)
et leurs **getters** sont déjà fournis. Il vous reste à écrire le **corps** de la méthode publique
`ouvrirSur`, repérée par un commentaire `// TODO (M-Diagnostic) …` qui décrit précisément l'attendu.

## Démarche (TDD)

1. **Activez le test d'abord** : dans
   `src/test/java/.../diagnostic/viewmodel/DiagnosticViewModelTest.java`, retirez l'annotation
   `@Disabled`, lancez-le et **constatez le rouge**. Les assertions du test sont votre cahier des
   charges : lisez-les, elles disent exactement quelles propriétés doivent valoir quoi.
2. **Écrivez `ouvrirSur`** : récupérez le diagnostic du passage auprès du service fourni
   (`ServiceDiagnostic`), puis renseignez chaque propriété observable à partir de ce diagnostic
   (l'enregistreur, la présence/absence de relevé climatique, la disponibilité GPS, la série de
   mesures, les anomalies, les évènements, le résumé). En cas d'erreur, l'écran doit rester vide et
   afficher un **message** : aucune exception ne doit remonter.
3. **Inspirez-vous** du ViewModel de référence `SiteDetailViewModel` (feature `sites`) pour le style.
4. Relancez le test jusqu'au **vert**.

> ⚠️ N'écrivez pas la solution « de tête » : laissez le test vous guider, propriété par propriété.

## Critères d'acceptation

- [ ] `DiagnosticViewModelTest` est **réactivé** (plus de `@Disabled`) et **vert**.
- [ ] `ouvrirSur` ne laisse remonter **aucune exception** : un passage introuvable se traduit par un
      message dans la propriété `message`, pas par un crash.

## Definition of Done

- [ ] **Un seul fichier modifié** : `DiagnosticViewModel.java` (+ le retrait du `@Disabled` de son
      test, qui fait partie de la même tâche TDD).
- [ ] Le ViewModel n'importe **que** `javafx.beans` / `javafx.collections` (jamais `javafx.scene`) →
      test **ArchUnit** vert.
- [ ] `./mvnw -Dglass.platform=Headless -Dprism.order=sw test` ne régresse pas ; `./mvnw spotless:check` OK.
- [ ] Modification livrée via une **branche + PR relue** ; la PR référence cette issue.
