#!/usr/bin/env bash
#
# Dit ce qui a changé entre DEUX tournages de recette - et le dit aussi quand rien n'a changé.
#
# ## Le problème
#
# Depuis #4269, chaque version porte les clips de ses deux bancs sur son tag. Savoir ce qui a bougé
# entre la dernière version et le tournage courant demandait d'ouvrir cinquante lecteurs et de se
# souvenir. Ce script fait le tri ; le regard fait le reste.
#
# ## Ce qu'il produit
#
# Un dossier d'images et un index Markdown destiné au résumé du job. Par cas, trois signaux, du plus
# fiable au moins fiable :
#
# 1. la PRÉSENCE - le cas est dans les deux tournages, ou apparu, ou disparu ;
# 2. l'IMAGE FINALE - les deux dernières images accolées, leur carte des différences, et la part de
#    pixels changés ;
# 3. la DURÉE - un scénario qui s'allonge a presque toujours changé.
#
# ## Pourquoi la dernière image, et pas une image au milieu
#
# Deux tournages ne se déroulent pas au même rythme : comparer l'image n°40 de l'un à l'image n°40 de
# l'autre compare deux instants différents. À la fin, le scénario est POSÉ, et c'est le seul moment où
# les deux sont comparables sans dépendre de leur cadence.
#
# ⚠️ **Contrepartie assumée : on compare la destination, pas le chemin.** Un cas dont l'objet est une
# transition - « la modale s'ouvre sans saut » - garderait une fin identique alors que son milieu
# aurait bougé. La durée est le seul garde-fou bon marché contre ça, et il est grossier.
#
# ## Pourquoi une tolérance de couleur, et pourquoi elle se mesure
#
# Mesuré sur deux tournages du MÊME commit (#4274) : l'écart brut monte à **16 %** sur un cas, et il
# est entièrement dû à l'ANTICRÉNELAGE - la carte des différences montre du rouge sur le texte et les
# bordures, jamais sur les aplats. Un décalage de mise en page a été soupçonné puis écarté par la
# mesure : décaler l'image aggrave l'écart.
#
# Avec `-fuzz 5%`, ce même plancher tombe sous **0,01 %**, et l'instrument n'en devient pas aveugle :
# un chiffre changé rend 0,021 %, un mot 0,101 %, un libellé 0,364 %, un encart 4,2 %.
#
# ⚠️ Ces 5 % valent pour une machine et sept cas. `--plancher` remesure le plancher sur place, en
# comparant deux tournages qu'on sait identiques : une tolérance figée finirait par mentir.
#
# ## Ce qu'il ne fait pas
#
# ⚠️ **Il ne juge rien et ne bloque rien.** Un écran qui change est le résultat NORMAL d'un chantier
# qui touche l'interface. Et le chiffre TRIE, il ne prouve pas : sous un mot changé on est à deux fois
# le plancher.
#
# Usage : compare-tournages.sh <dossier avant> <dossier après> <dossier de sortie> [tolérance %]
#         compare-tournages.sh --plancher <dossier A> <dossier B>
#         compare-tournages.sh --auto-test
set -uo pipefail

MOI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

TOLERANCE_PAR_DEFAUT=5

### La dernière image d'un clip. `-update 1` réécrit le même fichier à chaque image : ce qui reste est
### la dernière, sans avoir eu à compter les images d'abord.
derniere_image() {
  ffmpeg -v error -y -i "$1" -vsync 0 -f image2 -update 1 "$2" 2>/dev/null
}

