#!/usr/bin/env bash
#
# Rend leur version committée aux aperçus de CARTE dont **seul le fond cartographique** a changé.
#
# ## Le problème
#
# Les aperçus qui portent un fond OpenStreetMap changent à presque chaque exécution de la CI sans
# qu'aucun code n'ait bougé : `apercu-analyse-carte.png` a changé dans 28 des 30 commits d'aperçus
# précédant #3359. Trois coûts, dont le dernier est le seul qui compte : l'historique se remplit de
# commits qui ne disent rien ; les PR d'aperçus **conflictent entre elles** en permanence, les PNG
# étant binaires ; et le jour où une **vraie** régression touche une de ces images, elle devient
# indiscernable du bruit.
#
# ## Pourquoi un masque, et non plus un seuil
#
# La première version comparait un **pourcentage de pixels** à un seuil de 4 %. Mesure faite après
# #3375 - qui a rendu la CI et un poste de développement identiques partout sauf dans les tuiles - ce
# seuil ne pouvait pas tenir : le bruit de tuiles **seul** vaut jusqu'à **23,8 %** de l'image sur
# `apercu-multisite-carte-pleine`, et 9,7 % sur `apercu-multisite-edition`. Aucun pourcentage global
# ne sépare le bruit du signal, les deux vivant dans la même zone.
#
# Ce que #3375 a rendu possible, en revanche : **hors de la carte, la CI et un poste rendent au pixel
# près** - `apercu-accueil.png` sort identique au bit près. La bonne question n'est donc plus « de
# combien ça diffère ? » mais « quelque chose a-t-il changé **hors** de la carte ? », à tolérance
# **zéro**.
#
# C'est strictement mieux que le seuil : avec lui, un diff sur ces fichiers n'était un signal nulle
# part ; avec le masque, il redevient un signal **partout sauf dans le rectangle de la carte**.
#
# ⚠️ Ce que le masque ne voit pas : un changement **à l'intérieur** de la carte - un marqueur déplacé,
# un carré recoloré. C'est le prix, et c'est l'arbitrage déjà écrit dans l'ADR 3068 (« sur ces
# fichiers, la revue se fait à l'œil, pas au `cmp` ») - sauf qu'il ne porte désormais que sur le
# rectangle, et non sur toute l'image.
set -euo pipefail

# Auto-test (#3385), sur le modele de `check-captures.sh` (#3293) et de `verifie-titre-pr.sh` (#2947) :
# un garde qui cesse de detecter reste vert, et c'est le seul defaut qui se presente sous la forme d'un
# succes. Chaque cas monte un depot jetable, REINVOQUE ce script dessus, et compare le resultat a
# l'attendu - le cas de test et le chemin reel sont donc le meme code, par construction.
#
# Ce mode existe parce que ce script a ete livre SANS lui : ses deux cas etaient joues a la main, et
# rien n'aurait signale qu'il cesse de filtrer. C'est la faute que la cloture de #3385 a relevee dans
# son propre travail.
if [ "${1:-}" = "--auto-test" ]; then
    if ! command -v magick > /dev/null 2>&1 && ! command -v convert > /dev/null 2>&1; then
        echo "auto-test ignore : ImageMagick absent." >&2
        exit 0
    fi
    dessiner() { if command -v magick > /dev/null 2>&1; then magick "$@"; else convert "$@"; fi; }

    # `$0` est RELATIF quand le script est invoque depuis la racine du depot : apres le `cd` dans le
    # bac, il ne resout plus rien et le script ne s'executait pas du tout - l'auto-test concluait
    # « gardee » pour la mauvaise raison. Premier defaut que ce mode a trouve : le sien.
    moi="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
    echecs=0
    bac="$(mktemp -d)"
    trap 'rm -rf "${bac}"' EXIT

    monter() { # un depot jetable avec UNE capture committee, carte au centre
        rm -rf "${bac}/depot"
        mkdir -p "${bac}/depot/.github/assets"
        git -C "${bac}/depot" init -q
        git -C "${bac}/depot" config user.email t@t
        git -C "${bac}/depot" config user.name t
        dessiner -size 200x200 xc:white \
            -fill gray -draw "rectangle 50,50 150,150" \
            "${bac}/depot/.github/assets/apercu-test-carte.png"
        git -C "${bac}/depot" add -A
        git -C "${bac}/depot" commit -qm base
    }

    lancer() { # rend 0 si la capture a ete RENDUE (donc jugee bruit), 1 si elle a ete gardee
        ( cd "${bac}/depot" \
          && CARTES_ASSETS="${bac}/depot/.github/assets" \
             CARTES_LISTE="apercu-test-carte.png 50,50,150,150" \
             "${moi}" > /dev/null 2>&1
          cd "${bac}/depot" && git diff --quiet -- .github/assets/apercu-test-carte.png )
    }

    verifie() { # <attendu:rendue|gardee> <libelle>
        if lancer; then obtenu=rendue; else obtenu=gardee; fi
        if [ "${obtenu}" = "$1" ]; then echo "  ✔ $2"; else
            echo "  ✘ $2 : attendue ${1}, obtenue ${obtenu}"; echecs=1
        fi
    }

    monter
    dessiner "${bac}/depot/.github/assets/apercu-test-carte.png" \
        -fill black -draw "rectangle 60,60 140,140" \
        "${bac}/depot/.github/assets/apercu-test-carte.png"
    verifie rendue "un changement ENTIEREMENT dans la carte est du bruit"

    monter
    dessiner "${bac}/depot/.github/assets/apercu-test-carte.png" \
        -fill black -draw "rectangle 10,10 30,20" \
        "${bac}/depot/.github/assets/apercu-test-carte.png"
    verifie gardee "un changement HORS carte est garde, si petit soit-il"

    monter
    dessiner "${bac}/depot/.github/assets/apercu-test-carte.png" \
        -fill black -draw "rectangle 60,60 140,140" -fill black -draw "rectangle 10,10 30,20" \
        "${bac}/depot/.github/assets/apercu-test-carte.png"
    verifie gardee "du bruit de carte NE MASQUE PAS un changement hors carte"

    [ "${echecs}" = 0 ] && echo "Auto-test : les trois cas passent." || echo "Auto-test : ECHEC." >&2
    exit "${echecs}"
