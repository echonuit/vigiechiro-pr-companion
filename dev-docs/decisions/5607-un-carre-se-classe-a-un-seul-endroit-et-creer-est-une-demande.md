---
type: adr
title: "La présence d'un carré se classe à un seul endroit, et « Créer » vérifie ce qui ne l'a pas été"
status: stable
article: A15
heuristiques:
  - "nielsen-1"
  - "nielsen-5"
chantier: "#5607, lot 13 du chantier #5596"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/sites/model/PresenceDuCarreTest.java"
  - "src/test/java/fr/univ_amu/iut/sites/view/ModaleSiteCreerSansVerifierTest.java"
verification_note: "la première classe tient le classement en trois cas ; la seconde tient que « Créer » interroge le portail quand aucun verdict n est affiché. Ni l une ni l autre ne joue le portail réel, et l indisponibilité y est un bouchon"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# La présence d'un carré se classe à un seul endroit, et « Créer » vérifie ce qui ne l'a pas été
## Contexte

Samuel a déclaré le carré 202013, que Vigie-Chiro ne porte pas en Point Fixe. Il ne l'a appris qu'au
dépôt, sa nuit déjà importée et transformée (2.193.0). La vérification, le rapatriement et `creer-site`
classaient chacun à sa façon ce que la recherche d'un carré renvoie ; seul le rapatriement distinguait
le Point Fixe des autres protocoles. Un carré présent seulement en Routier était annoncé « existe
déjà, récupérez-le », pour aboutir à « rien n'a été récupéré ».

Le porteur a tranché le 30 septembre : on continue de renvoyer au portail pour activer un carré, mais
on le dit **au moment de la déclaration**.

## Décision

**1. Un seul classement**, `PresenceDuCarre`, fonction pure de `sites/model` : Point Fixe présent,
autre protocole seulement, absent. L'indisponibilité du portail n'en fait pas partie : elle vient de
la réponse, pas de la liste.

**2. Le verdict dit ce qu'il implique.** Pour un carré absent ou présent sous un autre protocole, les
trois gestes disent la même phrase sur le portail : il faudra l'activer en Point Fixe, puis le
récupérer ici. « Absent » passe de succès à avertissement.

**3. « Créer » est une demande.** Sans verdict affiché, la modale interroge le portail avant
d'enregistrer. Elle ne refuse pas pour autant de déclarer un carré absent : l'observateur peut
préparer son site avant de l'activer.

## Ce qui a été écarté

**Un message unique pour les trois gestes.** Vérifier, créer et récupérer ne disent pas la même
chose : seule la phrase sur le portail est commune.

**Corriger les seuls messages.** La règle serait restée écrite trois fois, et c'est ainsi que l'écart
est né.

## Conséquences

« Récupérer ce carré » ne s'offre plus que sur un carré en Point Fixe. Un quatrième appelant lit
`PresenceDuCarre`, il ne reclasse pas.
