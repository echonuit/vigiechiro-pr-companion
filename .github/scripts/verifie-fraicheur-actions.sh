#!/usr/bin/env bash
# Un épinglage cohérent peut être périmé (#3382).
#
# ## Ce que `verifie-epinglage.sh` ne dit pas
#
# Il garde la COHÉRENCE : tout est figé par SHA, aucune divergence de version entre deux emplacements.
# C'est une propriété du dépôt, vérifiable hors ligne, et elle reste vraie indéfiniment - y compris
# quand l'amont a pris une majeure d'avance. Un SHA figé reste figé.
#
# Mesuré au 2026-08-06 : `actions/attest-build-provenance` était épinglé sur `v3.0.0` (août 2025)
# quand l'amont en était à `v4.1.1` (juin 2026). Huit autres actions étaient à jour. Rien n'avait
# rougi, et Dependabot - actif, et proposant d'autres montées la semaine même - n'avait JAMAIS proposé
# celle-là. Le retard courait sur l'action qui signe la provenance des binaires livrés.
#
# ## Pourquoi une garde, alors que Dependabot existe pour ça
#
# Parce qu'on vient de constater qu'il peut se taire sans le dire. Une garde qui MESURE l'écart ne
# dépend pas de la bonne santé du mécanisme qui devrait le combler.
#
# ## Deux verdicts, pas un
#
#   - AVERTISSEMENT sur un retard de version dans la même majeure : l'amont publie pour des raisons
#     qui ne nous regardent pas, et un rouge à chaque release amont s'apprendrait vite à ignorer ;
#   - ROUGE sur un retard d'une MAJEURE entière : ce n'est plus du bruit de fond, et c'est le cas qui
#     a échappé à tout le monde pendant six mois.
#
# Deux modes :
#   --inventaire   : lit les workflows et rend `dépôt<TAB>sha<TAB>commentaire` (hors ligne)
#   (par défaut)   : lit `dépôt<TAB>tag épinglé<TAB>tag amont` sur STDIN et juge
#
# Usage : ./.github/scripts/verifie-fraicheur-actions.sh --inventaire [--racine <dir>]
#         ... | ./.github/scripts/verifie-fraicheur-actions.sh [--auto-test]
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"

### Rend une ligne `dépôt<TAB>sha<TAB>commentaire` par action épinglée par SHA.
###
### Les références non épinglées sont ignorées : les refuser est le travail de `verifie-epinglage.sh`,
### et deux gardes qui se disputent le même constat finissent par diverger.
inventorier() {
  local racine="$1"
  grep -rhoE 'uses: [a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+@[a-f0-9]{40}( # \S+)?' "$racine/.github/workflows" 2>/dev/null \
    | sed -E 's|uses: ([^@]+)@([a-f0-9]{40})( # (\S+))?|\1\t\2\t\4|' \
    | sort -u
}

# Un tag ne dit rien quand il ne bouge jamais : seuils sur l'ÂGE du commit épinglé face au HEAD amont.
# Calibrés sur une mesure du 2026-08-11, où le pire écart du dépôt était de 143 jours : la garde est
# donc muette sur un dépôt sain, et le cas qui lui avait échappé (608 jours) est rouge.
AGE_AVERTISSEMENT=180
AGE_ROUGE=365

### Vrai si la chaîne ressemble à une version (`v2`, `7.0.1`), faux pour `main`, `master`, `sans-tag`.
ressemble_a_une_version() {
  printf '%s' "$1" | grep -qE '^v?[0-9]'
}

