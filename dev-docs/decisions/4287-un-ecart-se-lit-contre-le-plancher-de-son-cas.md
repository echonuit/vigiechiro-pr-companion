---
type: adr
title: "Un écart se lit contre le plancher de son propre cas"
status: stable
article: A5
chantier: "#4287, EPIC #4295"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - ".github/assets/compare_tournages.py"
verified:
  - by: machine:ci
    at: 2026-08-23
relations:
  prolonge: ["4274"]
---

# Un écart se lit contre le plancher de son propre cas

## Contexte

Comparer deux tournages produit un pourcentage de pixels changés par cas. Reste à savoir à partir de
quand ce pourcentage **dit** quelque chose.

Deux tournages du **même commit**, 51 cas, lancés sur deux runners distincts, donnent la forme du bruit :

| plancher à 5 % de tolérance | cas |
|---|---|
| ≥ 0,5 % | 1 |
| 0,1 à 0,5 % | 2 |
| < 0,05 % | 48 |

**Médiane 0,008 %, pire cas 0,809 %.** Le bruit n'est donc pas une propriété du dispositif, c'est une
propriété **du cas** : 48 cas sont muets, trois sont bavards.

## Décision

**Un écart se lit contre le plancher de son propre cas**, jamais contre un seuil unique.

Les planchers vivent dans `.github/assets/planchers-tournages.tsv` - le cas, son plancher, et **le
nombre de paires de tournages qui l'ont produit**. `--plancher` les écrit et les **accumule**, en
gardant le **pire** plancher observé.

## Ce qui a été écarté, et pourquoi les deux échouent

**Le pire plancher comme seuil global** (0,809 %) **aveuglerait 48 cas pour se protéger de trois**. Un
libellé entier changé vaut 0,364 % à l'étalonnage : il passerait sous le seuil sans être vu.

**La médiane comme seuil global** (0,008 %) ferait crier les trois cas bavards à **chaque** tournage,
et le lecteur apprendrait à ignorer la colonne - ce qui revient à n'avoir pas de colonne.

Aucun seuil unique ne tient, parce qu'aucun ne peut être juste pour les deux populations à la fois.

## Conséquences

Le classement se fait par le **rapport** de l'écart au plancher du cas, et non par l'écart absolu. Sur
une comparaison réelle, les deux cas au-dessus de 1 % se séparent : l'un vaut quinze fois son bruit et
l'image confirme un vrai changement, l'autre deux fois seulement et sa carte des différences ne montre
que de l'anticrénelage.

**Le pire plancher est gardé, et non la moyenne.** Un plancher qui sous-estime le bruit fabrique des
faux positifs. Mieux vaut rater un petit changement sur un cas instable que crier au changement à
chaque tournage.

**Un plancher tiré d'une seule paire ne prouve rien.** C'est pourquoi le compte de paires est écrit
dans le fichier et rappelé dans le résumé. Un cas dont le plancher est ressorti à 0,000 % n'est pas
stable : il l'était cette fois-là. Le fichier livré avec cette décision ne porte **qu'une paire**, et
il le dit.

**Un cas sans plancher connu se dit « plancher inconnu ».** Le prendre pour stable reviendrait à
inventer une mesure qui n'a pas été faite (ADR 2748).
