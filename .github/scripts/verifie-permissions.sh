#!/usr/bin/env bash
# Garde du moindre privilège (#3294, suite du lot 3 du chantier #2720).
#
# ## Ce qu'elle empêche
#
# #2739 a retiré les droits d'écriture du **plancher** de `release.yml` pour les déclarer job par job.
# Rien ne l'empêchait de se défaire : une PR qui remettrait `issues: write` au niveau du workflow
# passerait toutes les autres gardes, qui vérifient l'épinglage et les secrets, pas les permissions.
#
# Le défaut corrigé était précis : un plancher permissif dans un workflow à **plusieurs jobs** accorde
# ses droits à **tous**, y compris à ceux qui n'en ont pas l'usage. La matrice d'installeurs - qui
# compile et exécute jpackage sur trois systèmes, le job le plus exposé du dépôt - pouvait ainsi
# écrire des issues et des pull requests, alors qu'elle ne fait qu'un `gh release upload`.
#
# ## Pourquoi cette règle-là, et pas une liste figée
#
# Figer les droits attendus par job serait plus précis, et faux à la première évolution légitime :
# #2742 a dû ajouter `id-token` et `attestations` au job des installeurs pour les attestations de
# provenance. Une liste figée aurait rougi sur un ajout parfaitement voulu, et se serait fait élargir
# machinalement - c'est-à-dire vidée de son sens.
#
# La règle porte donc sur ce qui a réellement dérivé : **le plancher**.
#
# Un workflow **mono-job** garde le droit d'écrire son plancher : plancher et job y désignent la même
# chose, et l'exiger au job ne changerait rien à la sécurité tout en imposant une réécriture. Trois
# workflows sont dans ce cas (`adr-rapport`, `capture-vues`, `flatpak`), et chacun utilise réellement
# chacun de ses droits (`git push`, `gh pr create`, et un `checks: write` motivé en commentaire).
#
# Usage : ./.github/scripts/verifie-permissions.sh [--auto-test]
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="${PERMISSIONS_RACINE:-$(cd "$ICI/../.." && pwd)}"

# PyYAML est requis : un YAML de workflow ne se lit pas à la ligne sans se tromper (blocs, ancres,
# `on:` que YAML interprète en booléen). S'il manque, la garde ÉCHOUE bruyamment - une garde qui se
# saute quand son outillage manque est un faux vert de plus.
if ! python3 -c 'import yaml' 2>/dev/null; then
  echo "❌ PyYAML est absent : la garde des permissions ne peut pas lire les workflows."
  echo "   Installer avec « pip install pyyaml », ou « apt-get install python3-yaml »."
  exit 1
fi

# Analyse un dossier de workflows et rend une ligne par manquement.
analyser() {
  python3 - "$1" <<'FIN'
import glob
import sys

import yaml

dossier = sys.argv[1]
problemes = []
verifies = 0

for chemin in sorted(glob.glob(f'{dossier}/*.yml')):
    nom = chemin.split('/')[-1]
    with open(chemin, encoding='utf-8') as fichier:
        document = yaml.safe_load(fichier) or {}
    jobs = document.get('jobs') or {}
    if not jobs:
        continue
    verifies += 1
    plancher = document.get('permissions') or {}
    if isinstance(plancher, str):
        plancher = {'*': plancher}
    ecritures = sorted(cle for cle, valeur in plancher.items() if valeur == 'write')
    if ecritures and len(jobs) > 1:
        problemes.append(
            f"{nom} : plancher en écriture ({', '.join(ecritures)}) dans un workflow de "
            f"{len(jobs)} jobs — ces droits sont accordés à TOUS, y compris à "
            f"{', '.join(sorted(jobs))}"
        )

print(f'VERIFIES {verifies}')
for probleme in problemes:
    print(f'PROBLEME {probleme}')
FIN
}

if [ "${1:-}" = "--auto-test" ]; then
  echecs=0
  verifie() { # <attendu> <libellé>
    code=0
    PERMISSIONS_RACINE="$bac" "$0" >/dev/null 2>&1 || code=$?
    if [ "${code}" = "$1" ]; then
      echo "  ✔ $2"
    else
      echo "  ✘ $2 : attendu $1, obtenu ${code}"
      echecs=1
    fi
  }

  ecrire() { # <nom> <contenu>
    printf '%s\n' "$2" > "$bac/.github/workflows/$1"
  }

  monter() {
    rm -rf "$bac"
    mkdir -p "$bac/.github/workflows"
  }

  bac="$(mktemp -d)"
  trap 'rm -rf "$bac"' EXIT

  monter
  ecrire propre.yml 'permissions:
  contents: read
jobs:
  a:
    steps: []
  b:
    permissions:
      contents: write
    steps: []'
  verifie 0 "un plancher en lecture, l écriture déclarée au job, passe"

  monter
  ecrire fautif.yml 'permissions:
  contents: write
  issues: write
jobs:
  a:
    steps: []
  b:
    steps: []'
  verifie 1 "un plancher en écriture dans un workflow de deux jobs est refusé"

  # Contrôles NÉGATIFS : la règle doit rester étroite.
  monter
  ecrire mono.yml 'permissions:
  contents: write
  pull-requests: write
jobs:
  seul:
    steps: []'
  verifie 0 "un workflow MONO-JOB garde le droit d écrire son plancher"

  monter
  ecrire sans.yml 'jobs:
  a:
    steps: []
  b:
    steps: []'
  verifie 0 "un workflow sans plancher déclaré ne déclenche pas"

  monter
  ecrire lecture.yml 'permissions:
  contents: read
  actions: read
jobs:
  a:
    steps: []
  b:
    steps: []'
  verifie 0 "plusieurs droits en LECTURE ne déclenchent pas"

  if [ "${echecs}" = 0 ]; then
    echo "Auto-test de la garde permissions : OK"
  else
    echo "Auto-test de la garde permissions : ÉCHEC - les règles ne font plus ce qu'elles promettent."
  fi
  exit "${echecs}"
fi

sortie="$(analyser "$RACINE/.github/workflows")"
verifies="$(printf '%s\n' "$sortie" | sed -n 's/^VERIFIES //p')"
problemes="$(printf '%s\n' "$sortie" | sed -n 's/^PROBLEME //p')"

if [ -n "$problemes" ]; then
  echo "❌ Plancher de permissions trop large :"
  printf '%s\n' "$problemes" | sed 's/^/   /'
  echo
  echo "Retirer l'écriture du bloc « permissions: » du workflow, et la déclarer dans le seul job"
  echo "qui en a besoin. Un droit accordé au plancher l'est à tous les jobs, y compris ceux qui"
  echo "compilent et empaquettent."
  exit 1
fi

echo "Garde permissions : OK ($verifies workflow(s), aucun plancher en écriture hors mono-job)."
