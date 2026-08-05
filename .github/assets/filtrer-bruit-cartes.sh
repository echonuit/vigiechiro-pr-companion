#!/usr/bin/env bash
#
# Rend leur version committée aux aperçus de CARTE dont l'écart n'est que du bruit de tuiles.
#
# ## Le problème
#
# Les aperçus qui portent un fond OpenStreetMap changent à presque chaque exécution de la CI sans
# qu'aucun code n'ait bougé. Mesuré sur les 30 derniers commits d'aperçus :
# `apercu-analyse-carte.png` a changé 28 fois, `apercu-multisite-carte-pleine.png` 27, les huit
# aperçus d'import 18. L'écart est sub-perceptible - deux versions consécutives ouvertes côte à côte
# sont indiscernables - mais il suffit à produire un commit, une PR, et un conflit avec la PR
# d'aperçus suivante (les PNG sont binaires : git ne sait pas les fusionner).
#
# Trois coûts, dont le dernier est le vrai : l'historique se remplit de commits qui ne disent rien,
# les PR d'aperçus conflictent en permanence, et le jour où une VRAIE régression touche une de ces
# images, elle devient indiscernable du bruit.
#
# ## Ce que ce script fait, et ne fait pas
#
# Il ne cherche pas à rendre les tuiles déterministes : elles sont une entrée **extérieure** au
# dépôt, et l'[ADR 3068] a tranché qu'on ne les figerait pas - une carte figée serait plus stable et
# moins vraie. Il cesse simplement de **committer l'insignifiant**.
#
# ⚠️ La tolérance ne vaut QUE pour les aperçus listés ci-dessous. Partout ailleurs, le moindre pixel
# reste committé, et c'est délibéré : sur un plein écran de 1080x640, une puce ajoutée pèse 0,6 %, un
# libellé corrigé bien moins. Un seuil **global** aurait avalé ces changements-là - exactement ceux
# que la galerie existe pour montrer.
#
# ## Le seuil, et pourquoi celui-là
#
# 3 %, et la marge se mesure des deux côtés :
#
#   - le **bruit** vaut 0,34 % (mesure de l'ADR 3068) à 1,6 % (mesure du 2026-08-05 entre deux
#     commits consécutifs d'`apercu-analyse-carte`) ;
#   - une **vraie** différence de tuile change un carré de 256x256. Sur `apercu-analyse-carte`
#     (1080x640) cela pèse 9,5 % ; sur l'aperçu le plus défavorable, où la carte n'occupe qu'une
#     bande de 195 px de haut (`apercu-import-assistant`, 1100x1032), encore 4,4 %.
#
# Le seuil se place donc entre 1,6 % et 4,4 %, et 3 % s'y tient du côté prudent.
set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEUIL_PCT="${SEUIL_BRUIT_CARTES:-3}"

# Aperçus portant un fond cartographique. Liste **explicite**, comme les inventaires de
# `cli-surface.bats` : une capture qui gagne une carte doit être ajoutée ici sciemment, et le garde
# ci-dessous refuse un nom qui n'existe pas.
#
# Établie par MESURE (quelles captures bougent d'un commit d'aperçus à l'autre) puis **vérifiée** :
# chaque outil producteur monte bien une carte, et `apercu-import-assistant.png` a été ouverte pour
# confirmer la bande cartographique en bas de l'assistant.
#
# ⚠️ `apercu-passage-rattachement.png` bouge aussi (10 fois sur 30) et n'est **pas** ici :
# `CapturePassage` ne monte aucune carte. Son instabilité a donc une autre cause, non élucidée, et
# lui appliquer cette tolérance masquerait des changements qui ne sont pas du bruit de tuiles.
CAPTURES_CARTE=(
  apercu-analyse-carte.png
  apercu-multisite.png
  apercu-multisite-annee-invalide.png
  apercu-multisite-carte-pleine.png
  apercu-multisite-edition.png
  apercu-multisite-filtre.png
  apercu-import-assistant.png
  apercu-import-decompression-volume.png
  apercu-import-en-cours.png
  apercu-import-incoherence.png
  apercu-import-melange.png
  apercu-import-multi-nuits.png
  apercu-import-rattachement-avertissements.png
  apercu-import-rejets.png
  apercu-sites-modale-point.png
  apercu-sites-modale-point-creation.png
)

if ! command -v compare > /dev/null 2>&1 || ! command -v magick > /dev/null 2>&1; then
  echo "::error::ImageMagick (compare, magick) est requis pour filtrer le bruit des cartes." >&2
  exit 1
fi

restaurees=0
gardees=0
for nom in "${CAPTURES_CARTE[@]}"; do
  chemin=".github/assets/${nom}"

  if [ ! -f "${ICI}/${nom}" ]; then
    echo "::error::${nom} est déclarée dans CAPTURES_CARTE mais n'existe pas. Liste à corriger." >&2
    exit 1
  fi

  # Une capture non modifiée n'a rien à comparer.
  if git diff --quiet -- "${chemin}" 2>/dev/null; then
    continue
  fi

  avant="$(mktemp --suffix=.png)"
  if ! git show "HEAD:${chemin}" > "${avant}" 2>/dev/null; then
    rm -f "${avant}"
    echo "  ${nom} : nouvelle capture, gardée."
    gardees=$((gardees + 1))
    continue
  fi

  # `compare -metric AE` écrit le NOMBRE de pixels différents sur stderr, et sort en 1 dès qu'il y a
  # une différence : ce code n'est pas une erreur ici, d'où le `|| true`.
  differents="$(compare -metric AE "${avant}" "${ICI}/${nom}" null: 2>&1 || true)"
  rm -f "${avant}"
  differents="${differents%%[^0-9]*}"
  if [ -z "${differents}" ]; then
    echo "  ${nom} : comparaison impossible (dimensions différentes ?), gardée."
    gardees=$((gardees + 1))
    continue
  fi

  total="$(magick identify -format "%[fx:w*h]" "${ICI}/${nom}")"
  # Arithmétique entière : le pourcentage est porté au centième pour rester lisible.
  pct_x100=$((differents * 10000 / total))
  seuil_x100=$((SEUIL_PCT * 100))

  if [ "${pct_x100}" -lt "${seuil_x100}" ]; then
    git checkout -- "${chemin}"
    printf "  %-46s %2d,%02d %% -> bruit, version committée rendue\n" \
      "${nom}" $((pct_x100 / 100)) $((pct_x100 % 100))
    restaurees=$((restaurees + 1))
  else
    printf "  %-46s %2d,%02d %% -> au-dessus du seuil, gardée\n" \
      "${nom}" $((pct_x100 / 100)) $((pct_x100 % 100))
    gardees=$((gardees + 1))
  fi
done

echo "Bruit des cartes : ${restaurees} rendue(s), ${gardees} gardée(s) (seuil ${SEUIL_PCT} %)."
