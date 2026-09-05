---
type: adr
title: "Un banc de mutation compte trois verdicts, parce qu'un rouge par plantage ne prouve rien"
status: stable
article: A2
chantier: "#5257 (chantier #5215)"
decided_at: 2026-09-05
verification: certaine
enforced_by:
  - ".github/scripts/temoins_de_ci_non_decoratifs.py"
verified:
  - by: machine:ci
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-05
---

# Un banc de mutation compte trois verdicts, parce qu'un rouge par plantage ne prouve rien

## Contexte

L'[ADR 4490](4490-un-temoin-se-prouve-par-mutation-mecaniquement.md) rend la mutation
mécanique : on neutralise les fonctions de détection d'un garde, on relance son auto-test, et on
**exige qu'il rougisse**. Tolérance zéro.

La neutralisation remplace chaque fonction du module par un `lambda` rendant `[]`. Elle a été écrite
pour des gardes qui rendent des listes de suspects, et elle leur retire leur détection proprement.

Le chantier #5215 a converti cinquante gardes en Python. Leurs fonctions rendent des tuples, des
entiers, des chemins, des booléens. Sur ceux-là, `[]` ne retire pas la détection : il fait **planter**
l'auto-test avant sa première assertion.

## La mesure

Sur les 43 gardes de `.github/`, classés par ce que leur rouge VAUT :

| ce qu'on observe | combien | ce que cela prouve |
|---|---:|---|
| rouge, sans trace Python | 34 | l'auto-test a vu la détection disparaître |
| rouge, AVEC une trace Python | 9 | rien : le garde est mort avant d'assertir |
| vert | 0 | l'auto-test est décoratif |

Les neuf se rangent en trois familles, et aucune n'est un défaut du garde : un dépaquetage
`a, b = f()` sur un `[]`, une `list` employée comme clé de dictionnaire, et une fonction dont le
travail était de CRÉER la fixture que l'auto-test ouvre ensuite.

Sur les 22 gardes de `scripts/methode`, la même mesure rend **16 tenants et 6 non concluants**, quand
leur banc annonce « les 22 gardes éprouvés rougissent sous mutation ».

## Décision

**Un banc de mutation rend trois comptes, et leur somme vaut sa population.** Il tient, il ne conclut
pas, ou il est décoratif.

**Il ne refuse que sur un décoratif.** Un non concluant se compte et se **nomme avec sa cause** :
refuser dessus reviendrait à refuser sur ce que le banc n'a pas su lire, ce que l'article A3
interdit. Le compter comme une réussite reviendrait à annoncer 43 gardes éprouvés quand il y en a 34.

**Aucune valeur de repli ne remplace les trois familles.** En chercher une reviendrait à deviner ce
que chaque fonction rend au lieu de le demander, ce que l'[ADR 5102](5102-une-capacite-se-demande-jamais-se-reconnait.md) refuse.

## Ce que cette ADR corrige dans la 4490

**« Exige qu'il rougisse » ne suffit pas.** C'est la même forme que
l'[ADR 4918](4918-un-cas-rouge-pour-la-mauvaise-raison-ne-prouve-rien.md), un cas rouge pour la
mauvaise raison, appliquée au banc plutôt qu'au cas.

**Et un faux positif n'est PAS bruyant.** La 4490 écrit : « si la neutralisation cessait de
fonctionner, le script crierait « témoin décoratif » à tort. Un faux positif est bruyant ; c'est le
silence qu'il fallait éviter. » Mesuré à l'inverse : le banc de #5254 a produit exactement ce faux
positif sur LUI-MÊME, et il ne s'est rien passé de bruyant. Un verdict « décoratif » se lit comme une
trouvaille, pas comme une panne d'outil, et c'est ce qui le rend dangereux.

## Conséquences

**Ce qu'on gagne.** Le compte cesse de flatter. Un banc qui annonce 43 quand il prouve 34 rassure à
tort, et c'est la forme de silence que ce dépôt refuse.

**Ce qu'on paie.** Trois comptes se lisent moins vite qu'un. Et le compte des non concluants ne
descend pas tout seul : le faire baisser demande de changer ce que les fonctions des gardes RENDENT,
ce qui est un autre travail.

**Ce qui reste ouvert.** Les deux bancs de `scripts/` n'ont pas encore ce verdict, et #5265 le porte.
Faut-il un cliquet sur le compte des non concluants ? Il descendrait à mesure qu'on rend les gardes
mutables, mais il ferait rougir un dépôt dont les gardes sont sains. La question se décide sur une
mesure, pas ici.