### La part de pixels qui diffèrent, en pourcentage, ou "?" si la mesure échoue.
part_changee() {
  local avant="$1" apres="$2" tolerance="$3" pixels total
  # `compare -metric AE` écrit son compte sur la SORTIE D'ERREUR et rend 1 dès qu'il y a une
  # différence : sans `|| true`, une mesure réussie passerait pour un échec.
  pixels=$(compare -metric AE -fuzz "${tolerance}%" "$avant" "$apres" null: 2>&1 || true)
  # ⚠️ Le PREMIER MOT, et c'est `awk` qui le lit - pas un découpage sur les chiffres. Au-delà d'un
  # million de pixels différents, `compare` rend lui aussi la NOTATION SCIENTIFIQUE : « 1.152e+06 (1) ».
  # Un `${pixels%%[^0-9]*}` s'arrête alors au point et rend « 1 », soit 0,000 % là où TOUT a changé.
  # C'est le même piège que celui du dénominateur, à l'autre bout du calcul, et il ment dans le sens
  # rassurant.
  pixels=${pixels%% *}
  [ -n "$pixels" ] || { printf '?'; return; }
  # ⚠️ `%w %h` et non `%[fx:w*h]`. Sur une toile de 1280 × 900, ImageMagick rend le produit en
  # NOTATION SCIENTIFIQUE - « 1.152e+06 » - que `[ … -gt 0 ]` refuse : la mesure rendait alors « ? »
  # sur TOUTES les grandes images. `compare-apercus.sh` ne peut pas le voir, ses images d'auto-test
  # faisant 80 × 40, soit 3200, qui s'écrit en entier.
  total=$(identify -format '%w %h' "$apres" 2>/dev/null) || { printf '?'; return; }
  LC_ALL=C awk -v p="$pixels" -v wh="$total" 'BEGIN {
    split(wh, d, " ")
    t = d[1] * d[2]
    # Une valeur illisible se dit, elle ne se prend pas pour un zéro.
    if (t <= 0 || p "" !~ /^[0-9]/) { printf "?"; exit }
    printf "%.3f", 100 * (p + 0) / t
  }'
}

### La durée d'un clip en secondes, ou "?" si elle ne se lit pas.
duree() {
  local d
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$1" 2>/dev/null) || { printf '?'; return; }
  [ -n "$d" ] || { printf '?'; return; }
  # ⚠️ `LC_ALL=C` : sur une machine en français, `printf` refuse « 6.5 » que `ffprobe` rend toujours
  # avec un point. Mesuré en écrivant ce script.
  LC_ALL=C awk -v d="$d" 'BEGIN { printf "%.1f", d }'
}

