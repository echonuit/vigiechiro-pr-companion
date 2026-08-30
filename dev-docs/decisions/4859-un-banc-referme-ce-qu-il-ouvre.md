---
type: adr
title: "Un banc referme ce qu'il ouvre"
status: stable
article: A9
chantier: "#4859, sous-chantier du lot 2 de l'EPIC #4804"
decided_at: 2026-08-30
verification: probable
verification_note: "le compte est DIFFÉRENTIEL et se mesure autour d'une suite complète, donc dans le job et non dans un test : deux pas encadrent la suite dans maven.yml"
enforced_by:
  - "scripts/methode/compte-les-reliquats.py"
ratchet: 138
verified:
  - by: human:nedseb
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un banc referme ce qu'il ouvre

## Contexte

La suite abandonnait ses répertoires temporaires. Ils se sont accumulés jusqu'à **13 730**, ont rempli
un tmpfs de 16 Go, et la suite s'est alors mise à échouer sur un message de quota qui envoie chercher
une régression de code (#4737). Le plus ancien datait de cinq jours.

Trois classes en faisaient un quart à elles seules, et leur correctif a ramené leur part à zéro. Il
reste **198 répertoires par passage**, venant de 96 fichiers.

## Ce qui a laissé le compte monter

Rien ne le comptait. Un banc qui appelle `Files.createTempDirectory` et n'enlève rien est **vert**, et
le restera : son défaut ne se voit qu'ailleurs, plus tard, sur une machine dont le disque se remplit.
C'est un acquis sans gardien, et chaque banc écrit depuis a rappelé la même ligne.

## Décision

**Un banc referme ce qu'il ouvre.** `@TempDir` de JUnit crée le répertoire et le supprime en fin de
test, sans rien à écrire ; le dépôt l'emploie déjà 259 fois. Une aide partagée, qui n'est pas un test
et à qui `@TempDir` ne s'injecte pas, supprime en fin de fork.

**Et le compte se tient**, par un cliquet qui ne peut que descendre. Il vaut **138**, après que
les dix bancs de #4868 en ont retiré 64 des 202 mesurés à sa pose. C'est l'article A9 : la
dette se tient par un cliquet, pas par un nettoyage. Le nettoyage manuel du 30 août a retiré 5,8 Go
et n'a rien empêché ; il se refera tant que rien ne compte.

## Le compte est DIFFÉRENTIEL, et c'est la moitié de la décision

Compter le **total** de `/tmp` ferait rougir le reliquat de la veille, sur le poste de n'importe qui,
sans que rien ait changé dans le dépôt. Le garde serait désarmé en une semaine, et le dépôt aurait un
dispositif de plus qui ne juge rien ([ADR 2748]).

Ce qui se compte est donc ce que la suite **ajoute** : un relevé avant, un relevé après, et la
différence. Le garde **refuse** de conclure si le relevé d'avant manque, plutôt que de rendre un total
plausible.

## Pourquoi ce garde n'est pas un test

Il faut compter quand tous les forks ont rendu la main. Un test tourne *pendant* : il ne voit ni les
forks voisins ni ce qui suit. Le garde vit donc dans le job, en deux pas qui encadrent la suite, et
c'est la seule place d'où la propriété soit observable.

## La limite, mesurée et écrite

Le compte attribue à la suite **tout ce qui apparaît pendant**, y compris ce qu'une autre session
crée au même moment. Mesuré le 30 août : une classe seule a rendu **237** alors que la suite complète
en laisse 198, parce qu'un second plan de travail jouait ses tests en parallèle sur la même machine.

En CI le runner est dédié, et la question ne se pose pas. En local, on lance la suite seule ou l'on ne
croit pas le chiffre. Distinguer les répertoires par leur créateur demanderait de les préfixer par le
processus, ce que ni JUnit ni le dépôt ne font ; le dire coûte moins que de l'outiller.

## Ce qui prouve que le garde voit

Retirer le `@TempDir` de `GestionnaireVuesTest` et rendre son `createTempDirectory` fait passer le
compte de **0 à 14**. Le remettre le ramène à 0.

Cette épreuve a d'abord rendu **0 dans les deux cas**, et j'ai failli conclure : la mutation ne
compilait pas, `Files` n'étant plus importé depuis la conversion, si bien que le banc tournait sur du
code sain. Une mutation qui ne compile pas ne prouve rien, et elle se présente comme un succès.

## Ce que cette décision empêche

Elle interdit d'ajouter un `createTempDirectory` sans nettoyage : le compte monterait, et le cliquet
refuserait. Elle interdit aussi de baisser le seuil sans avoir converti : le cliquet se lit dans cette
ADR, et le modifier est une décision qui se relit.

## Conséquences

- Le cliquet est celui que **la CI mesure**, jamais celui du poste où l'on écrit : à sa pose, mon poste
  rendait 198 et la CI 202, le nombre de forks suivant la machine (`forkCount=1C`). Il est descendu à
  **138** quand les dix bancs de #4868 ont cessé d'en laisser 64.
- Les 58 bancs qui créent dans un `@Start` ou un `@BeforeEach` se convertissent par lots, et le
  cliquet descend d'autant.
- Les outils `Capture*` de production ne sont pas visés : leur reliquat meurt avec le runner.

[ADR 2748]: https://companion-dev.echonuit.fr/decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit/
