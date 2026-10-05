## Context

Le motif d'une séquence absente se décide en un seul point : `RebranchementSequences.rebrancher`, quand
aucun fichier du dossier ne porte son nom. Il dépend de l'origine des candidats (`OrigineCandidats`) :
`DOSSIER` pour un dossier désigné par l'utilisateur, `REGENERATION` pour des tranches que nous venons
de fabriquer. Chaque absence passe par `BilanReactivation.absenter`, qui l'ajoute à `manquantes` et à
la liste `absences`.

Deux comptes rendus lisent le même `RapportReactivation`. La modale affiche le **chiffré**,
`CompteRenduChiffreReactivation` (#2358) : une barre qui ventile les séquences du passage, des motifs
groupés par cause, des mentions, et une sévérité qui vaut déjà avertissement dès que la nuit est
incomplète, erreur seulement si un fichier diverge. Le **textuel**, `CompteRenduReactivation`, reste
la surface du terminal et de l'annulation ; sa sévérité est le maximum de celles de ses constats, et
celui des introuvables est en erreur. Les deux partagent le titre, le préambule et la conclusion.
La commande `reactiver` projette en plus le rapport en JSON, clé par clé (`Reactiver.projeter`).

La voie se choisit une fois par dossier, les séquences l'emportant sur les bruts. Une seconde
réactivation pointée sur les enregistrements bruts saute les originaux dont toutes les tranches sont
présentes (#1962) et ne redécoupe que les autres : le conseil du constat est donc suivable tel quel.

## Goals / Non-Goals

**Goals:**

- Une règle de reconnaissance écrite une fois, au même endroit que l'arbitrage qui produit les
  perdants.
- Un compte des perdants qui traverse le rapport jusqu'aux deux surfaces sans qu'aucune le recalcule.

**Non-Goals:**

- Rien ne change à ce qui est rebranché, ni à l'ordre des voies.
- L'origine `REGENERATION` n'est pas concernée : la voie des bruts rejoue l'arbitrage et régénère les
  perdants.

## Decisions

**La reconnaissance vit dans `NommageSequences`.** C'est la classe qui arbitre les collisions et
décide qu'un perdant passe en `_001` : la règle inverse, « ce nom est un perdant », s'écrit à côté de
celle qui le produit. Elle exige un horodatage (`Prefixe.horodatageDe`) **et** un suffixe d'au moins
`_001`. *Écarté* : tester le seul suffixe, qui prendrait pour perdant la deuxième tranche d'un nom
sans horodatage, où `_001` est un index (repli de `Prefixe.nommerSequence`). *Écarté aussi* : vérifier
en base qu'un `_000` du même nom existe. La preuve serait plus forte, mais elle coûte une requête par
absence pour un cas que le nommage garantit déjà.

**Un compte à part dans le bilan, inclus dans `manquantes`.** `BilanReactivation` gagne la
liste `perdants`, dont la taille fait le compte, et chaque perdant incrémente aussi `manquantes`. Il
n'entre pas dans `absences` : les introuvables comptent `manquantes` moins les perdants, et ne
détaillent que les autres absences.
*Écarté* : sortir les perdants de `manquantes`. La clé JSON du même nom changerait de sens sans que
rien ne rougisse chez un script qui la lit (décision du porteur, 1er octobre).

**Les deux comptes rendus présentent les perdants, chacun dans sa forme.** Le textuel ajoute un
constat en `Severite.AVERTISSEMENT`, à côté de celui des introuvables. Le chiffré ajoute un segment
de barre, distinct de « Manquantes », un motif qui liste leurs noms, et la phrase du constat parmi ses
mentions ; sa sévérité ne change pas. La phrase est écrite une fois et lue par les deux, comme le
titre et la conclusion. *Écarté* : laisser les perdants dans le segment « Manquantes » de la barre,
qui dirait une proportion fausse de ce qui reste à chercher. *Écarté aussi* : ne traiter que le
terminal, alors que la modale est l'écran que l'utilisateur voit.

**La phrase est conditionnelle, et la même pour toutes les nuits.** *Écarté* : un libellé qui s'adapte au statut du passage. `DEPOSE` dit « déposée par nous » et `RECUPERE`
« rapatriée de Vigie-Chiro », mais une nuit déposée avec Kaleidoscope puis réimportée depuis la carte
ne porte ni l'un ni l'autre : le libellé se tromperait en silence sur le cas même qui a motivé le
changement.

**Les noms des perdants se montrent**, en détails du constat dans le terminal et en motif dans la
modale, comme les autres absences : la surface décide d'en plafonner l'affichage (ADR 0031). Le
nombre et la cause sont dans la phrase du constat, donc lisibles même quand la liste est tronquée. Les
noms servent à qui vérifie, et c'est ce qu'on a dû demander à Samuel pour #5604. *Écarté* : un motif
par séquence dans la liste des absences (option B de l'instruction), qui laissait le titre du constat
dire « introuvables » et noyait les perdants parmi les vraies absences.

## Risks / Trade-offs

- [Un dossier produit par notre application, mais incomplet, peut lui aussi manquer un `_001`] → le
  constat dit alors « découpé par un autre outil », ce qui est inexact pour ce dossier. Le geste
  conseillé reste juste, et le cas est rare : un dossier de notre application contient ses `_001`.
- [Le libellé conditionnel est plus long qu'un constat ordinaire] → il porte trois informations dont
  aucune ne se déduit des autres : la cause, le geste, l'écart possible avec Vigie-Chiro.

## Migration Plan

Aucune donnée ne change. La clé JSON est additive.
