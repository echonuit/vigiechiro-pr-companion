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
# ## Le plancher se mesure par CAS, pas une fois pour toutes
#
# Mesuré sur 51 cas, deux tournages du même commit sur deux runners : la médiane du plancher vaut
# 0,008 %, 48 cas sur 51 sont sous 0,05 %, et TROIS dépassent - jusqu'à 0,809 %.
#
# ⚠️ Un seuil unique mentirait donc dans les deux sens. Le pire plancher aveuglerait 48 cas pour se
# protéger de trois ; la médiane ferait crier ces trois-là à chaque tournage. Un écart se lit contre
# le plancher de SON cas, et c'est à quoi sert le fichier de planchers (#4287).
#
# Usage : compare-tournages.sh <dossier avant> <dossier après> <dossier de sortie> [tolérance %] [planchers]
#         compare-tournages.sh --plancher <dossier A> <dossier B> [fichier de planchers]
#         compare-tournages.sh --auto-test
set -uo pipefail

MOI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

# La mesure des pixels est PARTAGÉE avec l'autre comparaison : elle portait le même défaut aux deux
# endroits, corrigé deux fois (#4295).
# shellcheck source=.github/assets/mesure-pixels.sh
. "$(dirname "$MOI")/mesure-pixels.sh"

### Refuse de commencer sans ses outils, en NOMMANT ceux qui manquent.
###
### ⚠️ Sans cette garde, l'absence d'un outil ne se voyait pas : `compare` introuvable écrivait son
### « command not found » dans la sortie que la mesure capture, la part de pixels devenait « ? », et
### les cinquante cas d'un vrai tournage se rangeaient en « mesure impossible » sans qu'une seule
### ligne ne dise POURQUOI. Mesuré sur le run 32640637929, où le job restait vert.
###
### Un outil manquant est une PANNE D'INSTALLATION, pas un résultat de mesure. Les deux ne se
### réparent pas au même endroit, donc ils ne doivent pas se lire pareil.
exige_ses_outils() {
  local manquants=() outil
  for outil in ffmpeg ffprobe compare convert identify; do
    command -v "$outil" >/dev/null 2>&1 || manquants+=("$outil")
  done
  [ "${#manquants[@]}" -eq 0 ] && return 0

  echo "::error::Outils absents : ${manquants[*]}. Une mesure impossible faute d'outil n'est pas un" >&2
  echo "::error::résultat : installer ffmpeg et imagemagick avant de comparer." >&2
  return 1
}


TOLERANCE_PAR_DEFAUT=5

