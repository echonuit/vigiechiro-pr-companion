#!/usr/bin/env bash
# Garde-fou : une entrée `type: boolean` ne se compare jamais à une CHAÎNE dans un `if:`.
#
# ⚠️ Pourquoi cette garde existe, et ce qu'elle a coûté. Une entrée `type: boolean` de
# `workflow_dispatch` arrive dans le contexte `inputs` comme un VRAI booléen. Les expressions GitHub
# comparent deux types différents en les cassant tous les deux en nombres : `true` vaut 1, et
# `'true'` n'est pas un nombre. Toute comparaison avec NaN est fausse.
#
#     inputs.drapeau == 'true'   ->  TOUJOURS faux
#     inputs.drapeau != 'true'   ->  TOUJOURS vrai
#
# Mesuré sur le run 32395513266 de `recette filmée`. Deux conséquences, et la seconde est la grave :
#
#   1. les étapes gardées par `== 'true'` ne s'exécutaient jamais - le remuxage des clips a été sauté
#      alors que le tournage venait de produire le film ;
#   2. le témoin « sans gestionnaire de fenêtres » installait quand même openbox, parce que l'étape
#      d'installation était gardée par `!= 'true'`. Ce témoin est la garde qui prouve que la
#      vérification du pointeur garde quelque chose : elle n'a donc jamais rien gardé elle-même.
#
# ⚠️ Ce qui a rendu le défaut invisible : l'interpolation dans un `run:` marche. `"${{ inputs.x }}"`
# rend le texte `true`, et le test shell se comporte comme attendu. Les étapes en `run:` faisaient
# donc ce qu'on croyait, pendant que les `if:` faisaient le contraire.
#
# La forme juste est le booléen nu : `if: ${{ inputs.drapeau }}` et `if: ${{ !inputs.drapeau }}`.
#
# Exit 0 si aucune comparaison fautive, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
FLUX="$RACINE/.github/workflows"

auto_test() {
    local bac total=0 echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' RETURN
    echo "AUTO-TEST"

    essai() { # <nom> <vert|rouge> <contenu du workflow>
        local nom="$1" attendu="$2" contenu="$3" obtenu=vert
        mkdir -p "$bac/.github/workflows"
        printf '%s\n' "$contenu" > "$bac/.github/workflows/essai.yml"
        FLUX="$bac/.github/workflows" verifier >/dev/null 2>&1 || obtenu=rouge
        total=$((total + 1))
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-52s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-52s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    essai "un booléen comparé à la chaîne « true » est refusé" rouge \
'on:
  workflow_dispatch:
    inputs:
      drapeau:
        type: boolean
        default: false
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - if: inputs.drapeau == '"'"'true'"'"'
        run: echo'

    essai "la forme niée est refusée aussi"                  rouge \
'on:
  workflow_dispatch:
    inputs:
      drapeau:
        type: boolean
        default: false
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - if: inputs.drapeau != '"'"'true'"'"'
        run: echo'

    essai "le booléen nu passe"                              vert \
'on:
  workflow_dispatch:
    inputs:
      drapeau:
        type: boolean
        default: false
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - if: ${{ inputs.drapeau }}
        run: echo'

    essai "sa négation passe"                                vert \
'on:
  workflow_dispatch:
    inputs:
      drapeau:
        type: boolean
        default: false
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - if: ${{ !inputs.drapeau }}
        run: echo'

    # ⚠️ Le cas qui empêche la garde de tout refuser : une entrée de type CHAÎNE se compare
    # légitimement à une chaîne. Sans lui, la garde interdirait une forme juste et se ferait
    # contourner plutôt que corriger.
    essai "une entrée de type chaîne se compare à une chaîne" vert \
'on:
  workflow_dispatch:
    inputs:
      mode:
        type: string
        default: rapide
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - if: inputs.mode == '"'"'rapide'"'"'
        run: echo'

    # ⚠️ Et celui qui garde l INTERPOLATION : dans un `run:`, comparer le texte à « true » est la
    # forme JUSTE, et la refuser reviendrait à casser ce qui marche.
    essai "l interpolation shell reste permise"              vert \
'on:
  workflow_dispatch:
    inputs:
      drapeau:
        type: boolean
        default: false
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - run: if [ "${{ inputs.drapeau }}" = "true" ]; then echo; fi'

    echo
    echo "$total cas, dont 2 qui DOIVENT rougir."
    if [ "$echecs" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de ce garde."
        return 1
    fi
    echo "Auto-test concluant."
}

verifier() {
    python3 - "$FLUX" <<'PY'
import os
import re
import sys

import yaml

flux = sys.argv[1]
fautives = []
total = 0

# `inputs.<nom>` comparé à une chaîne entre apostrophes, dans un `if:`.
COMPARAISON = re.compile(r"inputs\.([A-Za-z0-9_-]+)\s*[!=]=\s*'([^']*)'")

for fichier in sorted(os.listdir(flux)):
    if not fichier.endswith((".yml", ".yaml")):
        continue
    chemin = os.path.join(flux, fichier)
    with open(chemin, encoding="utf-8") as f:
        contenu = yaml.safe_load(f)
    if not isinstance(contenu, dict):
        continue
    # Les entrées déclarées booléennes de ce workflow.
    declenchements = contenu.get(True) or contenu.get("on") or {}
    booleennes = set()
    if isinstance(declenchements, dict):
        for declencheur in ("workflow_dispatch", "workflow_call"):
            bloc = declenchements.get(declencheur) or {}
            for nom, spec in ((bloc.get("inputs") or {})).items():
                if isinstance(spec, dict) and spec.get("type") == "boolean":
                    booleennes.add(nom)
    if not booleennes:
        continue

    with open(chemin, encoding="utf-8") as f:
        for numero, ligne in enumerate(f, start=1):
            nue = ligne.strip()
            # ⚠️ `- if:` autant que `if:`. Une étape peut porter sa condition sur la ligne du tiret,
            # et ne reconnaître que la seconde forme laissait passer la première - c'est l'auto-test
            # qui l'a dit, sur une fixture écrite dans le style le plus courant.
            if nue.startswith("- "):
                nue = nue[2:].strip()
            # L'interpolation dans un `run:` est la forme JUSTE : on ne regarde que les `if:`.
            if not nue.startswith("if:"):
                continue
            total += 1
            for nom, valeur in COMPARAISON.findall(nue):
                if nom in booleennes:
                    fautives.append(f"{fichier}:{numero} · inputs.{nom} comparé à « {valeur} »")

if fautives:
    print(f"✗ {len(fautives)} comparaison(s) d'une entrée booléenne à une chaîne :")
    for f in fautives:
        print(f"   · {f}")
    print()
    print("  Une entrée `type: boolean` arrive comme un VRAI booléen. La comparer à une chaîne casse")
    print("  les deux en nombres : `== 'true'` vaut TOUJOURS faux, `!= 'true'` TOUJOURS vrai.")
    print("  Écrire `if: ${{ inputs.drapeau }}` ou `if: ${{ !inputs.drapeau }}`.")
    sys.exit(1)

print(f"✓ Les {total} condition(s) sur entrée booléenne emploient le booléen, pas une chaîne.")
PY
}

case "${1:-}" in
    --auto-test) auto_test ;;
    *) verifier ;;
esac
