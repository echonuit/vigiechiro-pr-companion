---
type: adr
title: "Un plancher appartient à l'instrument qui l'a pris, et repart de zéro quand cet instrument change"
status: stable
article: A5
chantier: "#5885, lot 10 du sous-chantier #5644"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - ".github/assets/compare_tournages.py"
  - ".github/scripts/verifie_decisions_du_tournage_connecte.py"
verification_note: "l auto-test de l outil tient l en-tête, les deux refus et la lecture de la version amont, seize mutants tués sur seize ; le garde tient les quatre décisions de l atelier de mesure, en lançant ses blocs face à un gh leurre. Rien ne tient que l image du runner reste celle des planchers : c est le refus de l outil qui le dira, le jour où elle changera"
relations:
  amende: ["4287-un-ecart-se-lit-contre-le-plancher-de-son-cas"]
  prolonge: ["4274-on-compare-la-derniere-image-pas-le-chemin", "5239-la-mesure-de-pixels-reste-un-sous-processus"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un plancher appartient à l'instrument qui l'a pris, et repart de zéro quand cet instrument change

## Contexte

L'[ADR 4287](4287-un-ecart-se-lit-contre-le-plancher-de-son-cas.md) lit l'écart d'un clip contre le
plancher de son propre cas : de combien deux tournages du même commit diffèrent. Le plancher se
prenait à la main, par `compare_tournages.py --plancher`, sur la machine de qui le lançait.

Le même script ne rend pas le même chiffre partout. Mesuré le 5 octobre 2026 sur la même paire de
clips : **0,020 %** avec ffmpeg 8.0.1 et ImageMagick 7.1.2, **0,134 %** avec ffmpeg 6.1.1 et
ImageMagick 6.9.12, les versions du runner. Sur 95 clips, l'écart du second instrument n'est jamais
inférieur à celui du premier, et sa médiane en vaut 5,5 fois.

Les 51 planchers du dépôt avaient été pris sur un poste, et le flux les lisait avec ses outils. Deux
tournages du même commit y sortaient **32 clips sur 51 à plus du double de leur plancher**. Rien ne le
disait : le fichier ne portait ni où ni avec quoi une ligne avait été mesurée.

## Décision

**Le fichier de planchers dit son instrument, et l'outil le confronte au sien dans les deux sens.**

- L'en-tête porte la version amont de ffmpeg et d'ImageMagick.
- L'outil **refuse de comparer** contre des planchers pris par un autre instrument, ou qui ne disent
  pas le leur.
- Il **refuse de compléter** un tel fichier, et le laisse intact.

Le refus nomme les deux instruments. Sans les deux noms, on ne sait pas lequel changer.

**La mesure se fait par un atelier**, `mesurer-les-planchers.yml`, sur la même image de runner et avec
les mêmes paquets que la comparaison. Il reçoit des numéros d'exécution, joue toutes les paires, et
rend le fichier en artefact : une demande le committe.

**Un plancher repart de zéro quand son instrument change.** C'est ce qui amende l'ADR 4287, pour
laquelle un plancher ne redescend jamais. La règle du pire observé vaut entre des mesures qui se
comparent. Garder le pire de deux instruments ne décrit le bruit d'aucun.

## Ce qui a été écarté

**Prévenir au lieu de refuser.** Un index classé contre le mauvais sol a l'air aussi juste que l'autre,
et un avertissement se lit une fois.

**La version du paquet plutôt que la version amont.** `6.1.1-3ubuntu5` ferait refuser la comparaison à
chaque correctif de sécurité reporté par la distribution, et remesurer cent clips pour rien. Le
contrôle retient `6.1.1`.

**Un instrument par ligne.** Le fichier se complète en gardant le pire : une ligne à deux instruments
n'aurait pas de sens, et un en-tête suffit dès que le mélange est refusé.

**La consigne seule**, celle d'un conteneur de la distribution du runner. Elle rend les chiffres du
flux au millième, et c'est ce qui a rempli le fichier avant que l'atelier existe. Mais une consigne ne
tient pas ce qu'un outil refuse.

## Conséquences

Sur un poste, la comparaison avec planchers est refusée. Pour regarder une paire chez soi, on compare
sans fichier de planchers : les écarts sortent en valeur absolue.

Le jour où l'image du runner changera de version, `comparer-tournages.yml` rougira en nommant les deux
instruments. Le geste est alors de relancer l'atelier avec `repartir_de_zero`.

L'atelier refuse des tournages de commits différents : un plancher pris entre deux commits rangerait un
changement du produit parmi le bruit. Ses témoins sont deux tournages hors de la mesure, parce qu'un
tournage qui a servi à mesurer reste sous son plancher par construction.

Les planchers remesurés valent 0,160 % en médiane sur 88 clips, contre 0,009 % pour ceux d'avant. Sur
deux témoins, 81 clips restent dans leur plancher et 3 dépassent le double, pour des écarts de 0,015 à
0,300 % : c'est la queue de la règle du pire, que #4309 porte.

## Ce qu'on ne sait pas

Laquelle des deux moitiés de l'instrument porte l'écart, l'extraction de l'image ou le comptage des
pixels. Elles n'ont pas été départagées, et l'en-tête nomme donc les deux.
