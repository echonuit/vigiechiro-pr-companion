#!/usr/bin/env bash
#
# Garde de complétude des captures d'écran (issue #86).
#
# Vérifie, à partir du manifeste `captures.manifest`, que :
#   1. chaque vue FXML sous `src/main/**/view/*.fxml` est déclarée au manifeste ;
#   2. chaque vue déclarée existe réellement dans le code ;
#   3. chaque capture déclarée existe dans `.github/assets/`.
# Échoue (exit 1) au moindre manquement. Léger : aucune compilation ni rendu, juste des fichiers.
#
# Lancé en CI (Quality gate). Pour le mettre à jour : ajouter la nouvelle vue + ses captures au
# manifeste, et générer les PNG via `capture-screenshots.sh`.

set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
MANIFESTE="$ICI/captures.manifest"
SOURCES="$RACINE/src/main/java"
erreurs=0

# Vues déclarées au manifeste (partie avant le « : »), normalisées.
declarees="$(grep -vE '^[[:space:]]*(#|$)' "$MANIFESTE" | sed 's/[[:space:]]*:.*//' | sed 's/[[:space:]]//g')"

# 1. Chaque *.fxml sous **/view/ doit être déclaré.
while IFS= read -r fxml; do
  rel="${fxml#"$SOURCES"/}"
  if ! grep -qxF "$rel" <<< "$declarees"; then
    echo "❌ Vue sans capture déclarée au manifeste : $rel"
    erreurs=$((erreurs + 1))
  fi
done < <(find "$SOURCES" -path '*/view/*.fxml' | sort)

# 2 + 3. Chaque vue déclarée existe ; chaque capture déclarée existe.
nb_vues=0
while IFS= read -r ligne; do
  case "$ligne" in ''|\#*) continue ;; esac
  vue="$(sed 's/[[:space:]]*:.*//;s/[[:space:]]//g' <<< "$ligne")"
  captures="$(sed 's/^[^:]*://' <<< "$ligne")"
  nb_vues=$((nb_vues + 1))
  if [[ ! -f "$SOURCES/$vue" ]]; then
    echo "❌ Vue déclarée au manifeste mais absente du code : $vue"
    erreurs=$((erreurs + 1))
  fi
  nb_captures=0
  for png in $captures; do
    nb_captures=$((nb_captures + 1))
    if [[ ! -f "$ICI/$png" ]]; then
      echo "❌ Capture déclarée mais absente de .github/assets/ : $png (vue $vue)"
      erreurs=$((erreurs + 1))
    fi
  done
  if [[ $nb_captures -eq 0 ]]; then
    echo "❌ Vue sans aucune capture déclarée : $vue"
    erreurs=$((erreurs + 1))
  fi
done < "$MANIFESTE"

if [[ $erreurs -gt 0 ]]; then
  echo "Garde captures : $erreurs problème(s) — voir ci-dessus."
  exit 1
fi
echo "Garde captures : OK ($nb_vues vues couvertes, toutes avec au moins une capture présente)."
