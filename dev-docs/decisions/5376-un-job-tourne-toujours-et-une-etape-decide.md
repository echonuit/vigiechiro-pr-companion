---
type: adr
title: "Un job tourne toujours et conclut, et c'est une étape qui décide"
status: stable
article: A3
chantier: "#5294 (le coût des ateliers), lot #5376 du chantier #5380"
decided_at: 2026-09-07
verification: certaine
enforced_by:
  - ".github/scripts/verifie_portees_de_ci.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-09-07
generated:
  by: "process:assistance-par-agents"
---

# Un job tourne toujours et conclut, et c'est une étape qui décide

## Le contexte

Une demande payait 82 minutes de CI, et 63 % d'entre elles n'engageaient aucun Java. Le remède
évident est le filtre `paths:` de la forge : le job ne se déclenche pas quand le diff ne le concerne
pas.

Ce dépôt l'interdit, et le chantier #5294 a construit un patron pour s'en passer.

## La décision

**Un job de demande tourne toujours. Sa première étape décide, et les suivantes en dépendent.** Quand
la réponse est non, le job écrit « sans objet » et finit **vert en ayant conclu**.

La portée d'un job est déclarée dans un seul endroit, `.github/scripts/porte_du_job.py`, et tout job
de demande y figure : soit avec ses chemins, soit dans les inconditionnels avec **sa raison écrite**.

## La correspondance job vers portée est une bijection, et ce n'est pas une commodité

Un nom de job ne désigne qu'un seul job. Cette contrainte a l'air d'un détail d'implémentation ;
c'est elle qui empêche qu'un job soit **couvert par la déclaration d'un autre**.

Mesuré le 2026-09-07, avant #5439 : `docs.yml` et `maven.yml` portaient tous deux un job `build`. Le
garde comptant par nom, une seule entrée les satisfaisait, avec une raison écrite qui appartenait à
l'un et était fausse pour l'autre. Vingt couples se déclenchaient pour dix-neuf noms distincts, et le
garde d'exhaustivité était **vert sur un job qu'il n'avait jamais examiné**.

Le dispositif reproduisait donc chez lui le défaut qu'il ferme partout ailleurs.

## Pourquoi `paths:` est interdit, et ce n'est pas une préférence

Deux décisions antérieures le condamnent, chacune de son côté.

**Un job qui ne tourne pas ne rend aucun verdict.** L'ADR 4571 refuse une fusion sans verdict complet,
et `verifie_verdict_avant_fusion.py` exclut `skipped` de ses conclusions probantes. Un job filtré par
chemins n'a donc rien jugé, et il ne peut pas compter comme ayant jugé.

**Un job absent du récapitulatif ne se distingue pas d'un job vert.** C'est le motif de l'ADR 2748.
Mesuré le 2026-09-06 sur la demande #5374 : `inventaire` et `fraicheur-des-actions`, dont l'atelier
porte encore un `paths:`, sont **absents** de son récapitulatif. Ni sautés, ni neutres : absents. Le
garde du verdict compte ce qui est présent, donc il déclare le verdict complet sans eux.

Le silence explicite d'un job qui conclut « sans objet » n'est pas la même chose qu'une absence, et
c'est toute la décision.

## Ce que le silence coûte, mesuré

Un job dont la portée dit non met **5 à 9 secondes** : le checkout, puis le `git fetch` de la base.
Huit jobs mesurés sur une même demande le 2026-09-06, de 5 s pour `fuseau-alternatif` à 9 s pour
`capturer` et `analyser-ecj`.

**La forge facture à la minute entamée**, donc huit secondes coûtent une minute. C'est ce chiffre-là
qui décide, jamais les secondes : le patron échange une minute facturée par job muet contre un
récapitulatif qui reste lisible et un verdict qui reste complet.

## Chaque portée nomme son propre atelier, et le dispositif s'éprouve lui-même

Une portée inclut le workflow qui la porte et le mécanisme qui la lit. Sans cela, une modification du
dispositif ne serait **jamais éprouvée par le dispositif**.

La conséquence est voulue et se mesure : une demande qui touche `porte_du_job.py` rallume tout. Sur la
demande qui a déplacé deux jobs vers la portée, aucun « sans objet » n'est apparu et `build` a repris
ses 547 secondes. Une demande d'outillage paie le prix fort, et c'est ce qui rend le reste croyable.

## La condition qui périmerait cette décision

Tout ceci tient parce que `main` **ne porte aucune protection de branche** (#4933). Le récapitulatif
n'est lu que par des humains et par le garde du verdict, tous deux capables de comprendre « sans
objet ».

Le jour où une protection arrive, une vérification requise et sautée se lira comme une vérification
**manquante**, et la forge refusera la fusion. La décision se relit alors entièrement.

## Ce qui en découle

Quatre décisions du chantier #5294 supposent ce patron sans le porter : #5329 sur la suite répartie,
#5340 sur les chemins non déclarés, #5345 sur le banc restreint au diff, #5373 sur la liste qu'un
garde confronte. Elles ont été écrites d'abord, et celle-ci après : le patron était devenu si évident
à ceux qui l'avaient posé qu'aucun n'a vu qu'il n'était écrit nulle part.
