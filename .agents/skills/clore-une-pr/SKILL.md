---
name: clore-une-pr
description: Use when a pull request has a verdict and is about to be merged. Covers which jobs actually judge the change, why a red is not always a regression, the squash whose title and body must describe what was really done, and the mother issue that does not close itself.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Clore une pull request

## Loi d'airain

```
ON FUSIONNE SUR UN VERDICT LU, PAS SUR UNE COULEUR APERÇUE
```

Une PR verte se fusionne sans demander : attendre la fait rattraper par des conflits. Mais « verte »
veut dire que les vérifications **qui jugent ce changement** ont conclu, pas qu'un bandeau était vert
au moment où on l'a regardé.

## Annoncer

« J'utilise la compétence clore-une-pr pour fusionner #N. »

## Fonction de garde

```
1. LIRE     le verdict : quelles verifications ont conclu, lesquelles jugent CE changement.
2. JUGER    un rouge avant de le croire : regression, ou bascule ?
3. RELIRE   le titre et le corps : decrivent-ils ce qui a ETE FAIT, pas ce qui etait prevu ?
4. FUSIONNER en squash.
5. VERIFIER `main` apres coup, sur ce que la PR touchait.
6. TENIR    l issue mere : fermee par la PR, ou mise a jour a la main.
```

## Toutes les vérifications ne jugent pas tout

Un changement de documentation n'est jugé par aucun job Java. Attendre `paquet`, `ordre-alternatif` ou
`fuseau-alternatif` sur une PR qui ne touche que des `.md` fait perdre vingt minutes pour un verdict
sans objet.

Ce qui juge, selon ce qu'on a touché :

| Ce que la PR touche | Ce qui la juge |
|---|---|
| documentation, compétences, ADR, workflows | `lint`, `corps`, `titre`, `contrat-fichiers` |
| code de production ou de test | tout, y compris les rejeux en fuseau et en ordre alternatifs |
| une vue ou une capture | `capturer` et les gardes de captures en plus |

**`lint` juge presque tout**, y compris les compétences : il porte les gardes de méthode, les
inventaires et les auto-tests des gardes de CI.

## Un rouge n'est pas toujours une régression

Avant de croire un rouge, on lit ce qu'il dit, et on cherche s'il tient ailleurs.

Vécu le 28 août 2026 : `fuseau-alternatif` a rougi sur une PR de documentation, sur un test d'IHM sans
rapport avec elle - **et le même job était vert sur `main` avec le même contenu fusionné**. C'était une
bascule, pas une régression : le test n'avait aucune attente entre son geste et son assertion.

Une bascule se **consigne**, elle ne se relance pas en silence. Relancer sans rien dire apprend à
ignorer les rouges, ce qui coûte bien plus cher que la minute gagnée.

## Le titre et le corps décrivent ce qui a été fait

Le titre devient la ligne du CHANGELOG, le corps est ce qu'atteint quiconque remonte depuis
`git log`. Tous deux ont été écrits **avant** que le travail ne soit fini.

Une PR grossit : un garde refuse, on corrige, on trouve un défaut adjacent, on l'ajoute. Le corps
écrit à l'ouverture décrit alors un travail plus petit que celui qu'on fusionne.

`gh pr edit --body-file` corrige sans repousser, et la relecture coûte une minute contre un texte que
personne ne pourra plus rectifier.

## L'issue mère ne se ferme pas toute seule

`Closes #N` dans le corps la ferme à la fusion, et **seulement** si le mot-clé est en anglais. Une
fermeture écrite en français fusionne verte en laissant l'issue ouverte.

Quand la PR ne ferme pas son issue - un lot dans un EPIC, une trouvaille qui déborde - l'issue se met
à jour **à la main** : ce qui a été livré, ce qui reste, et le lien vers la PR. Une issue qui garde le
corps d'avant fait lire l'ancien plan comme s'il valait encore.

**L'EPIC compte aussi.** Cocher le lot livré prend dix secondes, et un EPIC dont les cases ne bougent
pas laisse croire qu'il n'avance pas.

## Après la fusion, `main` se vérifie

La CI a jugé la **branche**. Ce qui part en production est la **fusion**, et deux PR vertes séparément
peuvent composer mal.

Relancer ce que la PR touchait, sur `main` mis à jour, coûte une minute. Et un `clean` enchaîné à une
compilation dans la même commande efface les rapports : un « 0 test exécuté » est alors un artefact de
mesure, pas un résultat.

Quand la PR fusionnée était la dernière d'un chantier, fermer les issues ne suffit pas :
[`clore-un-chantier`](../clore-un-chantier/SKILL.md) porte les douze passes, et
[`clore-une-issue`](../clore-une-issue/SKILL.md) rappelle que les deux corps se relisent avant.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'attends que tout soit vert » | Les jobs Java ne jugent pas une PR de documentation |
| « C'est rouge, donc j'ai cassé quelque chose » | Cherchez le même job ailleurs. Une bascule rougit sans avoir lu |
| « Je relance, ça passera » | Une bascule se consigne. La relancer en silence apprend à ignorer les rouges |
| « Le corps décrit bien la PR » | Il décrit ce qu'elle était à l'ouverture. Elle a grossi depuis |
| « La PR est fusionnée, l'issue est close » | Seulement avec `Closes #N` en anglais. Sinon elle est restée ouverte |
| « Les deux PR étaient vertes » | Séparément. C'est leur fusion qui part en production |
