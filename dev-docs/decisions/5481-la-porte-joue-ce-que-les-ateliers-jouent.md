---
type: adr
title: "La porte joue ce que les ateliers jouent, et dit sous son verdict ce qu'elle ne joue pas"
status: stable
article: A3
chantier: "#5478 (le chantier qui fermait des faux verts en a produit un), lot #5481"
decided_at: 2026-09-25
verification: certaine
enforced_by:
  - "scripts/batterie.py"
verified:
  - by: machine:ci
    at: 2026-09-25
relations:
  complete: ["5157-le-dispositif-se-lit-sans-mode", "5340-chemins-non-declares"]
generated:
  by: "process:assistance-par-agents"
---

# La porte joue ce que les ateliers jouent, et dit sous son verdict ce qu'elle ne joue pas

## Le contexte

`scripts/batterie.py` répond à « quels contrôles ce diff engage-t-il ? » et rend une ligne de
verdict. Deux défauts la rendaient trompeuse, et ils sont de nature différente.

**Elle lançait chaque garde nu.** Les ateliers en lancent dix avec `--verifie`, et pour plusieurs
d'entre eux le mode sans argument **écrit** au lieu de juger. Mesuré sur une ADR dont l'article
change : `matrice-constitution.py --verifie` rend 1 quand le même garde lancé nu rend 0 et réécrit
`CONSTITUTION.md`. La porte rendait donc vert **en rendant le dépôt conforme**, au lieu de constater
qu'il l'était. Ce n'est pas un verdict affaibli, c'est un verdict d'une autre nature.

**Et sa ligne de verdict avait la forme d'une conclusion** - un compte, des refus, une phrase - alors
que ce qu'elle ne couvre pas était annoncé **au-dessus**, dans une section qui nomme des commandes
sans les lancer. Cinq fois en vingt-quatre heures, une porte verte a précédé une CI rouge.

## La décision

**Les arguments et les outils se dérivent des ateliers**, jamais d'une liste écrite dans la porte.
Une forme et une seule est rejouée, faite de drapeaux uniquement ; un garde que les ateliers lancent
tantôt nu, tantôt avec des arguments, reste lancé nu, parce que la porte ne choisit pas à la place de
l'atelier.

**Et ce que la porte ne joue pas se dit SOUS sa ligne de verdict.** La position est la décision : ce
qui est annoncé au-dessus d'un résumé n'est pas lu par qui descend jusqu'au résumé.

## Pourquoi dériver, et non énumérer

Une liste écrite dans la porte se périmerait au premier garde qui gagne un mode. L'atelier, lui, est
la référence : c'est lui qui décide du rouge que la porte existe pour anticiper.

**Le critère n'est pas « jouer ce que la CI joue ».** La CI lance aussi `revoque_jeton.py`, qui
révoque un jeton, et `installer_paquets.py`. Une règle indexée sur la ressemblance ferait révoquer un
jeton depuis un poste de développement. La porte ne reprend donc que les **arguments** de gardes
qu'elle joue déjà, et le reste se dit (#5525).

## La limite, et elle est nommée

**Une règle indexée sur la CI est muette là où la CI est muette**, ce que l'ADR 5157 a écrit en
rejetant une règle indexée sur la CI pour choisir le `dispositif` d'un garde. La raison vaut ici :
un garde qu'aucun atelier ne nomme garde un comportement par défaut que rien ne déclare.

Mesuré le 2026-09-25 : vingt-quatre gardes du corpus ne sont dans aucun atelier, et **aucun** d'eux
ne porte de mode `--verifie` ou `--ecrire`. Le trou est vide aujourd'hui, et rien ne le maintient
vide.

## Conséquences

- la porte cesse de modifier l'arbre de travail en le jugeant ;
- elle joue `ruff` sur les dossiers que `lint.yml` lui donne, 0,02 s à froid, ce qui retire une
  commande à lancer à la main de deux surfaces d'instruction ;
- un agent qui lit la dernière ligne d'une porte verte sait ce qui reste à lancer ;
- le premier garde hors atelier qui gagnera un mode sera lancé dans le mauvais, et cette ADR est
  l'endroit où le lecteur l'apprendra.
