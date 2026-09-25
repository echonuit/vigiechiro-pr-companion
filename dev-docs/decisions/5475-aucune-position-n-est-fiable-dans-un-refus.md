---
type: adr
title: "Aucune position n'est fiable dans le refus d'un garde, donc on en montre plus"
status: stable
article: A3
chantier: "#5478 (le chantier qui fermait des faux verts en a produit un), lot #5475"
decided_at: 2026-09-07
verification: certaine
enforced_by:
  - "scripts/batterie.py"
verified:
  - by: machine:ci
    at: 2026-09-07
relations:
  complete: ["5398-une-exemption-se-declare-elle-ne-s-infere-pas"]
generated:
  by: "process:assistance-par-agents"
---

# Aucune position n'est fiable dans le refus d'un garde, donc on en montre plus

## Le contexte

`scripts/batterie.py` récapitule les refus qu'elle a recueillis. Elle en citait **une** ligne, et le
choix de laquelle est le sujet de cette décision.

La première écriture prenait la première ligne non vide. Sur les trois refus réels d'une batterie,
mesurés le 2026-09-07, deux commencent par la **cause** et le troisième par un **titre** suivi de
deux points. Prendre la dernière ligne échoue symétriquement.

Et le cas qui a ouvert l'issue est pire : le geste de `4617-code-mort-et-zone-de-test.py` est sur sa
**troisième** ligne, `Lancez d abord : ./mvnw -B -o test-compile pmd:pmd`. Un lecteur voyait donc un
refus dont la conduite à tenir était invisible.

## La décision

**On montre plus au lieu de deviner : les deux premières lignes non vides**, chacune bornée
séparément.

Ce n'est pas un compromis sur le nombre, c'est le refus d'une inférence. Deviner la position de la
cause ou du geste reviendrait à inférer une **structure** que les gardes ne partagent pas, et l'ADR
5398 vient précisément de refuser qu'une exemption s'infère de la forme d'une sortie.

Borner **chaque ligne** plutôt que la chaîne jointe est la seconde moitié : une troncature appliquée
à la concaténation supprime la seconde ligne dès que la première est longue, c'est-à-dire exactement
ce qu'on venait de rendre visible.

## Ce qu'elle coûte, et ce qu'elle ne résout pas

Deux lignes ne montrent pas un geste posé sur la troisième, et le cas de `4617` reste donc à moitié
traité. La décision l'assume : elle rend la règle **honnête** plutôt que juste, et le remède complet
demande que les gardes refusent par une forme commune, ce qui est un autre travail (#5485).

Ce que cette ADR ferme, c'est la tentation d'ajouter une règle de position de plus à chaque refus
rencontré.

## Conséquences

- un garde neuf n'a rien à déclarer pour que son refus soit lisible ;
- un refus dont la cause et le geste tiennent en deux lignes est intégralement montré ;
- le jour où #5485 donnera une forme commune aux refus, cette ADR sera amendée : la position
  redeviendra fiable, et montrer plus cessera d'être nécessaire.
