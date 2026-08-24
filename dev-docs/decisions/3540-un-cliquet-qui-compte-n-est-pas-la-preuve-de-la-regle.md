---
type: adr
title: "Un cliquet qui compte n'est pas la preuve de la règle qu'il escorte"
status: stable
article: A2
chantier: "#3540, clôture du lot 3 du chantier #3536"
decided_at: 2026-08-14
verification: humaine
verification_note: "l'inventaire des 63 sites publié en commentaire de #3547 : un site, un"
verified:
  - by: human:nedseb
    at: 2026-08-14
relations:
  amende: ["3547"]
  prolonge: ["3624"]
---

# Un cliquet qui compte n'est pas la preuve de la règle qu'il escorte

## Contexte

L'[ADR 3547](3547-un-binding-declare-ce-qu-il-lit.md) a été écrite pendant le lot 3 et déclare :

> **Vérification** : certaine - `DeclarationDesBindingsTest#tout_nouveau_binding_est_vu`

Son propre texte dit pourtant l'inverse, deux paragraphes plus bas : vérifier statiquement
« lu ⊆ déclaré » a été **écarté**, et le cliquet « ne vérifie donc **aucune déclaration** ». Il compte
les sites. Un binding qui sous-déclare ses dépendances le laisse **vert**.

La ligne `Vérification` désigne donc un test qui ne peut pas rougir sur la décision qu'il est censé
tenir. C'est mot pour mot la forme que l'[ADR 3624](3624-un-fait-que-rien-ne-peut-faire-rougir-s-ancre-autrement.md)
nomme : *un fait vrai, une conséquence visible, et aucun dispositif capable de rougir dessus*.

La passe 0 de cette clôture l'a trouvé. Elle ne l'aurait pas trouvé sur la fenêtre que j'avais d'abord
prise : j'avais lancé son instrument depuis le début du **lot** (`0c2676393`) au lieu du commit
d'ouverture du **chantier** (`8052471f0`, tracé dans l'EPIC), ce qui ramenait **une** ADR au lieu de
**vingt-trois**. L'ADR 3624 était dans les vingt-deux manquantes.

## Décision

**Le cliquet et la règle s'ancrent séparément, et chacun dit ce qu'il tient.**

- La règle « un binding déclare tout ce que son calcul lit » s'ancre par un **regard consigné** - le
  second des deux dispositifs que l'ADR 3624 autorise. Son support est l'inventaire des 63 sites :
  daté, attaché à un commit nommé, un verdict par ligne. Il se **relit** ; il ne se rejoue pas.
- Le cliquet `DeclarationDesBindingsTest` tient une promesse **plus faible et vérifiable** : aucun
  nouveau site n'entre sans être vu. C'est lui qui rougit, et il rougit sur le **compte**, jamais sur
  une déclaration.

Écrire `certaine` en désignant le cliquet revenait à emprunter la solidité de l'un pour couvrir
l'autre. Les deux sont utiles ; ils ne prouvent pas la même chose.

## Ce que 3547 aurait dû citer

L'[ADR 3575](3575-le-journal-fait-exception-et-le-cliquet-ne-recopie-rien.md) avait déjà tranché la
forme du garde, deux jours plus tôt et dans un chantier voisin : **« le cliquet est un compteur, pas une
liste »**, éprouvé « en ajoutant une commande **factice** au câblage, et non en relisant le code ».

Le cliquet du lot 3 est exactement cela, et a été éprouvé exactement ainsi - mais l'ADR 3547 le présente
comme une idée neuve appuyée sur `cli-surface.bats`. Elle a suivi une décision existante **sans la
citer**, ce qui laisse croire à deux idiomes parallèles là où il n'y en a qu'un.

## Conséquences

- La ligne `Vérification` d'une ADR nomme ce qui **rougit sur sa décision**, pas le dispositif le plus
  proche. Quand rien ne peut rougir, `humaine` est la réponse juste, et l'ADR 3624 impose de dire
  **lequel** des deux ancrages on choisit.
- La passe 0 se lance depuis le commit d'ouverture du **chantier**. Sur une clôture de lot, la fenêtre
  courte se lit comme suffisante et ne l'est pas : elle rate les décisions écrites pendant les lots
  précédents, qui sont précisément celles que le lot en cours n'a pas vu passer.

## Ce que nous avons écarté

**Réécrire la ligne de l'ADR 3547.** Les ADR sont immuables une fois acceptées ; le dépôt amende par une
nouvelle ADR ([3575](3575-le-journal-fait-exception-et-le-cliquet-ne-recopie-rien.md) amende
[3498](3498-la-declaration-porte-sur-les-lectrices.md),
[3624](3624-un-fait-que-rien-ne-peut-faire-rougir-s-ancre-autrement.md) amende 3482). La tentation était
réelle : l'ADR 3547 a moins d'un jour, et corriger une ligne coûtait moins que cette page. C'est
justement quand l'erreur est fraîche que l'exception paraît raisonnable.

**Renforcer le cliquet pour qu'il mérite `certaine`.** Il faudrait suivre les appels depuis la lambda.
L'ADR 3547 l'a écarté avec ses raisons, qui tiennent toujours.