### La dernière image d'un clip. `-update 1` réécrit le même fichier à chaque image : ce qui reste est
### la dernière, sans avoir eu à compter les images d'abord.
derniere_image() {
  ffmpeg -v error -y -i "$1" -vsync 0 -f image2 -update 1 "$2" 2>/dev/null
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
  local planchers="${5:-}"

  # Les planchers par cas, s'ils sont fournis : un écart se lit contre le bruit de SON cas (#4287).
  local -A sol nbp
  if [ -n "$planchers" ]; then
    # ⚠️ Un fichier ANNONCÉ mais absent ne se lit pas comme « aucun plancher connu » : c'est une
    # erreur de chemin, et sans ce message les cinquante cas diraient tous « plancher inconnu » sans
    # que personne ne cherche le fichier.
    if [ ! -f "$planchers" ]; then
      echo "::error::Fichier de planchers introuvable : « ${planchers} »."
      return 1
    fi
    local pn pv pc
    while IFS=$'\t' read -r pn pv pc; do
      case "$pn" in ''|\#*) continue ;; esac
      sol["$pn"]="$pv"
      nbp["$pn"]="${pc:-1}"
    done < "$planchers"
  fi
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
  local communs=0 apparus=0 disparus=0 bouges=0 illisibles=0 au_dessus=0
  local nom avant apres

  for nom in $noms; do
    avant="${avant_dir}/${nom}.mp4"
    apres="${apres_dir}/${nom}.mp4"

    if [ ! -f "$avant" ]; then
      printf '%s\t%s\t%s\t%s\n' "9000000" "$nom" "cas **apparu**" "pas d'avant à montrer" >> "$lignes"
      apparus=$((apparus + 1))
      continue
    fi
    if [ ! -f "$apres" ]; then
      printf '%s\t%s\t%s\t%s\n' "8000000" "$nom" "cas **disparu**" "plus de clip dans le tournage courant" >> "$lignes"
      disparus=$((disparus + 1))
      continue
    fi

    communs=$((communs + 1))
    local ia="${sortie}/${nom}.avant.png" ib="${sortie}/${nom}.apres.png"
    if ! derniere_image "$avant" "$ia" || ! derniere_image "$apres" "$ib"; then
      printf '%s\t%s\t%s\t%s\n' "7000000" "$nom" "⚠️ image finale illisible" "clip corrompu ?" >> "$lignes"
      illisibles=$((illisibles + 1))
      rm -f "$ia" "$ib"
      continue
    fi

    local part da db geste
    part=$(part_changee "$ia" "$ib" "$tolerance" 3)
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
      printf '%s\t%s\t%s\t%s\n' "6000000" "$nom" "⚠️ mesure impossible" "${da} s → ${db} s · ${geste}" >> "$lignes"
      illisibles=$((illisibles + 1))
      continue
    fi
    # ⚠️ Le tri se fait sur le RAPPORT au bruit propre du cas quand on le connaît, et non sur l'écart
    # absolu. Mesuré : un cas à 1,799 % dont le plancher vaut 0,809 % bouge deux fois moins qu'un cas
    # à 1,561 % dont le plancher vaut 0,101 %. L'écart absolu les classait dans le mauvais ordre.
    local cellule cle sien
    sien="${sol[$nom]:-}"
    if [ -z "$planchers" ]; then
      cellule="${part} %"
      cle="$part"
    elif [ -z "$sien" ]; then
      # Un cas sans plancher connu se DIT : le prendre pour stable serait inventer une mesure.
      cellule="${part} % · ⚠️ plancher inconnu"
      cle="$part"
    else
      local rapport
      rapport=$(LC_ALL=C awk -v e="$part" -v p="$sien" \
        'BEGIN { d = (p + 0 > 0.001) ? p + 0 : 0.001; printf "%.1f", e / d }')
      cellule="${part} % · **×${rapport}** de son bruit (plancher ${sien} %, ${nbp[$nom]} paire(s))"
      cle="$rapport"
      awk -v r="$rapport" 'BEGIN { exit !(r + 0 > 1) }' 2>/dev/null && au_dessus=$((au_dessus + 1))
    fi
    printf '%s\t%s\t%s\t%s\n' "$cle" "$nom" "$cellule" "${da} s → ${db} s · ${geste}" >> "$lignes"
    # ⚠️ Le comptage se fait sur une comparaison NUMÉRIQUE, pas sur la chaîne : « 10.000 » est plus
    # grand que « 9.000 », et un tri de texte dirait l'inverse.
    awk -v p="$part" 'BEGIN { exit !(p + 0 > 0) }' 2>/dev/null && bouges=$((bouges + 1))
  done

  {
    echo "### Comparaison de deux tournages"
    echo
    echo "Tolérance de couleur : **${tolerance} %**. Le chiffre **trie**, il ne prouve pas :"
    echo "sous un mot changé on est à deux fois le plancher de bruit. C'est la carte \`.ou.png\` qui dit **où**."
    if [ -n "$planchers" ]; then
      echo
      echo "Les cas sont classés par leur **rapport au bruit de leur propre cas**, et non par leur écart"
      echo "absolu : un cas dont le plancher est haut doit bouger davantage pour dire quelque chose."
      echo "⚠️ Lire le nombre de paires : un plancher tiré d'une seule paire ne prouve pas la stabilité."
    fi
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
    if [ -n "$planchers" ]; then
      echo "_${communs} cas comparé(s), ${au_dessus} au-dessus de leur propre plancher, ${bouges} au-dessus"
      echo "de zéro, ${apparus} apparu(s), ${disparus} disparu(s), ${illisibles} mesure(s) impossible(s)._"
    else
      echo "_${communs} cas comparé(s), ${bouges} au-dessus de zéro, ${apparus} apparu(s), ${disparus} disparu(s), ${illisibles} mesure(s) impossible(s)._"
    fi
  } > "$index"

  rm -f "$lignes"
  if [ -n "$planchers" ]; then
    echo "${communs} cas comparé(s), ${au_dessus} au-dessus de leur plancher, ${bouges} qui bougent, ${apparus} apparu(s), ${disparus} disparu(s), ${illisibles} mesure(s) impossible(s)."
  else
    echo "${communs} cas comparé(s), ${bouges} qui bougent, ${apparus} apparu(s), ${disparus} disparu(s), ${illisibles} mesure(s) impossible(s)."
  fi
  return 0
}

