---
type: adr
title: "Une position se saisit d'une seule façon, pour un site comme pour un point"
status: stable
article: A23
heuristiques:
  - "nielsen-4"
  - "nielsen-5"
chantier: "#5688, lots 19 et 20 du chantier #5596 (#5687, #5688)"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/sites/model/PositionColleeTest.java"
  - "src/test/java/fr/univ_amu/iut/sites/viewmodel/PointEditPositionTest.java"
  - "src/test/java/fr/univ_amu/iut/sites/viewmodel/SiteEditPremierPointTest.java"
  - "src/test/java/fr/univ_amu/iut/sites/model/PointVoisinTest.java"
verification_note: "les quatre classes tiennent la lecture d une position, le champ unique du point, la case du premier point et le voisinage à 40 m. Elles ne disent rien du seuil de 200 m que la fiche d un site applique par ailleurs, dont l origine est instruite en #5839"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Une position se saisit d'une seule façon, pour un site comme pour un point
## Contexte

L'observateur qui déclare un site colle une position pour trouver son carré : un champ, une paire, lue
par `PositionCollee`. Pour créer ensuite un point d'écoute, la modale lui demandait la même position
dans deux champs, Latitude et Longitude, lus axe par axe par un autre analyseur, `AnalyseurCoordonnees`,
qui n'acceptait pas les mêmes formes. Le porteur l'a dit ainsi : « avoir deux manières de saisir les
coordonnées est déstabilisant ».

## Décision

**1. Un seul champ « Position », lu au fil de la saisie**, pour le point comme pour le site. Ce qui
est lu, ou ce qui ne se lit pas, se dit sous le champ.

**2. Une seule règle de lecture.** `PositionCollee` gagne les formes que l'autre analyseur acceptait
(degrés et minutes décimales, cardinal après un décimal, refus motivé de la virgule décimale).
`AnalyseurCoordonnees` est supprimé : un lecteur sans appelant serait le prochain à diverger.

**3. Le premier point se crée avec son site**, par une case décochée par défaut, offerte dès que la
position collée se lit. Deux enregistrements successifs, non atomiques : si le point est refusé, le
site reste, et l'annonce le dit.

**4. Le code d'un nouveau point est proposé** : `Z` suivi du premier numéro libre, comme le portail
nomme un point libre. Les noms `A1` à `H2` relèvent de #5608.

**5. Un point du site à 40 m ou moins se signale, sans rien interdire.** C'est le rayon dans lequel le
portail tient deux positions pour le même point. L'écran et `ajouter-point` disent la même phrase
(`PointVoisin.Voisin.avertissement`).

## Ce qui a été écarté

**Ouvrir la modale de point à la suite de celle du site.** Une case dans la modale de site suffit, et
ne force aucun geste.

**Aligner la commande sur l'écran.** `ajouter-point` garde `--code` obligatoire et `--lat`, `--lon`
séparés : un script nomme ses points, et une paire collée n'y a pas de sens.

## Conséquences

Une forme de coordonnées nouvelle s'ajoute à `PositionCollee`, donc partout à la fois.
