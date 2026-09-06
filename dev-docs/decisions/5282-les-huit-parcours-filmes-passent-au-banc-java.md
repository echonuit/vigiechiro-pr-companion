---
type: adr
title: "Les huit parcours filmés passent au banc Java"
status: stable
article: A9
chantier: "#5282 (convergence des deux bancs filmés), lot #5311"
decided_at: 2026-09-06
verification: humaine
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Les huit parcours filmés passent au banc Java

## La question, et pourquoi aucun raisonnement ne pouvait y répondre

Le dépôt tient **deux** bancs filmés : celui de recette, en Java, et celui de documentation, en bash
(`filme-un-parcours.sh`, 2 065 lignes, plus 1 295 lignes d'orchestration). Le
[spike de convergence](../spikes/convergence-des-deux-bancs.md) a établi que les deux raisons d'être
du second - une décoration de fenêtre, un sélecteur de fichiers visible - étaient **techniquement**
solubles côté Java. Il s'est arrêté là, en écrivant qu'il ne mesurait ni le coût d'un parcours porté,
ni ce qu'un clip réel donnerait.

Ce chantier a livré les deux manquants et filmé le parcours. **La réponse est oui.**

## Ce que le clip établit, et qui ne se déduisait pas

`ParcoursImporterUneNuitTest` rend **une prise de 30 secondes** où le clic sur « Parcourir » ouvre un
dialogue, où le dossier se désigne, et où le chemin désigné arrive dans l'écran. Aucune composition
après coup, aucune concaténation.

Les trois choses que le montage de validation avouait fausses sont levées ensemble. La troisième est
celle qui comptait : le montage annonçait `/media/VIGIECHIRO/bruts` quand le clip réel remplissait le
champ avec un dossier temporaire, et il en tirait qu'« un double visible devra rendre un chemin
montrable ». Le dialogue étant réel, il montre le vrai chemin - et `CarteDeRecette.materialiser` rend
`/tmp/vc-carte-sd-nominale857838373229970727`, qui se tape à l'écran pendant six secondes. Le premier
tournage était **vert** et le clip était mauvais.

## Le coût réel, mesuré, et il va d'abord dans le mauvais sens

| | corps d'un parcours | orchestration derrière |
|---|---:|---:|
| bash (`parcours_importer_une_nuit`) | **37** lignes utiles | 2 065 + 1 295 |
| Java (`ParcoursImporterUneNuitTest`) | **158** lignes utiles | `BancDeRecette`, déjà là pour la recette |

Un parcours Java est **quatre fois plus long** que son équivalent bash. C'est la mesure que le spike
demandait, et elle contredit l'intuition qui portait le chantier.

Elle ne décide pourtant pas contre, parce que le total dit l'inverse : **8 × 37 + 3 360 = 3 656**
lignes contre **8 × 158 = 1 264**. L'orchestration bash n'a pas d'équivalent à écrire côté Java, elle
disparaît - le banc de recette la porte déjà, et il est éprouvé tous les jours par les cas de recette.

**Deux obstacles n'en font qu'un.** `monter_la_carte` monte un volume `udisksctl` étiqueté
`VIGIECHIRO` pour qu'il paraisse dans le sélecteur **natif**, que le parcours lit ensuite par OCR.
Rendre le dialogue interne à l'application retire du même geste la raison d'être du montage, de l'OCR
et de `xdotool` : 62 sites disparaissent sans être portés.

## Décision

**Les huit parcours de documentation migrent vers le banc Java.** La migration se fait un parcours à
la fois, chacun jouant son geste sans substituer son sélecteur.

**Ce qui n'est pas décidé ici.** Le retrait de `filme-un-parcours.sh` et la descente du cliquet de
l'[ADR 5188](5188-bash-disparait-une-tolerance-est-un-delai.md) attendent que les huit soient portés :
un banc retiré avant que son remplaçant couvre tout laisse la documentation sans clips.

## Conséquences

**Un parcours de documentation est un artefact DISTINCT d'un cas de recette**, et le rester. Le cas de
recette doit demeurer déterministe et rapide : le faire passer par un vrai dialogue lui ajouterait des
clics pour un service qui n'est pas le sien. Le parcours ne cite d'ailleurs aucun cas, et le banc le
filme par `-Drecette.film.tout`.

**La tolérance de l'ADR 5188 sur `lance-test-filme.sh` a maintenant une condition datée.** Elle dit
« tolérées tant que le banc Java n'est pas définitivement validé. La levée de cette condition
déclenche la conversion ». Cette ADR est cette levée ; la conversion suit la migration des parcours,
pas cette décision.

**Un clip se REGARDE.** Le premier tournage passait au vert et ne documentait rien. Un test de
documentation dont on ne lit que le verdict garde une exécution, pas un film.
