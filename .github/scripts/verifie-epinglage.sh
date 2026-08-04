#!/usr/bin/env bash
# Garde d'épinglage (#2737, lot 3 du chantier #2720).
#
# Refuse toute action ou tout conteneur désigné par un NOM déplaçable. Un tag - fût-il aussi précis
# que `v5.6.0` - peut être repointé sur un autre commit sans que rien ne bouge chez nous : ce qui
# s'exécute dans nos workflows changerait alors sans qu'aucun commit ne le dise.
#
# Sans cette garde, la prochaine action ajoutée le sera par tag et l'épinglage se déferait en
# silence - la forme même du défaut que #2737 corrige.
#
# Formes acceptées :
#   uses: <depot>/<action>@<sha 40 hex>          # <tag>   (le commentaire dit ce qu'on a cru épingler)
#   uses: docker://<image>@sha256:<64 hex>
#   uses: ./.github/actions/<locale>                        (action du dépôt : elle suit nos commits)
#
# Usage : ./.github/scripts/verifie-epinglage.sh
set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
cd "$RACINE"

problemes=0
while IFS= read -r trouve; do
  fichier="${trouve%%:*}"
  reste="${trouve#*:}"
  ligne="${reste%%:*}"
  contenu="${reste#*:}"

  # On ignore les commentaires : une ligne qui PARLE de `uses:` n'en est pas un.
  case "$(echo "$contenu" | sed 's/^[[:space:]]*//')" in
    '#'*) continue ;;
  esac

  if echo "$contenu" | grep -qE '@[0-9a-f]{40}([[:space:]]|$)'; then
    # Action figée sur un SHA : le commentaire de version est exigé, sinon plus personne ne saura
    # quelle version tourne (et Dependabot n'aurait rien à mettre à jour de lisible).
    if ! echo "$contenu" | grep -qE '@[0-9a-f]{40}[[:space:]]+#[[:space:]]*\S'; then
      echo "❌ $fichier:$ligne : SHA épinglé sans commentaire de version : ${contenu# }"
      problemes=$((problemes + 1))
    fi
    continue
  fi
  if echo "$contenu" | grep -qE '@sha256:[0-9a-f]{64}'; then
    continue
  fi
  if echo "$contenu" | grep -qE 'uses:[[:space:]]*\./'; then
    continue
  fi

  echo "❌ $fichier:$ligne : référence non figée : ${contenu# }"
  problemes=$((problemes + 1))
done < <(grep -rn 'uses:' .github/workflows/*.yml)

if [ "$problemes" -gt 0 ]; then
  echo
  echo "Garde épinglage : $problemes problème(s)."
  echo "Résoudre le tag en SHA :"
  echo "  gh api repos/<proprietaire>/<action>/git/ref/tags/<tag> --jq .object.sha"
  echo "(si l'objet est de type « tag », déréférencer : gh api repos/…/git/tags/<sha> --jq .object.sha)"
  echo "Puis écrire : uses: <proprietaire>/<action>@<sha>  # <tag>"
  exit 1
fi

total=$(grep -rc 'uses:' .github/workflows/*.yml | awk -F: '{s+=$2} END {print s}')
echo "Garde épinglage : OK ($total référence(s), toutes figées sur un SHA ou un digest)."
