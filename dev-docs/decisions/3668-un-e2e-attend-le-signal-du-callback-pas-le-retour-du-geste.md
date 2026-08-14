# ADR 3668 : Un E2E attend le signal du callback asynchrone, pas le retour du geste qui l'a déclenché

- **Statut** : Accepté - 2026-08-14
- **Chantier** : #3668 (PR #3716, #3722 ; audit #3717)
- **Vérification** : humaine - revue de code : tout `robot.interact(...)` (ou appel direct dans
  `@Start`) qui déclenche `occupation.occuper(...)` sur un test câblé au vrai `RacineInjecteur`
  doit être suivi d'un `WaitForAsyncUtils.waitFor(timeout, TimeUnit, prédicat)` sur l'état attendu
  avant toute assertion qui en dépend. Aucun scan mécanique ne peut distinguer un `interact` suivi
  d'une attente correcte d'un `interact` suivi d'une attente insuffisante (`waitForFxEvents()` est
  légitime ailleurs, quand rien d'asynchrone n'est en jeu) : c'est une décision de méthode, pas un
  invariant qui se prouve par grep.

## Contexte

La CI (`fuseau-alternatif`, qui rejoue toute la suite sous un fuseau lent) faisait apparaître des
E2E rouges de façon non reproductible en local, sur des tests sans rapport avec la modification en
cours - et le test qui tombait changeait d'une exécution à l'autre (#3668).

Cause commune : `robot.interact(...)` (ou un appel direct dans `@Start`) déclenche
`occupation.occuper(...)` qui, sur un test câblé au vrai `RacineInjecteur`, tourne sur le vrai
`ExecuteurTacheAsynchrone` (thread virtuel + `Platform.runLater`) - pas sur l'`ExecuteurTacheSynchrone`
que les tests de vue/ViewModel reçoivent par défaut (liaison Guice `@ImplementedBy`, #793).
L'assertion qui suivait lisait donc l'état **avant** que le callback de succès ne l'ait posé.
`WaitForAsyncUtils.waitForFxEvents()` ne corrige rien : il vide la file du fil FX, il n'attend pas
le thread d'arrière-plan qui doit encore la remplir.

Sur une machine rapide, la fenêtre de course est sous la seconde et le test passe presque toujours.
Sur un runner chargé, elle s'élargit assez pour rougir, une fois sur N, un test qui n'a rien à voir
avec la modification en cours de revue - le pire genre de rouge à traiter, celui qui accuse le
mauvais coupable.

## Décision

Après tout geste qui déclenche un chargement via `occupation.occuper(...)` sur le vrai injecteur,
l'assertion qui en dépend est précédée d'un `WaitForAsyncUtils.waitFor(timeout, TimeUnit, () ->
<prédicat observable>)` sur l'état attendu (contenu de table, sélection, propriété `disable`) -
jamais une assertion immédiate, jamais un simple `waitForFxEvents()`.

**Méthode de diagnostic et de validation**, reproductible pour tout défaut de la même famille :
injecter un délai temporaire dans `ExecuteurTacheAsynchrone` pour faire rougir le test de façon
fiable en local (reproduction empirique d'une course qui ne se manifeste qu'aléatoirement en CI) ;
valider la rigueur du correctif en vérifiant qu'un délai largement surdimensionné fait toujours
échouer proprement (`TimeoutException`) plutôt que de masquer la course par une attente généreuse ;
retirer le délai de diagnostic avant commit.

Décision négative, qui compte aussi : ne pas toucher `ExecuteurTacheAsynchrone` (production). Le
comportement asynchrone est voulu (IHM réactive pendant un traitement bloquant) ; le défaut est
dans la discipline d'attente du **test**, pas dans le contrat de production.

## Conséquences

- 6 fichiers E2E corrigés (#3716, #3722), aucun fichier `src/main` touché par ce chantier.
- Un site résiduel du même défaut, hors périmètre du grep initial de #3717 car déclenché depuis
  `@Start` et non via `interact(...)` : `ParcoursPublierCorrectionsE2ETest.java`, signalé en #3733.
- `dev-docs/tests-et-qualite.md` précise désormais que le double synchrone de l'exécuteur ne couvre
  que les tests de vue/ViewModel, pas les E2E (quatrième des « pièges récurrents »).
- `occupation.occuper` a 11 appelants recensés dans `src/main` (passe 7 de la clôture) : toute E2E
  future qui en exerce un est exposée à la même classe de défaut si elle n'applique pas cette
  décision.

## Alternatives écartées

- **Boucler `waitForFxEvents()` avec un `Thread.sleep`** : masque la course sans la nommer, ralentit
  la suite, et reste probabiliste (aucune garantie contre une machine encore plus lente).
- **Forcer l'exécuteur synchrone dans les E2E**, comme les tests de vue : contredit le but même d'un
  test E2E, qui vérifie le câblage **réel**, y compris son comportement asynchrone.
