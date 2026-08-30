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

**La documentation de ce dépôt est testée comme du code, et c'est ce qu'on oublie.** Huit classes de
test lisent des fichiers de documentation et refusent quand ils dérivent : `DocumentationAJourTest`,
`NomDeLApplicationTest`, `ConventionsDEcritureTest`, `AnnonceDesMutationsTest`, et les quatre de
`recette` qui tiennent les sessions, les clips et leur correspondance.

Une PR qui ne touche que des `.md` est donc jugée par `build`, et par les deux rejeux qui relancent la
**suite entière** sans filtre. Seul `paquet` en est vraiment dispensé : il assemble avec `-DskipTests`.

Ce qui juge, selon ce qu'on a touché :

| Ce que la PR touche | Ce qui la juge |
|---|---|
| documentation, compétences, ADR, workflows | `lint`, `corps`, `titre`, `contrat-fichiers`, **et `build`**, `ordre-alternatif`, `fuseau-alternatif` |
| code de production ou de test | tout, y compris `paquet` |
| une vue ou une capture | `capturer` et les gardes de captures en plus |

**`lint` juge presque tout**, y compris les compétences : il porte les gardes de méthode, les
inventaires et les auto-tests des gardes de CI.

**Ce que cette page a enseigné de faux, et ce qu'il en a coûté.** Elle affirmait qu'*un changement de
documentation n'est jugé par aucun job Java*, et son tableau omettait `build`. Le 30 août 2026, #4923
a été fusionnée avec les quatre vérifications que cette page désignait, toutes vertes. `build` a
conclu **douze minutes plus tard** : deux ADR portaient un titre en en-tête et un autre sur leur page.
`main` est resté rouge une heure, et deux demandes ouvertes ensuite en ont hérité.

La fusion était **correcte selon cette page**. Ce n'était pas une inattention, et c'est ce qui rend
l'erreur coûteuse : une consigne fausse est suivie exactement.

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

**Et la tâche du changement OpenSpec, s'il y en a un.** Elle se coche dans la branche, avec le
travail, donc **avant** la fusion : c'est le dernier moment où le geste est encore gratuit. Après, il
faut une demande pour cocher une case.

Ce n'est pas une exigence neuve, c'est ce qui s'est déjà fait : les dix-sept tâches du changement
`emporter-une-nuit` ont été cochées par les commits qui les réalisaient. Rien ne le demandait, et
c'est bien le problème - une habitude tient tant que la même personne travaille.

Une tâche non cochée ne se rattrape pas, elle se **découvre** : à l'archivage, quand `tasks.md`
décrit un plan que le code a déjà dépassé, et qu'on ne sait plus lequel des deux avait raison.

## Après la fusion, `main` se vérifie

La CI a jugé la **branche**. Ce qui part en production est la **fusion**, et deux PR vertes séparément
peuvent composer mal.

Relancer ce que la PR touchait, sur `main` mis à jour, coûte une minute. Et un `clean` enchaîné à une
compilation dans la même commande efface les rapports : un « 0 test exécuté » est alors un artefact de
mesure, pas un résultat.

Une fois fusionnée, [`clore-une-issue`](../clore-une-issue/SKILL.md) prend la suite : l'issue se met
au net et se ferme. Et fermer la dernière issue d'un chantier n'est pas le clore -
[`clore-un-chantier`](../clore-un-chantier/SKILL.md) porte les quatorze passes.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « C'est de la doc, aucun job Java ne la juge » | Huit classes de test lisent la documentation. Cette page a enseigné le contraire, et `main` en est resté rouge une heure |
| « Quatre vérifications sur cinq sont vertes » | Nommez celle qui manque. Compter les vertes ressemble à vérifier, et c'est le geste qui a fusionné #4923 |
| « J'attends que tout soit vert » | Seul `paquet` ne juge pas un `.md` : il assemble avec `-DskipTests`. Les deux rejeux, eux, relancent la suite entière |
| « C'est rouge, donc j'ai cassé quelque chose » | Cherchez le même job ailleurs. Une bascule rougit sans avoir lu |
| « Je relance, ça passera » | Une bascule se consigne. La relancer en silence apprend à ignorer les rouges |
| « Le corps décrit bien la PR » | Il décrit ce qu'elle était à l'ouverture. Elle a grossi depuis |
| « La PR est fusionnée, l'issue est close » | Seulement avec `Closes #N` en anglais. Sinon elle est restée ouverte |
| « Les deux PR étaient vertes » | Séparément. C'est leur fusion qui part en production |
