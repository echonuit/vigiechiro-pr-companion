---
type: adr
title: "Le format R1 se rétablit à l'entrée, pas au point de comparaison"
status: stable
article: A17
chantier: "#4576 (le rayon et le zéro, chantier #4573)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "NumeroDeCarreTest#departement_a_un_chiffre_retrouve_son_zero"
  - "CarroyageNationalTest#centroide_se_cherche_sur_six_chiffres"
  - "FournisseurEmpriseCarreOfficielTest#carre_de_departement_a_un_chiffre_est_cale"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Le format R1 se rétablit à l'entrée, pas au point de comparaison

## Contexte

La règle métier R1 veut six chiffres, département en tête, zéro de gauche compris. Le catalogue des
sites la respecte. **Les deux sources qui rendent un numéro l'amputent.**

| Source | Ce qu'elle rend pour la maille des Alpes-de-Haute-Provence |
|---|---|
| `GET /grille_stoc/cercle` | `40110` |
| `carrenat.csv.gz`, référentiel embarqué | `40110` |
| `GET /sites?q=` | `Vigiechiro - Point Fixe-040110` |

Mesuré le 2026-08-26 : `GET /sites?q=040110` trouve son site, `q=40110` ne trouve rien. Le référentiel
embarqué porte **13 342 numéros à cinq chiffres sur 137 481**.

L'amputation ne se voit pas. Un numéro à cinq chiffres est plausible, et chaque lecteur qui le compare
sans le savoir obtient un faux négatif silencieux. Deux l'ont fait pendant des mois :

- `ControleCarreStoc` comparait `"40110"` à `"040110"` et rendait `Diverge` **à tort dans neuf
  départements** (#4592) ;
- l'emprise cartographique cherchait `"040110"` dans une table dont les clés font cinq chiffres, ne
  trouvait rien, et retombait sur son repli. Un carré de département 01 à 09 **sans point géolocalisé
  n'était donc pas tracé du tout**, comme un carré hors référentiel.

## Décision

**Le format se rétablit là où le numéro entre dans l'application, une fois par entrée.**
`NumeroDeCarre.surSixChiffres` porte la règle ; `ReponsesVigieChiro` l'applique à la lecture de la
grille, et `CarroyageNational` bâtit sa table dessus.

Aucun lecteur en aval n'a donc à connaître l'amputation.

## Pourquoi pas au point de comparaison

C'était le remède évident quand on ne voyait qu'un symptôme : réparer `ControleCarreStoc`, qui
criait. Il aurait laissé le défaut entier pour le lecteur suivant, et il y en avait déjà un - l'emprise
cartographique, qui ne criait pas, elle : elle se rabattait en silence sur une approximation.

Un correctif au point de comparaison répare **le lecteur qui se plaint**, jamais celui qui se tait.

## Une conséquence à ne pas défaire

Une classe qui rendrait du rembourré tout en cherchant du brut ne pourrait pas se redonner ce qu'elle
vient de rendre. C'est arrivé pendant le chantier, entre `candidats()` et `centroide()`, et c'est la
passe 0 de la clôture qui l'a vu. La table se bâtit donc sur la forme de R1, pas sur celle du fichier.

## Comment on saurait qu'elle est rompue

`NumeroDeCarreTest#departement_a_un_chiffre_retrouve_son_zero` tient la règle.
`CarroyageNationalTest#centroide_se_cherche_sur_six_chiffres` tient que la table parle une seule langue.
`FournisseurEmpriseCarreOfficielTest#carre_de_departement_a_un_chiffre_est_cale` tient la conséquence
visible : la carte trace ces mailles.

Les trois rougissent si quelqu'un rend le référentiel « tel quel ».