### Juge la fraîcheur. Écrit son verdict, rend 0 (rien de grave) ou 1 (une majeure de retard, un âge
### excessif, ou une mesure qu'on n'a pas pu faire).
###
### Entrée : `dépôt<TAB>tag épinglé<TAB>tag amont<TAB>retard en jours<TAB>commentaire`
juger() {
  local inventaire="$1"

  if [ -z "$(printf '%s' "$inventaire" | tr -d '[:space:]')" ]; then
    echo "❌ Inventaire vide : aucune action examinée."
    echo "   Ce n'est pas « tout est à jour », c'est « la question n'a pas été posée »."
    echo "   Regarder l'extraction (--inventaire) et le droit de lecture sur l'API GitHub."
    return 1
  fi

  local rouges=0 avertis=0 ajour=0 lignes=0
  local sortie_rouge="" sortie_avertie=""

  while IFS=$'\t' read -r depot epingle amont retard commentaire; do
    [ -n "$depot" ] || continue
    lignes=$((lignes + 1))

    local motif_rouge="" motif_avert=""

    # ---- 1. Les tags, quand ils veulent dire quelque chose -------------------------------------
    if [ -z "$epingle" ] || [ "$epingle" = "?" ]; then
      # Notre SHA ne porte aucun tag. Deux cas très différents, que le COMMENTAIRE sépare.
      if [ -z "${commentaire:-}" ]; then
        # Sans commentaire, on ne peut pas distinguer « tag disparu » de « épinglage hors tag
        # assumé ». On ne tranche donc pas en faveur du rassurant : c'est le silence qu'on combat.
        motif_rouge="version indéterminée (aucun tag sur le SHA, aucun commentaire pour dire l'intention)"
      elif ressemble_a_une_version "$commentaire"; then
        # Le commentaire annonce une version : le tag a donc été déplacé ou supprimé en amont.
        # C'est en soi une nouvelle, et le motif d'origine de ce garde.
        motif_rouge="le commentaire annonce « $commentaire » mais le SHA ne porte plus aucun tag"
      fi
      # Sinon (`# main @ …`) : épinglage hors tag ASSUMÉ. Rien à dire côté tags, l'âge tranchera.
    elif [ -z "$amont" ] || [ "$amont" = "?" ]; then
      motif_rouge="version indéterminée en amont (épinglé « $epingle »)"
    elif [ "$epingle" != "$amont" ]; then
      local maj_e maj_a
      maj_e=$(printf '%s' "$epingle" | sed -E 's/^v?([0-9]+).*/\1/')
      maj_a=$(printf '%s' "$amont" | sed -E 's/^v?([0-9]+).*/\1/')
      if [ "$maj_e" != "$maj_a" ]; then
        motif_rouge="$epingle -> $amont (une MAJEURE de retard)"
      else
        motif_avert="$epingle -> $amont"
      fi
    fi

    # ---- 2. L'âge, qui voit ce que les tags cachent ---------------------------------------------
    # Le cas vécu : `winget-releaser` n'a qu'un tag `v2`, immobile depuis novembre 2024, pendant que
    # l'action installait un `komac` de mars 2026. Tag épinglé = tag amont = `v2` : AUCUN écart à
    # signaler, et vingt et un mois de retard réel. Un tag qui ne bouge jamais rend ce garde aveugle.
    if [ -n "${retard:-}" ] && printf '%s' "$retard" | grep -qE '^[0-9]+$'; then
      if [ "$retard" -ge "$AGE_ROUGE" ]; then
        motif_rouge="${motif_rouge:+$motif_rouge ; }commit épinglé vieux de $retard jours face au HEAD amont"
      elif [ "$retard" -ge "$AGE_AVERTISSEMENT" ]; then
        motif_avert="${motif_avert:+$motif_avert ; }commit épinglé vieux de $retard jours face au HEAD amont"
      fi
    elif [ -n "${retard:-}" ]; then
      motif_rouge="${motif_rouge:+$motif_rouge ; }âge indéterminé"
    fi

    if [ -n "$motif_rouge" ]; then
      sortie_rouge+="   $depot : $motif_rouge"$'\n'
      rouges=$((rouges + 1))
    elif [ -n "$motif_avert" ]; then
      sortie_avertie+="   $depot : $motif_avert"$'\n'
      avertis=$((avertis + 1))
    else
      ajour=$((ajour + 1))
    fi
  done <<< "$inventaire"

  if [ "$rouges" -gt 0 ]; then
    echo "❌ $rouges action(s) à regarder :"
    printf '%s' "$sortie_rouge"
  fi
  if [ "$avertis" -gt 0 ]; then
    echo "⚠️  $avertis action(s) en retard dans la même majeure (signalé, non bloquant) :"
    printf '%s' "$sortie_avertie"
  fi
  echo "Fraîcheur des épinglages : $lignes action(s) examinée(s), $ajour à jour, $avertis en retard mineur, $rouges bloquante(s)."

  [ "$rouges" -eq 0 ]
}

