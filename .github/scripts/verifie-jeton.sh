#!/usr/bin/env bash
# Garde « jeton VigieChiro en clair » (#2741, lot 3 du chantier #2720).
#
# ## Pourquoi une garde maison, alors que GitHub scanne les secrets
#
# Le scan de secrets et la protection au push sont activés sur le dépôt, mais ils ne reconnaissent que
# les **motifs de fournisseurs** : clés AWS, jetons GitHub, clés Stripe. Or le secret que ce dépôt
# risque de laisser fuir est un **jeton VigieChiro**, lu dans `localStorage['auth-session-token']` du
# site : une chaîne opaque, sans préfixe distinctif, qu'aucun catalogue de fournisseur ne connaît. Les
# motifs personnalisés, qui l'attraperaient, demandent GitHub Advanced Security - indisponible sur le
# plan de l'organisation (vérifié : `advanced_security_enabled` vaut `false`).
#
# ## Ce qu'elle cherche, et pourquoi ce n'est pas la forme du jeton
#
# La forme est indistinguable : une chaîne alphanumérique quelconque. Un détecteur par entropie
# hurlerait sur les empreintes SHA-256 que ce dépôt contient en clair partout (manifestes de
# sauvegarde, fixtures de recette).
#
# C'est donc le **contexte** qui est cherché, et il est stable : le nom de la propriété ou de la clé,
# suivi d'une **affectation** et d'une **valeur littérale assez longue** pour ne pas être un
# marque-place.
#
# ## Elle porte sa propre preuve
#
# `--auto-test` fait passer neuf lignes connues - quatre fuites, cinq usages légitimes - par le MÊME
# motif que le balayage. Une garde qu'on n'a jamais vue rougir n'est pas une garde : celle-ci se le
# prouve à chaque exécution, et la CI lance les deux modes.
#
# Usage : ./.github/scripts/verifie-jeton.sh [--auto-test]
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"

# Nom de la clé, une affectation (`=`, `:` ou `>` pour le XML), puis 12 caractères ou plus de valeur
# littérale. 12 : plus long que tous les marque-places du dépôt, plus court que n'importe quel jeton.
MOTIF='(vigiechiro\.token|auth-session-token)["'"'"']?[[:space:]]*[=:>][[:space:]]*["'"'"']?[A-Za-z0-9_-]{12,}'

# Ce qui reste admis malgré la forme : un marque-place explicite, ou une référence à une variable.
TOLERE='XXXX|VIGIECHIRO_TOKEN|EXEMPLE|PLACEHOLDER|votre-jeton|secrets\.'

# Vrai (0) si la ligne ressemble à un jeton en clair.
suspecte() {
  printf '%s\n' "$1" | grep -qE "$MOTIF" && ! printf '%s\n' "$1" | grep -qE "$TOLERE"
}

autotest() {
  local echecs=0
  local fuites=(
    './mvnw -Papi-live test -Dvigiechiro.token=a1b2c3d4e5f6a7b8c9d0'
    'vigiechiro.token: 5f2b8c1e9a3d7f4b2e6c'
    '"auth-session-token": "9d8c7b6a5e4f3d2c1b0a"'
    '<vigiechiro.token>abcdef1234567890</vigiechiro.token>'
  )
  local legitimes=(
    './mvnw -Papi-live test -Dvigiechiro.token=XXXX'
    './mvnw -B -Papi-live test -Dvigiechiro.token="$VIGIECHIRO_TOKEN"'
    'VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}'
    '<vigiechiro.token></vigiechiro.token>'
    'System.setProperty("vigiechiro.token", token);'
  )
  for ligne in "${fuites[@]}"; do
    if ! suspecte "$ligne"; then
      echo "❌ autotest : fuite NON détectée -> $ligne"
      echecs=$((echecs + 1))
    fi
  done
  for ligne in "${legitimes[@]}"; do
    if suspecte "$ligne"; then
      echo "❌ autotest : faux positif -> $ligne"
      echecs=$((echecs + 1))
    fi
  done
  if [ "$echecs" -gt 0 ]; then
    echo "Autotest de la garde jeton : $echecs échec(s). Le motif ne fait plus ce qu'il promet."
    return 1
  fi
  echo "Autotest de la garde jeton : OK (${#fuites[@]} fuites détectées, ${#legitimes[@]} usages légitimes tolérés)."
}

if [ "${1:-}" = "--auto-test" ]; then
  autotest
  exit $?
fi

cd "$RACINE"

# `git grep` : le contenu VERSIONNÉ, celui que la CI reçoit et qui part chez tout le monde.
trouvailles=$(git grep -nIE "$MOTIF" -- ':!.github/scripts/verifie-jeton.sh' 2>/dev/null \
  | grep -vE "$TOLERE" || true)

if [ -n "$trouvailles" ]; then
  echo "❌ Jeton VigieChiro possiblement en clair dans un fichier versionné :"
  echo "$trouvailles" | sed 's/^/   /'
  echo
  echo "Un jeton VigieChiro vit 14 jours et donne accès aux données de son porteur."
  echo "Si c'en est un : le RÉVOQUER d'abord (il est déjà dans l'historique git), puis le retirer."
  echo "Si c'est un marque-place, l'écrire « XXXX » comme le reste de la documentation."
  exit 1
fi

echo "Garde jeton : OK (aucune valeur littérale affectée à vigiechiro.token ni auth-session-token)."
