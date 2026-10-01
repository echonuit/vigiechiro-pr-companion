---
type: adr
title: "Un compte subi se tient par un cliquet, et sa population se dérive plutôt qu'elle ne s'énumère"
status: stable
article: A9
chantier: "#5570 (89 harnais lus en septembre, 93 trois semaines plus tard), lot de l'EPIC #5584"
decided_at: 2026-10-01
verification: probable
enforced_by:
  - "scripts/methode/releve-les-harnais-muets.py"
ratchet: 81
inv_key: cliquet-harnais-muets
verified:
  - by: machine:suspects
    at: 2026-10-01
relations:
  complete: ["4918-un-cas-rouge-pour-la-mauvaise-raison-ne-prouve-rien"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-01
---

# Un compte subi se tient par un cliquet, et sa population se dérive plutôt qu'elle ne s'énumère

## Contexte

L'[ADR 4918](4918-un-cas-rouge-pour-la-mauvaise-raison-ne-prouve-rien.md) veut qu'un auto-test nomme
le contrôle qui a rougi. Le chantier #5530 a livré l'instrument qui mesure le reste du dépôt, et il
s'est délibérément arrêté là : convertir un harnais est un travail par garde.

L'instrument comptait donc, et ne jugeait pas. Son propre contrat portait l'argument :

> Pourquoi `rapport` : il COMPTE, il ne juge pas. Convertir un harnais est un travail par garde, et
> refuser dessus reviendrait à bloquer le dépôt sur une dette que ce relevé sert à rendre visible.

**Cet argument est celui d'un butoir, et il est faux d'un cliquet.** Un butoir à zéro bloquerait sur
la dette existante ; un cliquet posé à la valeur mesurée n'exige rien des harnais déjà muets, et
exige tout des suivants. C'est la distinction que l'article A9 tient par ailleurs sur quarante-trois
cliquets, et que cette phrase effaçait.

Le coût de ne pas juger se mesure. Le relevé rendait `lus=89 | muets=74` en septembre, et
`lus=93 | muets=75` trois semaines plus tard. Personne n'a décidé cette hausse : elle a dérivé,
garde neuf après garde neuf.

## Décision

**Le relevé devient un cliquet**, à la valeur mesurée dans un run vert. Un harnais muet de plus fait
rougir `lint.yml` ; un de moins fait dire que la marge est à resserrer. La phrase du contrat qui
argumentait contre est **remplacée**, non laissée à côté : deux arguments opposés dans le même dépôt
font appliquer celui qu'on trouve en premier.

**Et sa population se dérive.** Le balayage énumérait cinq dossiers et les lisait par un
`glob("*.py")` non récursif, donc sans leurs sous-dossiers. Mesure du 2026-10-01 : huit gardes dont
la porte joue l'auto-test étaient hors population, et six d'entre eux muets. Le compte passe de
`muets=75` à `muets=81`.

Cette seconde moitié n'est pas un détail d'exécution. **Un cliquet posé sur la population d'avant
aurait verrouillé le mauvais chiffre** : le jour où quelqu'un aurait nommé les quatre dossiers
manquants, il aurait rougi pour une raison qui n'est pas la sienne, non qu'un harnais muet ait été
ajouté, mais que la mesure ait commencé à dire vrai.

## Conséquences

Un garde neuf dont l'auto-test passe ses cas à son aide **en valeur** fait désormais rougir la CI.
La capacité de s'y conformer existe depuis #5444 : `cas_d_auto_test` accepte un appelable.

La **conversion** des 81 harnais reste à faire, et elle ne relève pas de cette décision. Les deux
familles ne se réparent pas de la même façon : 46 sites posent leur marque d'échec en ligne et n'ont
aucune aide à qui différer quoi que ce soit, 29 en ont une et lui passent des cas en valeur. Elle part
en chantier propre, et le critère du quoi convertir y sera tranché.

**Le compte reste un minorant**, et pour une seule raison désormais : le relevé ne voit pas un harnais
dont l'aide vit dans un autre module sous un autre nom que `verifie`. La seconde raison, les
sous-dossiers, était réparable, et le contrat ne la déclare plus comme une limite.

**Ce que le cliquet ne dit pas.** Il compte des harnais incapables de nommer leur cas ; il ne dit rien
de la qualité de ce qu'ils vérifient. Une session pair a mesuré que sur 322 sites comptés, 321 sont
bien des appels à une aide et un seul est la fonction sous test elle-même : l'instrument est sain à
cette unité près, et elle est connue.
