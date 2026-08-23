#!/usr/bin/env bash
#
# Montre, sur une PR, les écrans que le diff va changer - et le dit quand il n'y en a aucun.
#
# ## Le problème
#
# `capture-vues.yml` génère les aperçus sur chaque PR, puis `filtrer-bruit-cartes.sh` rend leur version
# committée à ceux dont seul le fond cartographique a bougé. Ce qui reste modifié dans le plan de
# travail EST donc la liste des écrans qui ont vraiment changé.
#
# Cette liste était calculée, puis jetée : la publication ne se fait que sur `main`. Le seul endroit où
# l'on VOIT un changement visuel était le commit `chore(captures)`, c'est-à-dire APRÈS la fusion. Qui
# relit une PR devait croire sur parole que l'écran n'avait pas bougé.
#
# ## Ce qu'il produit
#
# Un dossier d'images `avant | après` accolées, une par écran modifié, et un index Markdown destiné au
# résumé du job. Chaque ligne chiffre la part de pixels changés - le nombre ne juge pas, il oriente le
# regard : 0,2 % sur un libellé n'est pas 12 % sur une mise en page.
#
# ⚠️ **Il ne juge rien et ne bloque rien.** Un écran qui change est le résultat NORMAL d'une PR qui
# touche l'interface ; ce qui manquait était de le montrer. Un garde qui refuserait tout changement
# visuel rendrait la CI rouge sur le travail attendu.
#
# ## Le cas qui compte autant que les autres
#
# ⚠️ **Aucun écran modifié se DIT.** Sans cette ligne, une PR sans changement d'écran serait
# indiscernable d'une comparaison qui a échoué à s'exécuter : deux silences identiques pour deux états
# opposés (ADR 2748).
#
# Usage : compare-apercus.sh <dossier de sortie> [fichier…]
#         compare-apercus.sh --auto-test
set -uo pipefail

MOI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

### La part de pixels qui diffèrent entre deux images, en pourcentage, ou "?" si la mesure échoue.
part_changee() {
  local avant="$1" apres="$2" pixels
  # `compare -metric AE` écrit son compte sur la SORTIE D'ERREUR et rend 1 dès qu'il y a une
  # différence : sans `|| true`, `set -e` ferait passer une mesure réussie pour un échec.
  pixels=$(compare -metric AE "$avant" "$apres" null: 2>&1 || true)
  # ⚠️ Le PREMIER MOT, lu par `awk`. Au-delà du million, `compare` rend la NOTATION SCIENTIFIQUE -
  # « 1.2034e+06 (1) » - et un découpage sur les chiffres s'arrête au point : il rendait « 1 », soit
  # 0,00 % là où tout l'écran avait changé (#4274).
  pixels=${pixels%% *}
  [ -n "$pixels" ] || { printf '?'; return; }

  local total
  # ⚠️ `%w %h` et non `%[fx:w*h]`, pour la même raison à l'autre bout du calcul : sur les 33 captures
  # qui dépassent le million de pixels - `apercu-import-assistant.png` fait 1100 × 1094 - le produit
  # s'écrit « 1.2034e+06 », que le test d'entier refusait. Ces écrans-là, les plus riches, rendaient
  # « ? » au lieu d'un chiffre. L'auto-test ne pouvait pas le voir avec ses images de 80 × 40.
  total=$(identify -format '%w %h' "$apres" 2>/dev/null) || { printf '?'; return; }
  LC_ALL=C awk -v p="$pixels" -v wh="$total" 'BEGIN {
    split(wh, d, " ")
    t = d[1] * d[2]
    if (t <= 0 || p "" !~ /^[0-9]/) { printf "?"; exit }
    printf "%.2f", 100 * (p + 0) / t
  }'
}

### Compose l'avant et l'après côte à côte. Rend 1 si l'une des deux images manque.
accoler() {
  local avant="$1" apres="$2" sortie="$3"
  convert "$avant" "$apres" +append "$sortie" 2>/dev/null
}

