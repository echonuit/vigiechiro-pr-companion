---
type: adr
title: "Le zéro des traces d'outil se garde, parce qu'il se retourne d'un seul collage"
status: stable
article: A31
chantier: "#4783 (lot 3 du chantier #4748)"
decided_at: 2026-08-29
verification: probable
enforced_by:
  - "scripts/adr/4783-traces-d-outil.py"
  - ".github/scripts/verifie_corps_pr.py"
ratchet: 0
inv_key: cliquet-traces-outil
verified:
  - by: machine:suspects
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Le zéro des traces d'outil se garde, parce qu'il se retourne d'un seul collage

## Contexte

La grille de la compétence `humaniser` porte six **traces d'outil** depuis #4776 : jetons de
citation
d'assistant, paramètres de suivi accrochés aux liens, caractères invisibles, homoglyphes, gabarit
non
rempli, raisonnement laissé dans le texte. Cinq se comptent par une expression ; la sixième demande
une lecture.

Ces cinq-là sont les seuls motifs du dépôt dont **une occurrence conclut**. Partout ailleurs la
grille demande un groupe.

Le relevé du 2026-08-29, sur 2 726 fichiers et 372 370 lignes, rend **zéro** pour les cinq. Les
quatre
caractères invisibles trouvés sont trois séquences d'emoji et une constante `BOM`.

## Le défaut

Rien ne compte ces cinq familles. La grille les nomme, et seul un agent qui l'ouvre les cherchera.

Le dépôt porte deux précédents contraires, tous deux dans `dev-docs/registre-editorial.md`. Le zéro
des connecteurs lourds **est gardé**, parce qu'il « se retournerait en silence ». Onze occurrences
réparties sur six familles **n'ont pas** justifié de règle, une règle opposable pour onze cas
coûtant
plus qu'elle ne rapporte.

## Décision

**Le zéro des cinq traces comptables est gardé, à tolérance zéro.**

C'est le précédent des connecteurs qui s'applique, et ce qui les sépare des onze occurrences est la
**forme de l'arrivée**. Onze occurrences sont une dette étalée, qu'une relecture rattrape et qu'un
cliquet fait descendre. Une trace d'outil arrive d'un seul collage, en une fois, et elle est quasi
probante : sa présence dit que le texte n'a pas été relu. Un cliquet sert à faire descendre une
dette, et il n'y en a pas ici : le garde tient un zéro.

## Trois exemptions, nominatives

**Les deux exemplaires de `humaniser/SKILL.md`.** La grille énumère les chaînes qu'elle cherche. La
mesure le prouve : sans l'exemption, le garde rend 22 marques de citation, toutes aux lignes de
`T1`.
C'est l'[ADR 3645](3645-un-detecteur-textuel-s-exclut-de-son-corpus.md), et pour une fois elle a été
appliquée avant plutôt qu'après un rouge.

**Le module qui porte la définition**, `scripts/_commun/traces.py`, qui nomme les mêmes chaînes.
C'était le garde lui-même jusqu'à #4749.

**Le signe cité plutôt qu'employé**, au grain de la ligne : entre accents graves, ou seul contenu
d'une chaîne littérale. `private static final char BOM` décrit la marque d'ordre, il ne la pose pas.
Et le liant `U+200D` qui suit un pictogramme compose une séquence d'emoji au lieu de traîner.

## Alternatives écartées

**Une loupe qui ne bloque pas.** Le dépôt en comptait cinq le 2026-08-29 et elles servent, mais
elles servent à mesurer une dette qu'on fait descendre. Le zéro n'en est pas une, et une loupe laisserait passer
l'occurrence qui le retourne.

**Ne rien écrire.** C'est le précédent des onze occurrences, et il ne s'applique pas : il vise le
coût d'une règle qui refuse souvent, quand celle-ci ne refusera jamais tant que personne ne colle.

**Attendre la première occurrence.** Elle serait déjà fusionnée, et le propre de ces traces est de
ne pas se voir à la relecture.

## Conséquences

Un garde de plus dans la boucle des cliquets, deux secondes par exécution.

Les trois exemptions se maintiennent : chacune peut devenir fausse, et c'est le prix admis. Celle
des
deux copies de la compétence disparaîtrait si la grille cessait d'énumérer ses chaînes, ce qui la
rendrait moins utile.

## Le corps d'une demande, depuis #4749

Le garde lit les fichiers suivis, et le corps d'une demande de fusion n'en est pas un. Mesuré le
2026-10-07 : `verifie_corps_pr.py` déclarait conforme un corps qui portait trois de ces traces.

Il les refuse depuis #4749, sur la même définition. Elle est sortie du garde pour vivre dans
`scripts/_commun/traces.py`, que les deux lisent : une seconde liste aurait divergé de la première.
Ce n'est pas une décision neuve. L'[ADR 4453](4453-la-prose-publiee-sur-la-forge-releve-de-la-meme-grille.md)
range déjà ce corps dans la prose que la grille couvre, et celle-ci tient le zéro : il manquait de
lire cette population.

Une exemption s'y ajoute, propre au corps : l'espace sans chasse que Dependabot place derrière une
arobase, pour ne pas notifier les auteurs des notes de version qu'il recopie. Sur les 300 dernières
demandes fusionnées, le caractère apparaît 68 fois et toujours à cette place.