# ---------------------------------------------------------------------------------------------
# Autotest, hors ligne. Les cas rouges se vérifient sur leur MESSAGE : un `exit 1` peut venir du
# script lui-même, et ce dépôt s'est déjà fait prendre à lire un plantage comme une détection.
# ---------------------------------------------------------------------------------------------
autotest() {
  local echecs=0 sortie code

  verifier() {
    local nom="$1" inventaire="$2" attendu="$3" fragment="$4"
    sortie=$(juger "$inventaire")
    code=$?
    local obtenu=vert
    [ "$code" -ne 0 ] && obtenu=rouge
    if [ "$obtenu" != "$attendu" ]; then
      echo "❌ autotest « $nom » : attendu $attendu, obtenu $obtenu"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=$((echecs + 1))
      return
    fi
    if ! printf '%s' "$sortie" | grep -qF "$fragment"; then
      echo "❌ autotest « $nom » : $obtenu attendu et obtenu, mais le message ne dit pas « $fragment »"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=$((echecs + 1))
    fi
  }

  verifier "tout à jour" \
    "$(printf 'actions/checkout\tv7.0.1\tv7.0.1\nactions/setup-java\tv5.7.0\tv5.7.0')" \
    vert "2 action(s) examinée(s), 2 à jour"

  # Le cas réel du 2026-08-06, rejoué.
  verifier "une majeure de retard" \
    "$(printf 'actions/attest-build-provenance\tv3.0.0\tv4.1.1\nactions/checkout\tv7.0.1\tv7.0.1')" \
    rouge "une MAJEURE de retard"

  # Un retard mineur ne bloque pas : sinon la garde rougirait à chaque release amont, et on
  # apprendrait à ne plus la lire.
  verifier "retard mineur, non bloquant" \
    "$(printf 'actions/checkout\tv7.0.1\tv7.2.0')" \
    vert "non bloquant"

  # Une mesure ratée n'est pas un « à jour ».
  verifier "amont introuvable" \
    "$(printf 'actions/checkout\tv7.0.1\t?')" \
    rouge "version indéterminée"
  verifier "épinglé introuvable" \
    "$(printf 'actions/checkout\t\tv7.0.1')" \
    rouge "version indéterminée"

  verifier "inventaire vide" "" rouge "Inventaire vide"

  # ---- L'ÂGE : ce que les tags cachent (#2213) ------------------------------------------------
  # Le cas vécu, rejoué : tag épinglé = tag amont = `v2`, donc aucun écart de version, et pourtant
  # vingt et un mois de retard réel. C'est exactement le vert que ce garde rendait.
  verifier "un tag immobile masque un commit de 608 jours" \
    "$(printf 'vedantmgoyal9/winget-releaser\tv2\tv2\t608\tv2')" \
    rouge "vieux de 608 jours"

  verifier "âge au-dessus du seuil d avertissement, non bloquant" \
    "$(printf 'anchore/scan-action\tv7\tv7\t200\tv7')" \
    vert "non bloquant"

  # Contrôles NÉGATIFS : la règle doit rester étroite.
  verifier "un âge sous les seuils ne dit rien" \
    "$(printf 'actions/checkout\tv7\tv7\t143\tv7')" \
    vert "1 à jour"

  # Épinglage hors tag ASSUMÉ : le commentaire ne prétend pas être une version, l âge est frais.
  verifier "épinglage sur main, récent, accepté" \
    "$(printf 'vedantmgoyal9/winget-releaser\t?\tv2\t0\tmain @ 2026-07-28')" \
    vert "1 à jour"

  # Mais un commentaire qui ANNONCE une version que le SHA ne porte plus reste une nouvelle.
  verifier "le tag annoncé a disparu en amont" \
    "$(printf 'actions/checkout\t?\tv7\t3\tv7')" \
    rouge "ne porte plus aucun tag"

  # Et sans commentaire du tout, on ne conclut pas au rassurant.
  verifier "ni tag ni commentaire" \
    "$(printf 'actions/checkout\t?\tv7\t3\t')" \
    rouge "aucun commentaire pour dire"

  # Le mode inventaire, sur des workflows fabriqués : il voit les épinglages et ignore le reste.
  local bac
  bac=$(mktemp -d)
  mkdir -p "$bac/.github/workflows"
  {
    echo 'jobs:'
    echo '  a:'
    echo '    steps:'
    echo '      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7'
    echo '      - uses: actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v7.0.0'
    echo '      - uses: quelquun/action-flottante@v3'
  } > "$bac/.github/workflows/essai.yml"
  local inv
  inv=$(inventorier "$bac")
  local n
  n=$(printf '%s\n' "$inv" | grep -c . )
  if [ "$n" -ne 2 ]; then
    echo "❌ autotest « inventaire » : 2 épinglages attendus, $n vu(s)"
    printf '%s\n' "$inv" | sed 's/^/      /'
    echecs=$((echecs + 1))
  elif ! printf '%s' "$inv" | grep -q 'actions/setup-node.*v7\.0\.0'; then
    echo "❌ autotest « inventaire » : le commentaire de version n'est pas remonté"
    echecs=$((echecs + 1))
  fi
  if printf '%s' "$inv" | grep -q 'action-flottante'; then
    echo "❌ autotest « inventaire » : une référence NON épinglée a été inventoriée"
    echecs=$((echecs + 1))
  fi
  rm -rf "$bac"

  if [ "$echecs" -gt 0 ]; then
    echo "Autotest de la fraîcheur : $echecs échec(s)."
    return 1
  fi
  echo "Autotest de la fraîcheur : OK (13 cas, dont 7 rouges vérifiés sur leur message)."
}

# ---------------------------------------------------------------------------------------------

MODE=juger
while [ $# -gt 0 ]; do
  case "$1" in
    --auto-test) autotest; exit $? ;;
    --inventaire) MODE=inventaire ;;
    --racine) RACINE="$2"; shift ;;
    *) echo "option inconnue : $1" >&2; exit 2 ;;
  esac
  shift
done

if [ "$MODE" = inventaire ]; then
  inventorier "$RACINE"
  exit 0
fi

juger "$(cat)"
