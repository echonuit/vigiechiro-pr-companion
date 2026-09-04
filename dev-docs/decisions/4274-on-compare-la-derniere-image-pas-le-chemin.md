---
type: adr
title: "On compare la dernière image d'un clip, pas son chemin"
status: stable
article: A5
chantier: "#4274, EPIC #4295"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - ".github/assets/compare_tournages.py"
verified:
  - by: machine:ci
    at: 2026-08-23
---

# On compare la dernière image d'un clip, pas son chemin

## Contexte

Comparer deux tournages demande de choisir **quoi** comparer dans un clip.

Deux tournages ne se déroulent pas au même rythme. Le même scénario, filmé deux fois, ne place pas ses
gestes aux mêmes instants : une modale qui met 200 ms de plus à s'ouvrir décale tout ce qui suit.
Comparer l'image n°40 de l'un à l'image n°40 de l'autre compare donc **deux moments différents**, et
rend un écart qui ne parle que de cadence.

Mesuré sur deux tournages du même commit : à la fin du clip, le scénario est **posé**, et l'écart
retombe sous 0,01 % pour 48 cas sur 51. C'est le seul instant où deux tournages sont comparables sans
dépendre de leur rythme.

## Décision

**On compare la dernière image de chaque clip.**

Trois signaux accompagnent cette image, du plus fiable au moins fiable : la **présence** du cas dans
les deux tournages, l'**image finale** elle-même avec sa carte des différences, et la **durée** du clip.

## Conséquences

**On compare la destination, pas le chemin.** Un cas dont l'objet est une transition garderait une
fin identique alors que son milieu aurait bougé. La durée est le seul garde-fou bon marché contre cela,
et il est grossier.

**Et cette limite mord précisément là où l'[ADR 4166](4166-un-etat-ne-se-juge-que-contre-son-contraire.md)
demande le plus.** Cette décision-là exige qu'un cas qui fait juger un état montre **cet état et son
contraire dans le même clip** : le geste offert puis empêché, le bouton grisé à côté d'un bouton actif.
Pour ces cas, ce qui fait le cas est le **contraste entre deux moments**, et l'image finale n'en garde
qu'un. Un changement qui toucherait le premier des deux états passerait donc inaperçu.

C'est une limite **assumée et écrite**, pas un oubli. Elle est signalée dans l'outil, dans la page
[Comparer deux tournages](../recette/comparer-deux-tournages.md), et suivie par une issue de suite.

## Ce qui a été écarté

**Échantillonner plusieurs images.** Rejeté pour la raison du contexte : sans point d'ancrage commun,
deux échantillons pris au même rang comparent deux instants différents, et le bruit de cadence noie le
signal.

**Comparer la première ET la dernière.** Non rejeté sur le fond - c'est la piste que l'issue de suite
instruit - mais pas retenu ici faute de l'avoir mesuré. Une première image n'est pas plus stable qu'une
autre tant qu'on ne l'a pas éprouvée sur deux tournages du même commit.
