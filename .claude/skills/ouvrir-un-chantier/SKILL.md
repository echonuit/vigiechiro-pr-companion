---
name: ouvrir-un-chantier
description: Use when opening a chantier or EPIC, before any issue is cut. Covers the four opening steps in their order, and the threshold above which a lot becomes a sous-chantier instead of a checkbox.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: AGENTS.md et CONTRIBUTING.md §5, avec l'ADR 4712 pour le palier du sous-chantier
---

# Ouvrir un chantier

## Loi d'airain

```
UN LOT SE DIMENSIONNE AVANT DE S'ACCROCHER
```

Découper est un geste de structure, pas une mise en liste. Un lot qu'on accroche sans avoir demandé
combien de PR il portera devient une case à cocher sous laquelle du travail s'accumule sans
périmètre, sans bilan et sans clôture.

## Annoncer

« J'utilise la compétence ouvrir-un-chantier pour ouvrir <l'EPIC>, étape par étape. »

## Les cinq étapes

| # | Étape | Ce qu'elle rend | Qui la porte |
|---|---|---|---|
| 0 | Trier et regrouper l'existant | les rattachements tranchés, les titres et corps recadrés | `trier-les-issues` |
| 1 | Cartographier | les patterns réutilisables **nommés**, avant d'écrire du neuf | cette compétence |
| 2 | Planifier | ce que le chantier livre, et ce qu'il ne livre pas | cette compétence |
| 2b | Décider de la spécification | la question posée, et **sa réponse écrite** | `openspec-explore`, puis `openspec-propose` |
| 3 | Découper | des lots dimensionnés, puis des issues ou des sous-chantiers | cette compétence, et [ADR 4712] |

L'étape 0 vient **en premier** parce qu'une issue est rattachée au chantier qui a remarqué son
symptôme, pas à celui qui traite sa cause. Sans elle, le recoupement entre deux chantiers ne se
découvre qu'au conflit de fusion.

L'étape 1 vient **avant** le plan, sinon le plan invente ce qui existe déjà. Le dépôt porte des
patrons éprouvés, et un chantier qui les redécouvre paie deux fois.

## L'étape 2b : la question se pose, et la réponse s'écrit

**Ce chantier change-t-il ce que le produit FAIT ?** La question se pose à chaque ouverture, et la
réponse s'écrit. Un « non » est une décision ; un silence est un oubli, et les deux se ressemblent
trop pour qu'on les laisse se confondre.

Si oui : `/instruire` puis `/proposer`. Le changement porte alors la spécification, la conception et
ses tâches, et **ces tâches sont les lots** de l'étape 3.

Si non : l'écrire en une phrase dans l'EPIC, comme la clôture écrit « sans objet » sur une passe qui
n'avait rien à faire. « Ce chantier ne change que des gardes », « il ne touche que de la prose », « son
delta n'a aucun effet observable » sont des réponses, et elles se relisent.

**Pourquoi une lettre et pas un numéro.** L'étape prolonge le plan sans être indépendante : on ne
décide pas d'une spécification avant de savoir ce qu'on livre. Et elle précède le découpage parce que
les tâches du changement **sont** les lots, ce que le premier changement du dépôt a montré. La règle
générale reste qu'une étape porte un numéro ; la lettre dit la continuité, comme pour la passe `6b` de
la clôture (#4839).

**Ce que ce geste a coûté de n'exister pas.** La clôture archive un changement OpenSpec depuis #4518,
en passe 10. Rien ne disait d'en créer un. Deux changements existaient à l'ouverture de ce lot, et
tous deux avaient été créés par le chantier qui construisait l'outil, jamais parce que la méthode le
demandait (#4907).

## Le palier du sous-chantier, à l'étape 3

**Plus de deux PR cohérentes, c'est un chantier.** Pour chaque lot, la question se pose avant de
l'accrocher :

- une ou deux PR : des issues rattachées à l'EPIC suffisent ;
- plus de deux, ou plusieurs issues déjà identifiées : le lot **s'ouvre en sous-chantier**, un EPIC
  enfant, ses issues s'y rattachent, et la case du parent pointe vers lui.

Un lot multi-PR sous une case à cocher n'a ni périmètre écrit, ni bilan, ni les quatorze passes de
clôture. Il se termine sans que personne sache s'il est fini.

**Le piège est que la forme observée enseigne l'erreur.** Qui cherche comment faire regarde les EPIC
en place et les imite. Au 2026-08-29, l'EPIC #4511 portait **sept lots et zéro sous-chantier**, et
#3848 quatre lots et zéro. La forme observée n'est pas la règle, et le porteur du produit a dû
redemander celle-ci plusieurs fois avant qu'elle soit écrite.

La loupe `scripts/adr/loupe-4712-lots-multi-pr.py` met les lots de chaque EPIC ouvert sous les yeux.
Elle ne tranche pas : le nombre de PR qu'un lot portera est un **jugement**, et deux dessins de garde
mécanique ont été mesurés puis écartés, faute de signal lisible dans de la prose.

## Pourquoi cette compétence existe

Le dépôt avait `ouvrir-une-issue` et `clore-une-issue`, et `clore-un-chantier` avec ses quatorze passes
détaillées. L'ouverture d'un chantier, elle, tenait en **une phrase**, dont seule la première étape
avait une compétence.

Cette asymétrie a un coût mesuré : le palier du sous-chantier n'avait aucune maison, et il s'est
d'abord retrouvé écrit dans `trier-les-issues`, c'est-à-dire dans la compétence qui se trouvait
ouverte plutôt que dans celle qui porte le geste.

## Ce que l'ouverture laisse derrière elle

Le corps de l'EPIC porte les lots, chacun disant ce qu'il livre et **comment on saura qu'il est
fini**. Un lot qui ne dit pas son fait-quand se clôt à l'estime.

Les lots devenus sous-chantiers y figurent par leur numéro, pas par une liste d'issues : le parent
reste lisible, et la clôture sait quoi clore.

Le chantier ouvert, [`ouvrir-une-issue`](../ouvrir-une-issue/SKILL.md) prend chaque issue une par une,
et la chaîne se poursuit jusqu'à [`clore-un-chantier`](../clore-un-chantier/SKILL.md).

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je découpe en issues, on verra la taille après » | La taille se demande avant. Après, le lot est déjà accroché |
| « Ce lot tiendra bien sous une case à cocher » | Combien de PR ? Plus de deux, il lui faut un sous-chantier |
| « Les autres EPIC font comme ça » | La forme observée enseigne l'erreur : #4511 porte sept lots et zéro sous-chantier |
| « Je cartographierai en écrivant le code » | Le plan aura alors inventé ce qui existait déjà |
| « Le triage, je le ferai si un doublon apparaît » | Il apparaîtra au conflit de fusion, quand deux chemins existent |

[ADR 4712]: https://companion-dev.echonuit.fr/decisions/4712-un-lot-multi-pr-est-un-sous-chantier/