comparer() {
  local sortie="$1"
  shift
  mkdir -p "$sortie"

  local index="${sortie}/index.md"
  : > "$index"

  # ⚠️ Aucun fichier n'est PAS une panne : c'est le cas le plus fréquent, et il doit se lire comme tel.
  if [ "$#" -eq 0 ]; then
    {
      echo "### Aperçus des écrans"
      echo
      echo "**Aucun écran ne change** : les aperçus régénérés sont identiques aux versions committées,"
      echo "bruit cartographique mis à part."
    } > "$index"
    echo "Aucun écran modifié."
    return 0
  fi

  {
    echo "### Aperçus des écrans"
    echo
    echo "| Écran | Pixels changés | Aperçu |"
    echo "|---|---|---|"
  } > "$index"

  local manquants=0 traites=0
  for chemin in "$@"; do
    local nom avant
    nom=$(basename "$chemin" .png)
    avant="${sortie}/${nom}.avant.png"

    # ⚠️ L'EXISTENCE se vérifie AVANT de chercher l'avant, et l'auto-test a payé l'ordre inverse : un
    # fichier ni dans git ni sur le disque était annoncé « écran nouveau », c'est-à-dire présenté comme
    # un cas normal alors qu'il signale un plan de travail incohérent.
    if [ ! -f "$chemin" ]; then
      echo "::warning::${chemin} est annoncé modifié mais absent du plan de travail."
      manquants=$((manquants + 1))
      continue
    fi

    # L'avant vient de l'index git : c'est la version que la PR remplacerait. Un fichier NOUVEAU n'y
    # est pas, et c'est un cas normal - on le dit au lieu de le compter comme une erreur.
    if ! git show "HEAD:${chemin}" > "$avant" 2>/dev/null; then
      rm -f "$avant"
      echo "| \`${nom}\` | écran **nouveau** | pas d'avant à montrer |" >> "$index"
      traites=$((traites + 1))
      continue
    fi

    local part
    part=$(part_changee "$avant" "$chemin")
    if accoler "$avant" "$chemin" "${sortie}/${nom}.avant-apres.png"; then
      echo "| \`${nom}\` | ${part} % | \`${nom}.avant-apres.png\` |" >> "$index"
    else
      echo "| \`${nom}\` | ${part} % | ⚠️ montage impossible |" >> "$index"
      manquants=$((manquants + 1))
    fi
    rm -f "$avant"
    traites=$((traites + 1))
  done

  {
    echo
    echo "_${traites} écran(s) modifié(s). Les images sont dans l'artefact « apercus-avant-apres »._"
  } >> "$index"

  echo "${traites} écran(s) comparé(s), ${manquants} problème(s)."
  [ "$manquants" -eq 0 ]
}