### Remesure le plancher : deux tournages qu'on SAIT identiques.
###
### Avec un troisième argument, écrit le plancher PAR CAS dans un fichier et l'ACCUMULE : relancer sur
### une autre paire garde le PIRE plancher observé et compte une paire de plus.
###
### ⚠️ Le pire, et non la moyenne : un plancher qui sous-estime le bruit fabrique des faux positifs,
### c'est-à-dire exactement ce qu'on cherche à éviter.
###
### ⚠️ Le compte de paires est écrit parce qu'un plancher tiré d'UNE paire ne prouve rien. Un cas dont
### le plancher est ressorti à 0,000 % n'est pas stable : il l'était cette fois-là (#4287).
plancher() {
  local a="$1" b="$2" fichier="${3:-}" bac pire=0
  bac=$(mktemp -d)
  trap 'rm -rf "$bac"' RETURN

  local -A sol nbp
  if [ -n "$fichier" ] && [ -f "$fichier" ]; then
    local n v c
    while IFS=$'\t' read -r n v c; do
      case "$n" in ''|\#*) continue ;; esac
      sol["$n"]="$v"
      nbp["$n"]="${c:-1}"
    done < "$fichier"
  fi

  local nom
  for nom in $(cas_du_dossier "$a"); do
    [ -f "$b/$nom.mp4" ] || continue
    derniere_image "$a/$nom.mp4" "$bac/a.png" || continue
    derniere_image "$b/$nom.mp4" "$bac/b.png" || continue
    local brut fuzz
    brut=$(part_changee "$bac/a.png" "$bac/b.png" 0 3)
    fuzz=$(part_changee "$bac/a.png" "$bac/b.png" "$TOLERANCE_PAR_DEFAUT" 3)
    printf '%-56s brut %8s %%   tolérance %s %% : %8s %%\n' "$nom" "$brut" "$TOLERANCE_PAR_DEFAUT" "$fuzz"
    pire=$(awk -v p="$pire" -v f="$fuzz" 'BEGIN { print (f + 0 > p + 0) ? f : p }')

    if [ -n "$fichier" ]; then
      local vu="${sol[$nom]:-}" compte="${nbp[$nom]:-0}"
      if [ -n "$vu" ]; then
        sol["$nom"]=$(awk -v x="$vu" -v y="$fuzz" 'BEGIN { print (y + 0 > x + 0) ? y : x }')
      else
        sol["$nom"]="$fuzz"
      fi
      nbp["$nom"]=$((compte + 1))
    fi
  done

  if [ -n "$fichier" ]; then
    {
      echo "# Plancher de bruit PAR CAS, à ${TOLERANCE_PAR_DEFAUT} % de tolérance."
      echo "# Colonnes : cas, plancher en %, nombre de paires de tournages qui l'ont produit."
      echo "# ⚠️ Le PIRE plancher observé est gardé : sous-estimer le bruit fabrique des faux positifs."
      echo "# ⚠️ Un plancher tiré d'UNE seule paire ne prouve rien. Lire la troisième colonne."
      for nom in "${!sol[@]}"; do
        printf '%s\t%s\t%s\n' "$nom" "${sol[$nom]}" "${nbp[$nom]}"
      done | LC_ALL=C sort
    } > "$fichier"
    echo "Planchers écrits dans « ${fichier} » : ${#sol[@]} cas."
  fi

  echo
  echo "Plancher le plus haut à ${TOLERANCE_PAR_DEFAUT} % de tolérance : ${pire} %."
  echo "⚠️ Ce nombre ne fait PAS un seuil : le retenir pour tous aveuglerait les cas stables, qui sont"
  echo "la grande majorité. Un écart se lit contre le plancher de SON cas."
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

  # 7. Un OUTIL ABSENT, et c'est le cas qui compte le plus.
  #
  # ⚠️ Sans lui, la garde d'outils ne serait elle-même gardée par rien. Le défaut qu'elle ferme s'est
  # produit pour de vrai : `comparer-tournages.yml` n'installait pas ImageMagick, `compare` était
  # introuvable, et les cinquante cas d'un vrai tournage se rangeaient en « mesure impossible » sans
  # qu'une ligne ne dise pourquoi - le job restant vert (run 32640637929).
  #
  # Le chemin est reconstruit avec tout SAUF `compare` : c'est la seule façon d'éprouver l'absence
  # sans désinstaller quoi que ce soit sur la machine qui lance l'auto-test.
  mkdir -p "$bac/bin"
  for outil in bash basename mktemp rm mkdir cp mv cat find wc sort sed grep awk ffmpeg ffprobe convert identify; do
    chemin=$(command -v "$outil" 2>/dev/null) && ln -sf "$chemin" "$bac/bin/$outil"
  done
  sortie=$(PATH="$bac/bin" "$MOI" "$bac/a" "$bac/b" "$bac/sans-outil" 2>&1) && echecs=1
  verifie "un outil absent est une panne, pas une mesure" "Outils absents" "$sortie"
  verifie "et l'outil manquant est NOMMÉ" "compare" "$sortie"

  # 8. Le fichier de planchers : écrit, puis ACCUMULÉ en gardant le PIRE.
  #
  # ⚠️ La deuxième paire est volontairement BRUYANTE là où la première était muette. Deux paires
  # identiques ne prouveraient que le compteur ; il faut un écart qui monte pour prouver que c'est bien
  # le maximum qui est retenu, et non la dernière valeur vue.
  mkdir -p "$bac/p1a" "$bac/p1b" "$bac/p2a" "$bac/p2b"
  clip "$bac/p1a/stable.mp4" white
  cp "$bac/p1a/stable.mp4" "$bac/p1b/stable.mp4"
  clip "$bac/p2a/stable.mp4" white
  clip "$bac/p2b/stable.mp4" black

  "$MOI" --plancher "$bac/p1a" "$bac/p1b" "$bac/sols.tsv" >/dev/null 2>&1
  sortie=$(grep -v '^#' "$bac/sols.tsv")
  verifie "le plancher d'une paire muette vaut zéro, sur une paire" "stable	0.000	1" "$sortie"

  "$MOI" --plancher "$bac/p2a" "$bac/p2b" "$bac/sols.tsv" >/dev/null 2>&1
  sortie=$(grep -v '^#' "$bac/sols.tsv")
  verifie "une seconde paire bruyante ÉCRASE par le haut" "stable	100.000	2" "$sortie"

  # 9. Un écart se lit contre le plancher de son cas, et le compte le dit.
  #
  # Le plancher de « stable » vaut 100 % : même un écart de 100 % ne le dépasse pas. C'est le cas qui
  # distingue ce classement de l'ancien, où tout écart non nul « bougeait ».
  sortie=$("$MOI" "$bac/p2a" "$bac/p2b" "$bac/avec-sols" 5 "$bac/sols.tsv" 2>&1)
  verifie "un écart égal à son plancher ne le dépasse pas" "0 au-dessus de leur plancher" "$sortie"
  verifie "et le rapport au bruit propre est affiché" "de son bruit" "$(cat "$bac/avec-sols/index.md")"

  # 10. Un cas ABSENT du fichier de planchers se dit, au lieu d'être pris pour stable.
  mkdir -p "$bac/p3a" "$bac/p3b"
  clip "$bac/p3a/inconnu.mp4" white
  clip "$bac/p3b/inconnu.mp4" blue
  sortie=$("$MOI" "$bac/p3a" "$bac/p3b" "$bac/sans-sol" 5 "$bac/sols.tsv" 2>&1)
  verifie "un cas sans plancher connu est SIGNALÉ" "plancher inconnu" "$(cat "$bac/sans-sol/index.md")"

  # 11. Un fichier de planchers ANNONCÉ mais absent : une erreur de chemin, pas cinquante inconnues.
  sortie=$("$MOI" "$bac/p3a" "$bac/p3b" "$bac/sol-absent" 5 "$bac/pas-la.tsv" 2>&1) && echecs=1
  verifie "un fichier de planchers introuvable est une erreur" "Fichier de planchers introuvable" "$sortie"

  # 12. C'est bien la DERNIÈRE image qui est comparée, et non la première.
  #
  # ⚠️ Rien ne l'affirmait jusqu'ici. Le clip « virage » commence BLANC et finit NOIR ; le clip
  # « noir » est noir de bout en bout. Comparer les dernières images doit donc rendre 0 %, quand
  # comparer les premières rendrait 100 %. Un jour où quelqu'un échantillonnera le milieu du clip pour
  # « mieux voir la transition », ce cas dira ce qui se casse (ADR 4274).
  mkdir -p "$bac/d1" "$bac/d2"
  clip "$bac/blanc.mp4" white
  clip "$bac/noir.mp4" black
  ffmpeg -v error -y -i "$bac/blanc.mp4" -i "$bac/noir.mp4" \
    -filter_complex "[0:v][1:v]concat=n=2:v=1[v]" -map "[v]" \
    -c:v libx264 -pix_fmt yuv420p "$bac/d1/virage.mp4" 2>/dev/null
  cp "$bac/noir.mp4" "$bac/d2/virage.mp4"

  sortie=$("$MOI" "$bac/d1" "$bac/d2" "$bac/derniere" 2>&1)
  verifie "la dernière image est comparée, pas la première" "1 cas comparé(s), 0 qui bougent" "$sortie"

  if [ "$echecs" = 0 ]; then
    echo "Auto-test de la comparaison de deux tournages : OK (21 cas, dont la dernière image et les planchers par cas)."
  else
    echo "Auto-test de la comparaison de deux tournages : ÉCHEC."
  fi
  exit "$echecs"
fi

# ---------------------------------------------------------------------------------------------

if [ "${1:-}" = "--plancher" ]; then
  [ "$#" -ge 3 ] || { echo "usage : $(basename "$MOI") --plancher <dossier A> <dossier B> [fichier]" >&2; exit 2; }
  exige_ses_outils || exit 3
  plancher "$2" "$3" "${4:-}"
  exit 0
fi

if [ "$#" -lt 3 ]; then
  echo "usage : $(basename "$MOI") <dossier avant> <dossier après> <dossier de sortie> [tolérance %] [planchers]" >&2
  exit 2
fi
exige_ses_outils || exit 3
comparer "$1" "$2" "$3" "${4:-$TOLERANCE_PAR_DEFAUT}" "${5:-}"
