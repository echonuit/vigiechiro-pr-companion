---
type: adr
title: "Une attente de fin attend un état que son étape seule produit"
status: stable
article: A2
chantier: "#6002, #6105 et #6134, lots 10, 12 et 15 du chantier #5933"
decided_at: 2026-10-07
verification: humaine
verification_note: "dire si une condition peut être fausse avant l étape demande de lire le cas, le FXML et le modèle de vue : un libellé jamais vide et un nœud déclaré ressemblent, au texte, à n importe quelle attente juste. L ADR 5334 a mesuré qu aucun motif statique ne sépare les deux. La vacance se prouve par le retard de cette même ADR, site par site"
relations:
  prolonge: ["5334-un-motif-statique-ne-distingue-pas-une-assertion-pressee"]
verified:
  - by: machine:ci
    at: 2026-10-12
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-12
---

# Une attente de fin attend un état que son étape seule produit

## Contexte

L'[ADR 5334](5334-un-motif-statique-ne-distingue-pas-une-assertion-pressee.md) a établi qu'une
attente devant une assertion ne protège que si elle attend la bonne chose, et que le défaut se
cherche en retardant le travail, pas par motif. Trois lots du chantier #5933 ont dit ce que « la
bonne chose » n'est pas, en classant 41 attentes une par une.

- **Un libellé qui n'est jamais vide** (#6002). Quatorze attentes disaient attendre la fin de
  l'inspection d'une source en lisant `#labelOriginaux`. Ce libellé affiche « 0 enregistrement(s)
  WAV détecté(s) » dès que l'écran est chargé : l'attente ne pouvait pas expirer. Sous une inspection
  retardée d'une seconde et demie, deux cas qui affirment l'absence d'un bandeau passaient verts sans
  inspection, et un cas qui en affirme la présence rougissait au lieu d'attendre.
- **La présence d'un nœud que son FXML déclare** (#6105). Vingt-trois attentes lisaient qu'un
  identifiant existe. Vingt et une suivaient le geste qui ouvre l'écran, et étaient justes : l'écran
  n'existait pas avant. Deux attendaient un bouton d'une modale déjà ouverte, présent dès son
  chargement. Sous une vérification retardée, l'une restait verte par chance, l'autre faisait un faux
  rouge en cliquant un bouton que personne ne voyait.
- **Un texte « non vide »** (#6134). Quatre attentes, toutes justes : chacune lit un texte vide avant
  l'événement attendu, et qui peut donc le rester.

## Décision

**Une attente qui précède une assertion de fin porte sur un état que son étape seule produit, donc
qui peut être faux avant elle.** Un libellé chargé avec l'écran, ou un nœud que le FXML déclare, ne
l'est pas : il faut lire ce que l'étape change, sa visibilité, son contenu ou un drapeau posé en
dernier.

Quand un même état s'attend à plusieurs endroits, il a son aide, nommée par ce qu'il signifie.
`InspectionConclue.attendre` lit la visibilité de la section d'inspection, que le modèle de vue pose
en dernier, après les bandeaux. Les douze sites de #6002 l'appellent.

**Une attente vacante se prouve, elle ne se lit pas seulement.** Le classement se fait par lecture du
cas contre son FXML et son modèle de vue, puis le retard de l'ADR 5334 montre la vacance avant le
remède, et l'attente qui expire ou qui attend après.

## Ce qui a été écarté

- **Un garde sur la forme.** Une attente sur `#labelOriginaux` est vacante après un geste et juste
  après un autre. La même ligne se lit des deux façons, et l'ADR 5334 a mesuré qu'aucun critère
  statique ne les sépare.
- **Garder le retard dans les bancs filmés.** Il allongerait chaque clip. Rien ne fait donc rougir un
  banc si l'ancienne condition revenait : c'est ce que cette décision écrit à la place.

## Conséquences

Une attente neuve se relit contre la question « pourrait-elle être fausse avant l'étape ? ».
Les 41 sites classés le sont sur leurs issues, et les seize vacants trouvés ont été corrigés : les
quatorze de #6002 par #6027 et #6108, les deux de #6105 par #6109.
