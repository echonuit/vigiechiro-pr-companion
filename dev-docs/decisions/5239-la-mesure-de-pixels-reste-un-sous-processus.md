---
type: adr
title: "La mesure de pixels reste un sous-processus ImageMagick"
status: stable
article: A26
chantier: "#5239 (sous-chantier #5235, chantier #5215)"
decided_at: 2026-09-05
verification: certaine
enforced_by:
  - ".github/assets/mesure_pixels.py"
verified:
  - by: machine:ci
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-05
---

# La mesure de pixels reste un sous-processus ImageMagick

## Contexte

Le chantier de conversion #5215 fait disparaître le shell du dépôt. Le lot B de #5235 y amène quatre
fichiers qui **regardent des pixels** : la mesure partagée `mesure-pixels.sh`, la comparaison des
aperçus, celle de deux tournages, et le filtre du bruit cartographique.

Convertir ces quatre-là posait une question que les trente-huit conversions précédentes n'avaient pas
posée : **une fois en Python, faut-il continuer d'appeler ImageMagick, ou lire les pixels par une
bibliothèque ?**

La question est réelle. `part_changee` contourne aujourd'hui deux fois la notation scientifique
d'ImageMagick - une fois sur le compte de pixels, une fois sur le produit des dimensions - et ces
contournements ont chacun coûté un défaut vécu. Avec Pillow et numpy, le calcul tiendrait en trois
lignes sans aucun de ces pièges.

## Décision

**Les quatre continuent d'appeler ImageMagick par sous-processus.** Aucune bibliothèque d'images
n'entre dans la couche des gardes.

## Conséquences

**Ce que la mesure MESURE ne change pas.** `compare -metric AE -fuzz N%` a une définition précise du
« pixel différent » : la tolérance porte sur une distance de couleur dans l'espace d'ImageMagick, et
`AE` compte les pixels au-delà de cette distance. Reproduire ce calcul avec numpy demanderait de
redéfinir la distance, donc de changer ce que le chiffre veut dire - et tous les planchers mesurés
par cas, jusqu'à 0,809 % sur son pire cas, deviendraient faux.

**La confrontation avant/après reste possible, et elle a servi.** Les 32 mesures sur les captures
réelles du dépôt sortent identiques entre les deux versions, au centième près, y compris les deux qui
doivent rendre « ? ». Une bibliothèque aurait rendu cette confrontation impossible : on n'aurait pu
comparer que deux nombres différents en espérant qu'ils disent la même chose.

**La couche qui juge tout le reste ne gagne aucune dépendance.** Le dépôt n'en déclare qu'une pour
ses gardes, PyYAML, et `verifie-dependances-declarees.py` existe parce que même celle-là n'était
déclarée nulle part. Pillow et numpy pèsent plus que le sous-processus qu'ils économiseraient.

**Le coût est assumé et il est réel.** Un sous-processus par mesure, deux invocations par cas, et un
`fuzz` qui se passe en pourcentage sur une ligne de commande plutôt qu'en paramètre typé. Sur
cinquante cas d'un tournage, cela fait deux cents appels - mesuré à quelques secondes, contre des
minutes de rendu vidéo dans le même job.

**Ce qui n'est PAS décidé ici** : la question reste ouverte pour un usage qui aurait besoin de lire
les pixels un par un. Aucun des quatre n'en a besoin ; ils comptent, ils accolent, ils masquent un
rectangle - trois choses qu'ImageMagick fait en une commande.

## Ce qui tient cette décision

`mesure_pixels.py` porte l'appel, et son en-tête porte cette décision. Le fichier est le seul endroit
du dépôt où la part de pixels se calcule : c'est déjà ce que #4295 avait établi, après que le même
défaut a dû être corrigé deux fois dans deux copies.
