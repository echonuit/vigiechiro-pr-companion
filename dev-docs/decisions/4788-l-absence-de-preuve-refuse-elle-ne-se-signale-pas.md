---
type: adr
title: "Un dispositif qui ne peut pas prouver refuse, il ne se contente pas de le signaler"
status: stable
article: A2
chantier: "#4788 (EPIC #4803)"
decided_at: 2026-08-30
amende: "4770"
verification: certaine
enforced_by:
  - "scripts/methode/temoins-de-methode-non-decoratifs.py"
verified:
  - by: machine:ci
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un dispositif qui ne peut pas prouver refuse, il ne se contente pas de le signaler

## Contexte

L'ADR 4770 a rendu l'article A2 mécanique pour `scripts/methode/` : chaque garde est neutralisé, et
son auto-test doit rougir. Six gardes exécutaient leur corps au niveau du module, sans point d'entrée
où insérer la neutralisation. Ils étaient **nommés à chaque passage** plutôt que passés en silence,
ce qui paraissait honnête.

## Le défaut, mesuré et non supposé

Le garde les nommait en **sortant 0**. Six gardes sur quinze, soit 40 % du corpus, n'avaient donc
aucune preuve au titre de l'article A2, la CI était verte, et la ligne qui le disait passait dans un
journal que personne ne relit ligne à ligne.

Le mot employé aggravait la chose. L'ADR 4770 présentait le maintien de cet état comme « vivre avec
un refus déclaré », et l'issue #4788 a repris la formule. Il n'y avait pas de refus : une déclaration
qui ne coûte rien n'en est pas un. La formulation transformait un choix entre deux coûts en choix
entre un coût et rien, et c'est en la vérifiant qu'elle s'est révélée fausse.

## Décision

Un dispositif qui ne peut pas conclure sur une partie de son corpus **refuse**, et son message dit le
remède plutôt que de renvoyer à une issue. Un signalement qui n'a pas de coût n'est pas lu, et la
liste ne se vide pas.

Les six ont reçu un point d'entrée, et leur comportement n'a pas changé : codes de sortie et sorties
comparés octet à octet, sur trois invocations chacun, zéro écart sur dix-huit.

## Pourquoi les deux gestes et non le premier seul

Poser les points d'entrée sans faire refuser aurait nettoyé la liste sans empêcher qu'elle se
remplisse : le septième garde écrit plus tard y serait revenu, toujours en vert. C'est le refus qui
tient la convention dans le temps, pas la correction ponctuelle.

## Ce que cela coûte, et qui est assumé

Une contrainte de forme imposée à tout garde de méthode futur, pour le seul besoin du harnais qui le
vérifie. Le dépôt exige déjà un `--auto-test` de chaque garde ; un point d'entrée est une cérémonie
plus petite, et elle sert la même fin.
