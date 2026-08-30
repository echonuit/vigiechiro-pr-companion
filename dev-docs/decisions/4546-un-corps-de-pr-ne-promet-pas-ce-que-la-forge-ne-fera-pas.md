---
type: adr
title: "Un corps de pull request ne promet pas ce que la forge ne fera pas"
status: stable
article: A31
chantier: "#4546 (passe 11 de la clôture de #4502)"
decided_at: 2026-08-26
verification: certaine
enforced_by:
  - ".github/scripts/verifie-corps-pr.sh"
verified:
  - by: machine:ci
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Un corps de pull request ne promet pas ce que la forge ne fera pas

## Contexte

`verifie-corps-pr.sh` refusait trois défauts, tous de **typographie** : un cadratin, une apostrophe
courbe, une élision sans apostrophe. Chacun est adossé à une décision qui vaut partout ailleurs dans
le dépôt, et le corps de pull request y était simplement rattaché par l'ADR 4453.

« Ferme #N » n'est pas de cette famille. La forge ne reconnaît que `close`, `fix` et `resolve` : la
demande fusionne verte, l'issue reste ouverte, et **rien ne le signale**. Ce n'est pas une forme
malheureuse, c'est une promesse non tenue.

#4350 avait posé la règle sans la rendre opposable, et écarté un garde sur un argument juste : il
aurait cherché ce qui **manque**, un mot-clé de fermeture, et rougi sur tout lot d'un EPIC qui
renvoie sans clore. « La ligne du gabarit suffit probablement. »

Elle ne suffit pas. Le piège s'est produit **deux fois dans une seule session**, gabarit en place et
règle connue : #4506 et #4527 ont fusionné en promettant leur fermeture, et sont restées ouvertes
jusqu'à un balayage des issues.

## Décision

**Le corps d'une pull request est refusé s'il porte un verbe de fermeture français accolé à un
renvoi**, et ce refus s'ajoute aux trois autres du même garde.

La règle cherche ce qui est **présent**, jamais ce qui manque. C'est ce qui la sépare du garde
qu'écartait #4350 : la forme refusée n'est jamais légitime, puisque qui l'écrit veut clore et ne
clôt pas. « Refs #N » et « Rattaché à #N » ne la portent pas, donc un lot d'EPIC reste vert.

**`close` est absent de la liste des verbes**, et c'est le point qui décide de la justesse du garde :
« Close #N » est un mot-clé anglais valide, que la forge honore. Le refuser ferait réécrire en
français ce qui marchait, soit l'inverse exact du service rendu.

## Conséquences

Le niveau est `certaine` : la forme se voit à coup sûr, et cinq contrôles négatifs tiennent qu'un
renvoi ordinaire passe. Ce n'est pas un cliquet, la zone étant à zéro le jour de la décision.

**Un garde qui refuse une promesse, et non une forme.** C'est ce que cette ADR ajoute au dépôt, et
ce dont la cinquième règle du même fichier devra se réclamer ou se distinguer : les trois premières
refusent ce qui se lit mal, celle-ci refuse ce qui ne se fera pas.

Les accents se déplient avant la comparaison, le motif étant écrit sans eux. Un témoin accentué le
tient : écrit sans accent, il passait la mutation sans rien dire, et l'ADR 3661 nomme ce défaut.
