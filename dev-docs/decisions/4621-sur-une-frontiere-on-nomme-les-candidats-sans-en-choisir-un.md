---
type: adr
title: "Sur une frontière, on nomme les carrés candidats sans en choisir un"
status: stable
article: A17
chantier: "#4621 (le carroyage répond dans les deux sens, chantier #4573)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "PropositionCarreTest#frontiere_nomme_sans_choisir"
  - "CarroyageNationalTest#milieu_d_un_cote_rend_deux_candidats"
  - "ModaleSiteViewTest#situer_sur_une_frontiere_ne_remplit_rien"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Sur une frontière, on nomme les carrés candidats sans en choisir un

## Contexte

Le carré d'une position est la maille de centroïde le plus proche. Sur une frontière, « le plus
proche » ne désigne rien.

Mesuré le 2026-08-27 contre le serveur réel, autour du carré `040110` :

| Position | Ce que la grille rend |
|---|---|
| Milieu d'un côté commun | 2 mailles, à **997,7 m chacune** |
| Coin commun à quatre carrés | 4 mailles, à **1 412 m chacune** |
| Le milieu, décalé de 5 m | le premier élément **bascule** |

L'égalité est stricte, et MongoDB ne garantit aucun ordre entre distances égales. La bande concernée
n'est pas la ligne : cinq mètres suffisent, soit l'ordre de grandeur de ce qu'on vise en cliquant sur
une carte.

## Décision

**Quand plusieurs carrés sont à moins de 50 m d'écart, l'application les nomme et n'en dépose aucun.**
L'observateur tranche.

Le seuil se dérive de la géométrie plutôt que de se choisir : pour un point à `x` mètres d'un bord,
l'écart des distances vaut environ `2x`. Cinquante mètres désignent donc les points à moins de 25 m
d'une frontière.

Les candidats se rendent **rangés par numéro**, et non par distance : l'ordre ne doit pas suggérer la
préférence que la phrase refuse d'avoir.

## Pourquoi ce n'est pas un calcul à corriger

Pour un point exactement sur une frontière, « le carré qui le contient » n'existe pas sans une
convention, du type intervalle semi-ouvert où le carré de gauche possède son bord. La grille ne stocke
que des **centres** : sa représentation ne peut pas porter cette convention, et aucune question posée à
la plateforme ne rendra la bonne réponse, parce qu'il n'y en a pas une.

C'est un fait du domaine. Le produit doit le dire plutôt que le masquer.

## Ce que l'inverse coûterait

Choisir le plus proche et se taire, c'est proposer un carré sur deux au hasard le long de chaque bord.
Un numéro faux et **plausible** est le défaut que tout ce chantier cherche à éviter : il contaminerait
ensuite le préfixe `Car######` de tous les fichiers de la nuit, et ne se verrait qu'au dépôt.

Ne rien proposer près d'un bord serait l'autre excès. Deux candidats valent mieux que rien : ils
épargnent le détour par le portail, qui est la corvée que ce chantier supprime.

## Comment on saurait qu'elle est rompue

`PropositionCarreTest#frontiere_nomme_sans_choisir` exige qu'aucun numéro ne soit proposé, et que les
deux soient nommés dans l'ordre des numéros.
`ModaleSiteViewTest#situer_sur_une_frontiere_ne_remplit_rien` exige que le champ des six chiffres garde
ce qu'il avait.

Elles rougissent si quelqu'un « répare » le geste en prenant le plus proche.
