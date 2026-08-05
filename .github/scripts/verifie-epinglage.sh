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
# Elle vérifie AUSSI qu'une même action n'est pas épinglée sur deux SHA différents. Ce cas n'est pas
# théorique : pendant le lot 3, une PR Dependabot a monté `setup-java` de v5.6.0 à v5.7.0 en fusionnant
# ONZE MINUTES après un workflow qui venait d'être ajouté - sa liste de fichiers, calculée avant, ne
# pouvait pas le connaître. Le dépôt s'est retrouvé avec neuf occurrences en v5.7.0 et une en v5.6.0,
# et rien ne l'a signalé : ce contrôle-ci n'existait pas, et la divergence n'a été vue qu'à l'œil nu.
#
# Formes acceptées :
#   uses: <depot>/<action>@<sha 40 hex>          # <tag>   (le commentaire dit ce qu'on a cru épingler)
#   uses: docker://<image>@sha256:<64 hex>
#   uses: ./.github/actions/<locale>                        (action du dépôt : elle suit nos commits)
#
# ## Elle porte sa propre preuve
#
# `--autotest` fait passer huit lignes connues - quatre à refuser, quatre à accepter - par les MÊMES
# règles que le balayage, plus un cas de divergence de versions. Une garde qu'on n'a jamais vue rougir
# n'est pas une garde ; celle-ci se le prouve à chaque exécution, et la CI lance les deux modes.
#
# Usage : ./.github/scripts/verifie-epinglage.sh [--autotest]
set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
cd "$RACINE"

# Rend 0 si la ligne est acceptable, 1 sinon. Partagée par le balayage et l'autotest : les éprouver
# séparément laisserait l'autotest certifier une règle que le balayage n'applique pas.
ligne_acceptable() {
  local contenu="$1"
  case "$(echo "$contenu" | sed 's/^[[:space:]]*//')" in
    '#'*) return 0 ;;
  esac
  if echo "$contenu" | grep -qE '@[0-9a-f]{40}([[:space:]]|$)'; then
    echo "$contenu" | grep -qE '@[0-9a-f]{40}[[:space:]]+#[[:space:]]*\S'
    return $?
  fi
  echo "$contenu" | grep -qE '@sha256:[0-9a-f]{64}|uses:[[:space:]]*\./'
}

autotest() {
  local echecs=0 sha40='0123456789abcdef0123456789abcdef01234567'
  local a_refuser=(
    "      - uses: actions/checkout@v7"
    "      - uses: actions/setup-java@v5.7.0"
    "      - uses: actions/checkout@${sha40}"
    "        uses: docker://ghcr.io/flathub/flatpak-external-data-checker:latest"
  )
  local a_accepter=(
    "      - uses: actions/checkout@${sha40} # v7"
    "        uses: docker://ghcr.io/flathub/exemple@sha256:$(printf '0%.0s' $(seq 1 64))"
    "      - uses: ./.github/actions/locale"
    "      # uses: actions/checkout@v7 (mention en commentaire)"
  )
  for ligne in "${a_refuser[@]}"; do
    if ligne_acceptable "$ligne"; then
      echo "❌ autotest : référence non figée ACCEPTÉE -> $ligne"
      echecs=$((echecs + 1))
    fi
  done
  for ligne in "${a_accepter[@]}"; do
    if ! ligne_acceptable "$ligne"; then
      echo "❌ autotest : référence correcte REFUSÉE -> $ligne"
      echecs=$((echecs + 1))
    fi
  done
  if [ "$echecs" -gt 0 ]; then
    echo "Autotest de la garde épinglage : $echecs échec(s). Les règles ne font plus ce qu'elles promettent."
    return 1
  fi
  echo "Autotest de la garde épinglage : OK (${#a_refuser[@]} refusées, ${#a_accepter[@]} acceptées)."
}

if [ "${1:-}" = "--autotest" ]; then
  autotest
  exit $?
fi

problemes=0
while IFS= read -r trouve; do
  fichier="${trouve%%:*}"
  reste="${trouve#*:}"
  ligne="${reste%%:*}"
  contenu="${reste#*:}"

  ligne_acceptable "$contenu" && continue

  if echo "$contenu" | grep -qE '@[0-9a-f]{40}([[:space:]]|$)'; then
    # Action figée sur un SHA mais sans commentaire : sans lui, plus personne ne sait quelle version
    # tourne, et Dependabot n'a rien à mettre à jour de lisible.
    echo "❌ $fichier:$ligne : SHA épinglé sans commentaire de version : ${contenu# }"
  else
    echo "❌ $fichier:$ligne : référence non figée : ${contenu# }"
  fi
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

# Deux SHA pour une même action : presque toujours une montée de version incomplète.
divergences=$(grep -rhoE 'uses: [^@ ]+@[0-9a-f]{40}' .github/workflows/*.yml \
  | sed 's/uses: //' \
  | sort -u \
  | awk -F@ '{print $1}' \
  | uniq -d)

if [ -n "$divergences" ]; then
  echo "❌ Une même action est épinglée sur DEUX SHA différents :"
  for action in $divergences; do
    echo "   $action"
    grep -rnE "uses: ${action}@" .github/workflows/*.yml | sed 's/^/      /'
  done
  echo
  echo "Presque toujours une montée de version incomplète : un fichier ajouté pendant qu'une PR de"
  echo "mise à jour était ouverte n'a pas pu être repris par elle. Aligner sur la version la plus"
  echo "récente, ou dire en commentaire pourquoi la divergence est voulue."
  exit 1
fi

total=$(grep -rc 'uses:' .github/workflows/*.yml | awk -F: '{s+=$2} END {print s}')
distinctes=$(grep -rhoE 'uses: [^@ ]+@' .github/workflows/*.yml | sort -u | wc -l)
echo "Garde épinglage : OK ($total référence(s) figées, $distinctes action(s) distincte(s), aucune divergence de version)."