# ---------------------------------------------------------------------------------------------
# Auto-test : des images fabriquées sur place, pour éprouver les quatre cas sans dépendre du dépôt.
# ---------------------------------------------------------------------------------------------
if [ "${1:-}" = "--auto-test" ]; then
  command -v convert >/dev/null || { echo "ImageMagick requis pour l'auto-test." >&2; exit 2; }
  echecs=0
  bac="$(mktemp -d)"
  trap 'rm -rf "$bac"' EXIT

  verifie() { # <libellé> <attendu dans la sortie> <sortie obtenue>
    if printf '%s' "$3" | grep -qF "$2"; then
      echo "  ✔ $1"
    else
      echo "  ✘ $1 : « $2 » attendu, obtenu :"
      printf '%s\n' "$3" | sed 's/^/      /'
      echecs=1
    fi
  }

  # 1. Aucun fichier : le cas le plus fréquent, et celui qui doit se DIRE.
  sortie=$("$MOI" "$bac/vide" 2>&1)
  verifie "aucun écran modifié le dit" "Aucun écran modifié" "$sortie"
  verifie "et l'index le dit aussi" "Aucun écran ne change" "$(cat "$bac/vide/index.md")"

  # 2. Un écran modifié : l'avant vient de git, donc on travaille dans un dépôt jetable.
  depot="$bac/depot"
  mkdir -p "$depot/.github/assets"
  git -C "$depot" init -q
  git -C "$depot" config user.email "auto@test"
  git -C "$depot" config user.name "auto"
  convert -size 80x40 xc:white "$depot/.github/assets/ecran.png"
  git -C "$depot" add -A && git -C "$depot" commit -qm "avant"
  # 40 colonnes sur 80, soit exactement la moitié : `rectangle 0,0 39,39` et non `40,40`, qui en
  # couvrirait 41 et rendrait 51,25 % - l'auto-test l'a montré.
  convert -size 80x40 xc:white -fill black -draw "rectangle 0,0 39,39" "$depot/.github/assets/ecran.png"

  sortie=$(cd "$depot" && "$MOI" "$bac/change" .github/assets/ecran.png 2>&1)
  verifie "un écran modifié est comparé" "1 écran(s) comparé(s), 0 problème(s)" "$sortie"
  verifie "sa part de pixels est chiffrée" "50.00 %" "$(cat "$bac/change/index.md")"
  if [ -f "$bac/change/ecran.avant-apres.png" ]; then
    echo "  ✔ le montage avant/après existe"
  else
    echo "  ✘ le montage avant/après manque"
    echecs=1
  fi

  # 3. Un écran NOUVEAU : pas d'avant dans git, et ce n'est pas une panne.
  convert -size 80x40 xc:blue "$depot/.github/assets/neuf.png"
  sortie=$(cd "$depot" && "$MOI" "$bac/neuf" .github/assets/neuf.png 2>&1)
  verifie "un écran nouveau est annoncé comme tel" "1 écran(s) comparé(s), 0 problème(s)" "$sortie"
  verifie "et l'index le nomme" "écran **nouveau**" "$(cat "$bac/neuf/index.md")"

  # 4. Un fichier annoncé mais absent : celui-là est un problème, et il se compte.
  sortie=$(cd "$depot" && "$MOI" "$bac/absent" .github/assets/ecran.png .github/assets/fantome.png 2>&1) || true
  verifie "un fichier absent est signalé" "1 problème(s)" "$sortie"

  # 5. Une GRANDE capture, celle que les quatre cas précédents ne peuvent pas voir.
  #
  # ⚠️ Trente-trois captures du dépôt dépassent le million de pixels, et sur celles-là ImageMagick
  # écrit ses comptes en notation scientifique. Le calcul rendait « ? », ou pire 0,00 % quand plus d'un
  # million de pixels changeaient. Des images de 80 × 40 ne peuvent PAS montrer ce défaut : la taille
  # est le cœur de ce cas, pas un détail de mise en scène.
  convert -size 1100x1094 xc:white "$depot/.github/assets/grand.png"
  git -C "$depot" add -A && git -C "$depot" commit -qm "grand avant"
  convert -size 1100x1094 xc:black "$depot/.github/assets/grand.png"
  sortie=$(cd "$depot" && "$MOI" "$bac/grand" .github/assets/grand.png 2>&1)
  verifie "une grande capture est comparée" "1 écran(s) comparé(s), 0 problème(s)" "$sortie"
  verifie "et son écart vaut 100, pas « ? » ni 0" "100.00 %" "$(cat "$bac/grand/index.md")"

  if [ "$echecs" = 0 ]; then
    echo "Auto-test de la comparaison des aperçus : OK (10 cas, dont la grande capture et 1 rouge vérifié)."
  else
    echo "Auto-test de la comparaison des aperçus : ÉCHEC."
  fi
  exit "$echecs"
fi

# ---------------------------------------------------------------------------------------------

SORTIE="${1:-}"
if [ -z "$SORTIE" ]; then
  echo "usage : $(basename "$MOI") <dossier de sortie> [fichier…]" >&2
  exit 2
fi
shift
comparer "$SORTIE" "$@"
