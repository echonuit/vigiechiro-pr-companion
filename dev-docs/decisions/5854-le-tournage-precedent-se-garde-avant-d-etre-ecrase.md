---
type: adr
title: "Le tournage précédent de la plateforme de test se garde avant d'être écrasé"
status: stable
article: A5
chantier: "#5854, lot 8 du sous-chantier #5644"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - ".github/scripts/verifie_decisions_du_tournage_connecte.py"
verification_note: "le garde lit l ordre des pas du job de publication par ce que leur run fait, et non par leur nom : la recopie vient avant toute écriture sur la pré-version courante, elle reprend bien les pièces de la courante, et la comparaison ne refuse pas la source précédente. Trois cassures : recopie déplacée en dernier, recopie retirée, refus étendu à la précédente"
relations:
  complete: ["5641-les-tests-connectes-ont-deux-cibles", "4291-un-clip-tourne-contre-la-plateforme-ne-se-range-pas-avec-les-autres"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Le tournage précédent de la plateforme de test se garde avant d'être écrasé

## Contexte

L'[ADR 5641](5641-les-tests-connectes-ont-deux-cibles.md) a levé, pour la plateforme de test, le refus
de comparer un clip connecté. Les clips de cette cible vont sur leur propre pré-version,
`clips-plateforme-de-test`, que chaque tournage écrase.

`comparer-tournages.yml` prend ses deux côtés dans des versions publiées. Il n'en avait donc qu'un à
donner à ces clips. La comparaison ne s'était faite qu'à la main, depuis des artefacts qui expirent en
quatorze jours.

## Décision

**Avant d'écrire sur la pré-version courante, le job de publication la recopie sous un second nom**,
`clips-plateforme-de-test-precedent` : les clips, l'index et les notes.

La comparaison se lance alors de l'une à l'autre. Les notes des deux pré-versions disent l'exécution
et le commit qui les ont tournées : c'est là qu'on lit ce qu'on compare. Deux tournages du même commit
mesurent du bruit ; deux commits différents, ce que le second a changé.

**L'ordre est la décision.** Recopier après le versement garderait le tournage courant sous les deux
noms. La comparaison dirait « rien n'a changé » entre un tournage et lui-même, et ce serait vert.

Une relance du job de publication ne refait pas la recopie : elle la saute quand les notes de la
pré-version portent déjà son exécution. Sans cela, la seconde tentative écraserait le précédent par le
courant.

## Ce qui a été écarté

**Tourner sur chaque tag de version**, comme les clips ordinaires. Chaque publication monterait Docker
et hériterait de deux rouges que le dépôt connaît sans les avoir réparés : la bascule de #5818 et la
panne de runner de #4187. Et une version porterait les clips ordinaires et connectés sous le même
préfixe : comparée à la pré-version de test, elle annoncerait cinquante-cinq cas « disparus ».

**Lire l'artefact d'une exécution**, par une entrée « numéro d'exécution » dans la comparaison. C'est
une seconde voie de reprise à garder en parité avec la première, et un artefact expire. Le besoin est
revenu depuis par un autre côté, celui d'une branche à comparer avant fusion : #5930 le porte.

## Conséquences

On ne compare que deux tournages consécutifs. Qui veut comparer deux commits précis lance deux
tournages dans cet ordre.

La première comparaison lancée ainsi a trouvé ce que la mesure à la main n'avait pas vu : un clip à
20 % d'écart sur le même commit
([ADR 5870](5870-un-verdict-au-bas-de-sa-page-s-y-cale.md)), puis des planchers pris par le mauvais
instrument ([ADR 5885](5885-un-plancher-appartient-a-l-instrument-qui-l-a-pris.md)).

Rejouée à la clôture du chantier, de la précédente à la courante, elle classe les cinq clips à moins
de 1,6 fois leur plancher.
