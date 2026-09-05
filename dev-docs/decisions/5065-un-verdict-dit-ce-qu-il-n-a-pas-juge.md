---
type: adr
title: "Un verdict dit ce qu'il n'a pas jugé, et sur quoi il s'est replié"
status: stable
article: A12
heuristiques:
  - "nielsen-1"
chantier: "#5065"
decided_at: 2026-09-05
verification: certaine
enforced_by:
  - "ServiceQualificationTest#sans_coordonnees_la_couverture_dit_sa_fenetre"
  - "FenetreDeCouvertureTest#sans_point_la_fenetre_se_replie"
  - "FenetreDeCouvertureTest#la_nuit_polaire_se_replie"
verified:
  - by: machine:ci
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-05
---

# Un verdict dit ce qu'il n'a pas jugé, et sur quoi il s'est replié

## Contexte

Deux défauts du même chantier, trouvés à deux moments et par deux chemins, disent la même chose.

**Le feu vert par ignorance.** Quand la fenêtre de référence était illisible, le pré-check rendait une
couverture neutre, donc verte, et son explication disait « Couverture horaire : **conforme** (couverture
non mesurable en détail) ». Rien n'avait été jugé. Le mot « conforme » se lit pourtant comme un
acquittement, et c'est le seul mot que l'observateur retient.

**Le repli muet.** La qualification mesurait la couverture contre les heures déclarées du passage,
quand le diagnostic la mesurait contre le coucher et le lever réels. Deux écrans répondaient
différemment à la même question, sans que rien n'indique lequel croire.

Le second a été corrigé en branchant les deux surfaces sur la même source. Restait le cas où cette
source manque, faute de coordonnées : trois comportements se défendaient, et il a fallu choisir.

## Décision

**Un verdict rendu à un humain distingue trois états, jamais deux** : ce qu'il a jugé conforme, ce
qu'il a jugé non conforme, et ce qu'il n'a **pas pu** juger. Le troisième ne se replie pas sur le
premier.

**Et quand il statue sur une source de second choix, il la nomme.** Sans coordonnées, la couverture se
mesure encore sur les heures déclarées, et l'explication le dit : « Couverture horaire (fenêtre
déclarée, faute de coordonnées) ». Elle ne se tait pas, et elle ne prétend pas non plus mesurer ce que
l'autre écran mesure.

## Ce qui a été écarté, et pourquoi

**Rendre le feu indisponible sans coordonnées**, comme le fait le diagnostic. Cohérent, et c'est ce qui
plaidait pour. Écarté parce qu'**un feu qui disparaît est un feu qu'on cesse de regarder** : l'écran
perdrait une information qu'il a, pour éviter de dire laquelle.

**Exiger les coordonnées.** Un pré-check consultatif qui refuse de se prononcer transforme une aide en
obstacle, sur des fiches de site que l'observateur ne maîtrise pas toujours.

## La sœur de cette décision

L'[ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md) tient la même règle pour les
**dispositifs de vérification** : un garde qui n'a rien vérifié le dit plutôt que de rendre vert.
Celle-ci la porte du côté du **produit** : ce que le verdict raconte à l'humain qui le lit.

Les deux ont la même cause, et c'est ce qui rend la règle générale : un vert obtenu par absence de
jugement est indiscernable d'un vert mérité, pour une machine comme pour une personne.

## Comment on le vérifie

Trois cas tiennent les deux replis, et la mutation les a rendus nécessaires : avant eux, les deux
chemins de repli de `FenetreDeCouverture` étaient en `NO_COVERAGE`. Le comportement arbitré n'était
éprouvé par rien, et douze mutations sur douze sont désormais tuées.
