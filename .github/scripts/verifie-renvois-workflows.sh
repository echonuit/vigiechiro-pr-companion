#!/usr/bin/env bash
# Garde des renvois entre workflows (#3287, lot 4 du chantier #2720).
#
# ## Ce qu'elle empêche
#
# Un workflow qui en attend un autre le désigne par son LIBELLÉ, pas par son fichier :
#
#     on:
#       workflow_run:
#         workflows: ["Aperçus des vues"]
#
# GitHub apparie par égalité STRICTE de chaîne. Un libellé qui ne correspond au `name:` d'aucun
# workflow n'arme rien, ne rougit pas, et ne produit AUCUN run : il n'y a littéralement rien à
# regarder pour s'apercevoir que le lien est mort.
#
# ## Le vécu
#
# #1439 renomme « Aperçus des vues (main) » en « Aperçus des vues ». La référence dans `docs.yml`
# reste sur l'ancien libellé. La republication de la documentation après régénération des captures
# cesse le 2026-07-14 et n'est remarquée que TROIS SEMAINES plus tard (#3279), non par la CI mais en
# lisant les deux fichiers côte à côte. Le déclencheur `push` étant par ailleurs bloqué par le
# `[skip ci]` des commits d'aperçus, le site n'était simplement plus reconstruit.
#
# Le commentaire de `docs.yml` disait pourtant déjà « Le nom doit suivre `name:` de capture-vues.yml ».
# La convention était écrite, connue, et elle s'est quand même perdue au premier renommage : c'est
# exactement le cas où ce dépôt met une garde plutôt qu'une phrase.
#
# ## Pourquoi la comparaison est stricte
#
# Ni casse relâchée, ni accents normalisés, ni espaces rognés. GitHub, lui, ne fait aucune de ces
# tolérances : une garde plus permissive passerait au vert sur des paires que la plateforme refuse
# d'apparier, ce qui est pire que pas de garde du tout.
#
# Usage : ./.github/scripts/verifie-renvois-workflows.sh [--auto-test]
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="${RENVOIS_RACINE:-$(cd "$ICI/../.." && pwd)}"

# PyYAML est requis : `on:` est lu par YAML comme le BOOLÉEN `True`, les libellés peuvent être en
# bloc ou en ligne, et une lecture à la ligne se tromperait. S'il manque, la garde ÉCHOUE bruyamment -
# une garde qui se saute quand son outillage manque est un faux vert de plus.
if ! python3 -c 'import yaml' 2>/dev/null; then
  echo "❌ PyYAML est absent : la garde des renvois ne peut pas lire les workflows."
  echo "   Installer avec « pip install --group gardes », qui lit pyproject.toml."
  exit 1
fi

analyser() {
  python3 - "$1" <<'FIN'
import glob
import sys

import yaml

dossier = sys.argv[1]
libelles = {}   # libellé -> fichier qui le porte
renvois = []    # (fichier citant, libellé cité)

for chemin in sorted(glob.glob(f'{dossier}/*.yml')):
    nom = chemin.split('/')[-1]
    with open(chemin, encoding='utf-8') as fichier:
        document = yaml.safe_load(fichier) or {}

    libelle = document.get('name')
    if isinstance(libelle, str):
        libelles[libelle] = nom

    # `on:` est lu par YAML comme le booléen True (norme YAML 1.1). On accepte les deux écritures
    # plutôt que d'imposer `"on":` aux auteurs.
    declencheurs = document.get('on', document.get(True)) or {}
    if not isinstance(declencheurs, dict):
        continue
    workflow_run = declencheurs.get('workflow_run') or {}
    if not isinstance(workflow_run, dict):
        continue
    for cite in workflow_run.get('workflows') or []:
        renvois.append((nom, cite))

print(f'RENVOIS {len(renvois)}')
for citant, cite in renvois:
    if cite not in libelles:
        connus = ', '.join(f'« {l} »' for l in sorted(libelles)) or '(aucun)'
        print(
            f'PROBLEME {citant} attend « {cite} », qui n\'est le name: d\'aucun workflow. '
            f'Libellés existants : {connus}'
        )
FIN
}

