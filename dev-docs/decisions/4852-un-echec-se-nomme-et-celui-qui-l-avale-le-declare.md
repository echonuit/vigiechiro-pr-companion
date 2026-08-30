---
type: adr
title: "Un échec se nomme, et celui qui l'avale le déclare"
status: stable
article: A3
chantier: "#4852 (clôture, passe 11)"
decided_at: 2026-08-30
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/api/ReponseApiTest.java"
  - "src/test/java/fr/univ_amu/iut/qualification/ServiceQualificationTest.java"
verified:
  - by: machine:ci
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un échec se nomme, et celui qui l'avale le déclare

## Contexte

Deux suites trouvées dans deux features qui ne se connaissent pas racontaient le même défaut : un
échec rendu à l'appelant **comme une absence**. `ReponseApi.enOptionnel()` aplatit quatre issues
distinctes en un vide, et les appelants rendaient les quatre comme « participation introuvable » ou
comme une liste vide. `ServiceQualification.detaillerSelection` retirait d'une liste la séquence
qu'elle n'avait pas su lire, sans un mot.

Les deux mots se ressemblent et n'appellent pas le même geste : on réessaie une coupure, on ne
réessaie pas une participation qui n'existe pas.

L'ADR 0008 tient déjà la forme la plus grossière, le `catch` au corps vide. Ces deux-ci lui échappent
parce qu'elles ne sont pas vides : un `Optional.empty()` rendu pour quatre raisons, un `ifPresent`
qui laisse tomber le cas absent.

## Décision

**Un échec se nomme.** Le type qui porte la distinction la rend jusqu'à l'appelant : `pourquoiVide`
répond à la question que les appels ne posaient pas, et un `404` est la seule issue qui soit vraiment
une absence. `DetailSelection` rend la liste **et** ce qui a été écarté.

**Nommer, pas refuser.** Refuser était l'autre voie, et c'est ce que `ServiceEmport.composer` fait
pour un paquet. Un écran de vérification qui ne s'ouvre plus parce qu'une séquence manque serait un
remède plus dur que le mal ; l'appelant décide, il n'est pas décidé pour lui.

**Un canal qui rend compte est abstrait, jamais par défaut.** `SuiviDepot.reconciliationImpossible`
n'a pas d'implémentation vide : chaque suivi dit ce qu'il en fait. Un défaut vide réintroduirait
exactement le silence que cette décision corrige.

**Et l'avertissement passe avant l'avancement.** Quand la réconciliation n'a pas pu lire, la ligne
d'état le dit **devant** « n/N déposées ». L'avancement n'apprend rien à l'utilisateur qu'il puisse
utiliser ; l'avertissement si, tant qu'il peut encore arrêter et réessayer.

## Le revers : celui qui avale le déclare

L'audit d'harmonisation a relevé **six** boucles de la même forme dans le code de production. Ouvertes
une par une, **cinq déclarent leur ignorance en javadoc** et ne sont donc pas le défaut : « Les noms
non horodatés sont ignorés », « Les colonnes introuvables sont ignorées ».

C'est la seconde moitié de la décision, et c'est elle qui la rend applicable : avaler un cas est
permis quand on l'écrit. Ce qui est interdit, c'est de l'avaler en silence. La sixième ne le déclarait
pas, et elle est consignée en #4875.

## Conséquences

Six sites d'appel nomment désormais leur cause, deux replis assumés restent tels quels parce qu'ils
ne prétendent rien. Une signature publique a changé, consommée à trois endroits.

Le coût est réel : `SuiviDepot` a gagné une méthode abstraite, donc trois implémentations à compléter.
C'est le prix d'un canal qui ne peut pas se taire.

## Alternatives écartées

**Refuser partout, comme `ServiceEmport`.** Cohérent, mais un refus ferme l'écran entier pour un
défaut local. La sélection d'écoute d'une nuit vaut mieux qu'un écran qui ne s'ouvre pas.

**Un défaut vide sur le canal de suivi**, qui aurait évité de toucher trois implémentations. Il aurait
rendu le silence légal, et le silence est ce que cette ADR interdit.

**Ne rien faire, en comptant sur l'ADR 0008.** Elle ne voit que les `catch` vides, et ces deux défauts
n'en sont pas.