### Les noms de cas d'un dossier de clips, triés.
cas_du_dossier() {
  local dossier="$1" clip
  for clip in "$dossier"/*.mp4; do
    [ -e "$clip" ] || continue
    basename "$clip" .mp4
  done | LC_ALL=C sort
}

comparer() {
  local avant_dir="$1" apres_dir="$2" sortie="$3" tolerance="${4:-$TOLERANCE_PAR_DEFAUT}"
  mkdir -p "$sortie"
  local index="${sortie}/index.md"

  local noms
  noms=$( { cas_du_dossier "$avant_dir"; cas_du_dossier "$apres_dir"; } | LC_ALL=C sort -u)

  # ⚠️ Aucun clip des deux côtés n'est PAS un résultat : c'est une comparaison qui n'a rien eu à
  # comparer, et les deux se lisent pareil si on ne le dit pas.
  if [ -z "$noms" ]; then
    {
      echo "### Comparaison de deux tournages"
      echo
      echo "⚠️ **Aucun clip dans l'un ni l'autre des deux tournages.** Ce n'est pas « rien n'a changé »,"
      echo "c'est « il n'y avait rien à comparer » : vérifier que les deux dossiers ont bien été remplis."
    } > "$index"
    echo "::error::Aucun clip à comparer."
    return 1
  fi

  local lignes="${sortie}/.lignes" ; : > "$lignes"
  local communs=0 apparus=0 disparus=0 bouges=0 illisibles=0
  local nom avant apres

  for nom in $noms; do
    avant="${avant_dir}/${nom}.mp4"
    apres="${apres_dir}/${nom}.mp4"

    if [ ! -f "$avant" ]; then
      printf '%s\t%s\t%s\t%s\n' "999.999" "$nom" "cas **apparu**" "pas d'avant à montrer" >> "$lignes"
      apparus=$((apparus + 1))
      continue
    fi
    if [ ! -f "$apres" ]; then
      printf '%s\t%s\t%s\t%s\n' "998.998" "$nom" "cas **disparu**" "plus de clip dans le tournage courant" >> "$lignes"
      disparus=$((disparus + 1))
      continue
    fi

    communs=$((communs + 1))
    local ia="${sortie}/${nom}.avant.png" ib="${sortie}/${nom}.apres.png"
    if ! derniere_image "$avant" "$ia" || ! derniere_image "$apres" "$ib"; then
      printf '%s\t%s\t%s\t%s\n' "997.997" "$nom" "⚠️ image finale illisible" "clip corrompu ?" >> "$lignes"
      illisibles=$((illisibles + 1))
      rm -f "$ia" "$ib"
      continue
    fi

    local part da db geste
    part=$(part_changee "$ia" "$ib" "$tolerance")
    da=$(duree "$avant")
    db=$(duree "$apres")

    convert "$ia" "$ib" +append "${sortie}/${nom}.avant-apres.png" 2>/dev/null
    compare "$ia" "$ib" -highlight-color red -lowlight-color white "${sortie}/${nom}.ou.png" 2>/dev/null
    rm -f "$ia" "$ib"

    geste="\`${nom}.avant-apres.png\` · \`${nom}.ou.png\`"
    # ⚠️ Une mesure qui ÉCHOUE ne se range pas parmi les cas qui ne bougent pas. Ce défaut a été
    # commis en écrivant ce script : les sept mesures rendaient « ? », et l'index annonçait
    # tranquillement « aucun cas ne bouge ». Un instrument cassé qui se présente en succès est pire
    # que pas d'instrument (ADR 2748).
    if [ "$part" = "?" ]; then
      printf '%s\t%s\t%s\t%s\n' "996.996" "$nom" "⚠️ mesure impossible" "${da} s → ${db} s · ${geste}" >> "$lignes"
      illisibles=$((illisibles + 1))
      continue
    fi
    printf '%s\t%s\t%s %%\t%s\n' "$part" "$nom" "$part" "${da} s → ${db} s · ${geste}" >> "$lignes"
    # ⚠️ Le comptage se fait sur une comparaison NUMÉRIQUE, pas sur la chaîne : « 10.000 » est plus
    # grand que « 9.000 », et un tri de texte dirait l'inverse.
    awk -v p="$part" 'BEGIN { exit !(p + 0 > 0) }' 2>/dev/null && bouges=$((bouges + 1))
  done

  {
    echo "### Comparaison de deux tournages"
    echo
    echo "Tolérance de couleur : **${tolerance} %**. Le chiffre **trie**, il ne prouve pas :"
    echo "sous un mot changé on est à deux fois le plancher de bruit. C'est la carte \`.ou.png\` qui dit **où**."
    echo
    if [ "$bouges" -eq 0 ] && [ "$apparus" -eq 0 ] && [ "$disparus" -eq 0 ] && [ "$illisibles" -eq 0 ]; then
      echo "**Aucun cas ne bouge** : les ${communs} cas communs rendent une image finale identique,"
      echo "à la tolérance près, et aucun cas n'est apparu ni disparu."
    else
      echo "| Cas | Pixels changés | Durée et images |"
      echo "|---|---|---|"
      LC_ALL=C sort -rn -k1,1 "$lignes" | while IFS=$'\t' read -r _ nom part reste; do
        echo "| \`${nom}\` | ${part} | ${reste} |"
      done
    fi
    echo
    echo "_${communs} cas comparé(s), ${bouges} au-dessus de zéro, ${apparus} apparu(s), ${disparus} disparu(s), ${illisibles} mesure(s) impossible(s)._"
  } > "$index"

  rm -f "$lignes"
  echo "${communs} cas comparé(s), ${bouges} qui bougent, ${apparus} apparu(s), ${disparus} disparu(s), ${illisibles} mesure(s) impossible(s)."
  return 0
}

### Remesure le plancher : deux tournages qu'on SAIT identiques, la plus grande part observée.
plancher() {
  local a="$1" b="$2" bac pire=0
  bac=$(mktemp -d)
  trap 'rm -rf "$bac"' RETURN
  local nom
  for nom in $(cas_du_dossier "$a"); do
    [ -f "$b/$nom.mp4" ] || continue
    derniere_image "$a/$nom.mp4" "$bac/a.png" || continue
    derniere_image "$b/$nom.mp4" "$bac/b.png" || continue
    local brut fuzz
    brut=$(part_changee "$bac/a.png" "$bac/b.png" 0)
    fuzz=$(part_changee "$bac/a.png" "$bac/b.png" "$TOLERANCE_PAR_DEFAUT")
    printf '%-56s brut %8s %%   tolérance %s %% : %8s %%\n' "$nom" "$brut" "$TOLERANCE_PAR_DEFAUT" "$fuzz"
    pire=$(awk -v p="$pire" -v f="$fuzz" 'BEGIN { print (f + 0 > p + 0) ? f : p }')
  done
  echo
  echo "Plancher observé à ${TOLERANCE_PAR_DEFAUT} % de tolérance : ${pire} %."
  echo "Un écart inférieur à ce plancher ne dit rien ; c'est la carte des différences qu'il faut ouvrir."
}

# ---------------------------------------------------------------------------------------------
# Auto-test : des clips fabriqués sur place, pour éprouver les cinq cas sans dépendre d'un tournage.
#
# ⚠️ Le premier cas est le plus important : deux dossiers vides doivent être une PANNE, et non un
# « rien n'a changé ». Sans cette distinction, une comparaison qui a échoué à récupérer ses clips se
# lirait comme un produit stable (ADR 2748).
# ---------------------------------------------------------------------------------------------
if [ "${1:-}" = "--auto-test" ]; then
  command -v ffmpeg >/dev/null || { echo "ffmpeg requis pour l'auto-test." >&2; exit 2; }
  command -v compare >/dev/null || { echo "ImageMagick requis pour l'auto-test." >&2; exit 2; }
  echecs=0
  bac="$(mktemp -d)"
  trap 'rm -rf "$bac"' EXIT

  clip() { # <fichier> <couleur>
    ffmpeg -v error -y -f lavfi -i "color=c=$2:s=160x120:d=1:r=10" \
      -c:v libx264 -pix_fmt yuv420p "$1" 2>/dev/null
  }

  verifie() { # <libellé> <attendu dans la sortie> <sortie obtenue>
    if printf '%s' "$3" | grep -qF "$2"; then
      echo "  ✔ $1"
    else
      echo "  ✘ $1 : « $2 » attendu, obtenu :"
      printf '%s\n' "$3" | sed 's/^/      /'
      echecs=1
    fi
  }

  # 1. Deux dossiers vides : une panne, et elle se DIT.
  mkdir -p "$bac/vide-a" "$bac/vide-b"
  sortie=$("$MOI" "$bac/vide-a" "$bac/vide-b" "$bac/rien" 2>&1) || true
  verifie "deux dossiers vides sont une panne" "Aucun clip à comparer" "$sortie"
  verifie "et l'index refuse de dire « rien n'a changé »" "rien à comparer" "$(cat "$bac/rien/index.md")"

  # 2. Deux clips identiques : aucun cas ne bouge, et ça se dit aussi.
  mkdir -p "$bac/a" "$bac/b"
  clip "$bac/a/pareil.mp4" white
  cp "$bac/a/pareil.mp4" "$bac/b/pareil.mp4"
  sortie=$("$MOI" "$bac/a" "$bac/b" "$bac/identique" 2>&1)
  verifie "deux clips identiques ne bougent pas" "0 qui bougent" "$sortie"
  verifie "et l'index le dit" "Aucun cas ne bouge" "$(cat "$bac/identique/index.md")"

  # 3. Un cas présent seulement après : apparu.
  clip "$bac/b/neuf.mp4" blue
  sortie=$("$MOI" "$bac/a" "$bac/b" "$bac/apparu" 2>&1)
  verifie "un cas apparu est compté" "1 apparu(s)" "$sortie"
  verifie "et l'index le nomme" "cas **apparu**" "$(cat "$bac/apparu/index.md")"

  # 4. Un cas présent seulement avant : disparu.
  rm -f "$bac/b/neuf.mp4"
  clip "$bac/a/perdu.mp4" green
  sortie=$("$MOI" "$bac/a" "$bac/b" "$bac/disparu" 2>&1)
  verifie "un cas disparu est compté" "1 disparu(s)" "$sortie"

  # 5. Un clip vraiment différent : chiffré, accolé, et localisé.
  rm -f "$bac/a/perdu.mp4"
  clip "$bac/b/pareil.mp4" black
  sortie=$("$MOI" "$bac/a" "$bac/b" "$bac/change" 2>&1)
  verifie "un clip différent est vu" "1 qui bougent" "$sortie"
  verifie "et sa part de pixels vaut 100" "100.000 %" "$(cat "$bac/change/index.md")"
  if [ -f "$bac/change/pareil.avant-apres.png" ] && [ -f "$bac/change/pareil.ou.png" ]; then
    echo "  ✔ le montage ET la carte des différences existent"
  else
    echo "  ✘ le montage ou la carte des différences manque"
    echecs=1
  fi

  # 6. Une GRANDE image, celle qui a fait tomber la première version.
  #
  # ⚠️ Ce cas ne ressemble aux autres qu'en apparence. `identify -format '%[fx:w*h]'` rend le produit
  # en NOTATION SCIENTIFIQUE dès qu'il dépasse le million - « 1.152e+06 » - et le test d'entier qui le
  # suivait refusait alors la mesure. Les sept cas d'un vrai tournage rendaient « ? », et l'index
  # annonçait « aucun cas ne bouge ». Des clips de 160 × 120 ne peuvent PAS voir ce défaut : leur
  # produit s'écrit en entier. La taille est donc le cœur du cas, pas un détail.
  rm -f "$bac/a"/*.mp4 "$bac/b"/*.mp4
  ffmpeg -v error -y -f lavfi -i "color=c=white:s=1280x900:d=1:r=10" \
    -c:v libx264 -pix_fmt yuv420p "$bac/a/grand.mp4" 2>/dev/null
  ffmpeg -v error -y -f lavfi -i "color=c=black:s=1280x900:d=1:r=10" \
    -c:v libx264 -pix_fmt yuv420p "$bac/b/grand.mp4" 2>/dev/null
  sortie=$("$MOI" "$bac/a" "$bac/b" "$bac/grand" 2>&1)
  verifie "une grande toile se mesure quand même" "0 mesure(s) impossible(s)" "$sortie"
  verifie "et son écart est chiffré, pas rendu « ? »" "100.000 %" "$(cat "$bac/grand/index.md")"

  if [ "$echecs" = 0 ]; then
    echo "Auto-test de la comparaison de deux tournages : OK (12 cas, dont la grande toile et la panne des dossiers vides)."
  else
    echo "Auto-test de la comparaison de deux tournages : ÉCHEC."
  fi
  exit "$echecs"
fi

# ---------------------------------------------------------------------------------------------

if [ "${1:-}" = "--plancher" ]; then
  [ "$#" -ge 3 ] || { echo "usage : $(basename "$MOI") --plancher <dossier A> <dossier B>" >&2; exit 2; }
  plancher "$2" "$3"
  exit 0
fi

if [ "$#" -lt 3 ]; then
  echo "usage : $(basename "$MOI") <dossier avant> <dossier après> <dossier de sortie> [tolérance %]" >&2
  exit 2
fi
comparer "$1" "$2" "$3" "${4:-$TOLERANCE_PAR_DEFAUT}"