if [ "${1:-}" = "--auto-test" ]; then
  echecs=0
  # On exige le MESSAGE de la garde, pas seulement son code de sortie. Un `exit 1` peut venir d'un
  # PyYAML absent, d'une erreur de syntaxe Python ou d'un bac mal monté : ces rouges-là ne prouvent
  # rien de la règle. Vécu le même jour sur #3335, où une compilation cassée a servi de fausse preuve.
  # Le compte des cas et de ceux qui DOIVENT rougir (#3886).
  cas=0
  rouges=0
  verifie() { # <attendu> <libellé> [motif attendu dans la sortie]
    code=0
    cas=$((cas + 1))
    if [ "$1" != 0 ]; then rouges=$((rouges + 1)); fi
    sortie="$(RENVOIS_RACINE="$bac" "$0" 2>&1)" || code=$?
    if [ "${code}" != "$1" ]; then
      echo "  ✘ $2 : attendu $1, obtenu ${code}"
      echecs=1
      return
    fi
    motif="${3:-}"
    if [ -n "$motif" ] && ! grep -q "$motif" <<< "$sortie"; then
      echo "  ✘ $2 : code $1 obtenu, mais sans le message attendu (« $motif »)."
      echo "       La garde n'a pas parlé : ce rouge vient d'ailleurs."
      echecs=1
      return
    fi
    echo "  ✔ $2"
  }

  ecrire() { printf '%s\n' "$2" > "$bac/.github/workflows/$1"; }

  monter() { # un bac COHÉRENT : un producteur, un consommateur qui le nomme correctement
    rm -rf "$bac"
    mkdir -p "$bac/.github/workflows"
    ecrire producteur.yml 'name: Aperçus des vues
on:
  push: {}
jobs:
  a:
    runs-on: ubuntu-latest'
    ecrire consommateur.yml 'name: docs
on:
  workflow_run:
    workflows: ["Aperçus des vues"]
    types: [completed]
jobs:
  b:
    runs-on: ubuntu-latest'
  }

  bac="$(mktemp -d)"
  trap 'rm -rf "$bac"' EXIT

  # Contrôle NÉGATIF d'abord : sans lui, une garde qui refuse tout passerait tous les autres cas.
  monter
  verifie 0 "un renvoi qui désigne un libellé existant passe"

  monter
  ecrire producteur.yml 'name: Aperçus des vues (main)
on:
  push: {}
jobs:
  a:
    runs-on: ubuntu-latest'
  verifie 1 "un libellé renommé chez le producteur est refusé (le vécu de #3279)" "visant un libellé inexistant"

  monter
  ecrire consommateur.yml 'name: docs
on:
  workflow_run:
    workflows: ["aperçus des vues"]
jobs:
  b:
    runs-on: ubuntu-latest'
  verifie 1 "une différence de CASSE est refusée (GitHub ne la tolère pas)" "visant un libellé inexistant"

  monter
  ecrire consommateur.yml 'name: docs
on:
  workflow_run:
    workflows: ["Apercus des vues"]
jobs:
  b:
    runs-on: ubuntu-latest'
  verifie 1 "une différence d ACCENT est refusée" "visant un libellé inexistant"

  monter
  ecrire consommateur.yml 'name: docs
on:
  workflow_run:
    workflows: ["Aperçus des vues "]
jobs:
  b:
    runs-on: ubuntu-latest'
  verifie 1 "un espace en trop est refusé (comparaison stricte)" "visant un libellé inexistant"

  # Non-vacuité : un balayage qui ne trouve rien à examiner ne doit pas se déclarer conforme.
  monter
  ecrire consommateur.yml 'name: docs
on:
  push: {}
jobs:
  b:
    runs-on: ubuntu-latest'
  verifie 1 "aucun renvoi à examiner : la garde refuse au lieu de passer" "a rien examiné"

  # La tolérance reste étroite : un producteur en plus ne gêne pas.
  monter
  ecrire tiers.yml 'name: Quality gate
on:
  push: {}
jobs:
  c:
    runs-on: ubuntu-latest'
  verifie 0 "un workflow sans renvoi ne déclenche pas"

  if [ "${rouges}" -eq 1 ]; then verbe=DOIT; else verbe=DOIVENT; fi
  echo "${cas} cas, dont ${rouges} qui ${verbe} rougir."
  if [ "$echecs" = 0 ]; then
    echo "Auto-test de la garde des renvois : OK"
  else
    echo "Auto-test de la garde des renvois : ÉCHEC" >&2
  fi
  exit "$echecs"
fi

sortie="$(analyser "$RACINE/.github/workflows")"
renvois="$(sed -n 's/^RENVOIS //p' <<< "$sortie")"
problemes="$(grep '^PROBLEME ' <<< "$sortie" | sed 's/^PROBLEME //')"

# Non-vacuité : si plus aucun `workflow_run` n'est trouvé, ce n'est pas « tout va bien », c'est que la
# garde ne regarde plus rien. Le dépôt en a au moins un (docs.yml attend les aperçus).
if [ "${renvois:-0}" -eq 0 ]; then
  echo "❌ Aucun renvoi « workflow_run » trouvé sous .github/workflows/ : la garde n'a rien examiné."
  echo "   Soit les renvois ont disparu, soit le motif de détection a cessé de correspondre - dans"
  echo "   les deux cas, son vert ne mesurerait plus rien."
  exit 1
fi

if [ -n "$problemes" ]; then
  echo "❌ Renvoi(s) entre workflows visant un libellé inexistant :"
  printf '   %s\n' "$problemes"
  echo
  echo "   GitHub apparie par égalité STRICTE : un libellé introuvable n'arme rien et ne rougit pas."
  echo "   Corrigez le libellé cité, ou le « name: » du workflow attendu."
  exit 1
fi

echo "Garde des renvois : OK ($renvois renvoi(s), tous vers un libellé existant)."