fi

# Surchargeables par l'auto-test, qui vise un depot jetable plutot que la galerie reelle.
ICI="${CARTES_ASSETS:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"

# Aperçus à fond cartographique, et le rectangle `x1,y1,x2,y2` de leur carte.
#
# Les rectangles sont **mesurés**, pas estimés : depuis #3375, un diff entre la version de la CI et
# une régénération locale ne contient plus **que** les tuiles, donc sa boîte englobante **est** la
# carte. Relevé du 2026-08-06, avec une marge de quelques pixels sur chaque bord.
#
# ⚠️ Une capture dont la mise en page bouge doit voir son rectangle **remesuré** : trop petit, le bruit
# repasse et l'on recommence à committer pour rien ; trop grand, on s'aveugle sur une bande qui n'est
# pas la carte. Le garde ci-dessous refuse un nom qui n'existe pas ; il ne peut rien dire du rectangle.
if [ -n "${CARTES_LISTE:-}" ]; then
  CARTES=("${CARTES_LISTE}")
else
CARTES=(
  "apercu-analyse-carte.png 190,138,1068,424"
  "apercu-multisite.png 12,138,469,568"
  "apercu-multisite-annee-invalide.png 12,144,469,571"
  "apercu-multisite-carte-pleine.png 12,90,1088,571"
  "apercu-multisite-edition.png 12,90,865,571"
  "apercu-multisite-filtre.png 12,138,469,568"
  "apercu-import-assistant.png 35,679,1065,880"
  "apercu-import-decompression-volume.png 524,825,547,849"
  "apercu-import-en-cours.png 35,679,1065,880"
  "apercu-import-incoherence.png 35,743,1065,944"
  "apercu-import-melange.png 35,743,1065,944"
  "apercu-import-multi-nuits.png 35,787,1065,988"
  "apercu-import-rattachement-avertissements.png 35,679,1065,880"
  "apercu-import-rejets.png 35,679,1065,880"
  "apercu-sites-modale-point.png 18,331,464,457"
  "apercu-sites-modale-point-creation.png 18,331,464,457"
)
fi

# ImageMagick s'invoque de deux façons selon sa version, et les deux existent dans la nature : la **7**
# regroupe tous les outils sous `magick`, la **6** - celle du paquet `imagemagick` d'Ubuntu 24.04, donc
# du runner - expose `compare` et `convert` comme commandes propres, et n'a pas de `magick`.
#
# ⚠️ La première version de ce script exigeait `magick` : elle passait sur un poste en ImageMagick 7 et
# échouait en CI. Le garde était juste, l'exigence non (#3370).
if command -v magick > /dev/null 2>&1; then
  comparer() { magick compare "$@"; }
  masquer() { magick "$@"; }
elif command -v compare > /dev/null 2>&1 && command -v convert > /dev/null 2>&1; then
  comparer() { compare "$@"; }
  masquer() { convert "$@"; }
else
  echo "::error::ImageMagick est requis pour filtrer le bruit des cartes (paquet « imagemagick »)." >&2
  exit 1
fi

restaurees=0
gardees=0
for entree in "${CARTES[@]}"; do
  read -r nom rect <<< "${entree}"
  chemin=".github/assets/${nom}"

  if [ ! -f "${ICI}/${nom}" ]; then
    echo "::error::${nom} est déclarée dans CARTES mais n'existe pas. Liste à corriger." >&2
    exit 1
  fi
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

  # La carte est noircie des DEUX côtés : ce qui reste est tout le produit, et rien que lui.
  IFS=',' read -r x1 y1 x2 y2 <<< "${rect}"
  avant_masque="$(mktemp --suffix=.png)"
  apres_masque="$(mktemp --suffix=.png)"
  masquer "${avant}" -fill black -draw "rectangle ${x1},${y1} ${x2},${y2}" "${avant_masque}"
  masquer "${ICI}/${nom}" -fill black -draw "rectangle ${x1},${y1} ${x2},${y2}" "${apres_masque}"

  differents="$(comparer -metric AE "${avant_masque}" "${apres_masque}" null: 2>&1 || true)"
  rm -f "${avant}" "${avant_masque}" "${apres_masque}"
  differents="${differents%%[^0-9]*}"

  if [ -z "${differents}" ]; then
    echo "  ${nom} : comparaison impossible (dimensions différentes ?), gardée."
    gardees=$((gardees + 1))
  elif [ "${differents}" -eq 0 ]; then
    git checkout -- "${chemin}"
    printf "  %-46s hors carte : identique -> version committée rendue\n" "${nom}"
    restaurees=$((restaurees + 1))
  else
    printf "  %-46s hors carte : %s pixel(s) changé(s) -> gardée\n" "${nom}" "${differents}"
    gardees=$((gardees + 1))
  fi
done

echo "Bruit des cartes : ${restaurees} rendue(s), ${gardees} gardée(s)."
