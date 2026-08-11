#!/usr/bin/env bash
# Garde du secret de soumission winget (#2213, lot 5 de l'EPIC #2104).
#
# ## Ce qu'elle empêche
#
# `winget.yml` refusait de soumettre quand `WINGET_TOKEN` manquait, et sortait **en vert**, avec un
# simple `::notice::`. Ce choix était le bon tant que le workflow se déclenchait sur `release:
# released` : rougir à chaque publication aurait été du bruit, sur un canal qu'on savait inerte.
#
# Il a cessé de l'être quand le workflow est passé en `workflow_dispatch` **seul**. Un dispatch manuel
# est un geste délibéré : on le lance parce qu'on veut soumettre une version. Répondre « vert » à ce
# geste sans avoir rien soumis, c'est annoncer une publication qui n'a pas eu lieu - le seul type de
# défaut qui se présente sous la forme d'un succès.
#
# La garde rougit donc désormais. Le canal n'a rien à ignorer proprement : il n'est sollicité que
# lorsqu'on veut qu'il agisse.
#
# ## Pourquoi une garde, et pas un `if` inline
#
# Parce qu'une garde qui ne sait que réussir ne garde rien, et que celle-ci a vécu neuf mois en ne
# sachant que réussir. Sortie en script, elle répond à `--auto-test` comme les autres, et cet
# auto-test éprouve les **deux** directions : le secret manquant doit rougir, le secret présent doit
# passer. Une règle qui refuse tout serait aussi inutile qu'une règle qui accepte tout.
#
# ## Ce qu'elle ne vérifie pas
#
# Que le jeton soit **valide** (non révoqué, non expiré, portant bien le scope `public_repo`). Cela
# demanderait le réseau, et l'échec correspondant est de toute façon **bruyant** : `winget-releaser`
# rougit s'il ne peut pas pousser sur le fork. Tardivement, mais sans ambiguïté. Le silence n'était
# possible que sur l'absence, et c'est l'absence que cette garde couvre.
#
# Usage : WINGET_TOKEN=… ./.github/scripts/verifie-secret-winget.sh [--auto-test]
set -uo pipefail

# Chemin absolu : l'auto-test se réinvoque, et il doit pouvoir le faire depuis n'importe quel dossier.
MOI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

if [ "${1:-}" = "--auto-test" ]; then
  echecs=0
  verifie() { # <attendu> <libellé> <commande>
    code=0
    ( eval "$3" ) >/dev/null 2>&1 || code=$?
    if [ "${code}" = "$1" ]; then
      echo "  ✔ $2"
    else
      echo "  ✘ $2 : attendu $1, obtenu ${code}"
      echecs=1
    fi
  }

  # Le défaut corrigé : c'est exactement ce cas qui sortait en vert.
  verifie 1 "un secret ABSENT de l environnement est refusé" \
    "env -u WINGET_TOKEN '$MOI'"
  verifie 1 "un secret VIDE est refusé" \
    "WINGET_TOKEN= '$MOI'"
  verifie 1 "un secret réduit à des espaces est refusé" \
    "WINGET_TOKEN='   ' '$MOI'"

  # Contrôles NÉGATIFS : la règle doit rester étroite. Une garde qui refuse tout serait verte de
  # méfiance et bloquerait la publication qu'elle est censée protéger.
  verifie 0 "un secret présent passe" \
    "WINGET_TOKEN=ghp_unJetonDeTest '$MOI'"
  verifie 0 "un secret présent, d une autre forme, passe aussi" \
    "WINGET_TOKEN=github_pat_unAutreJeton '$MOI'"

  if [ "${echecs}" = 0 ]; then
    echo "Auto-test de la garde secret winget : OK"
  else
    echo "Auto-test de la garde secret winget : ÉCHEC - la règle ne fait plus ce qu'elle promet."
  fi
  exit "${echecs}"
fi

jeton="${WINGET_TOKEN:-}"

if [ -z "${jeton//[[:space:]]/}" ]; then
  echo "❌ WINGET_TOKEN est absent : aucune soumission ne peut partir vers winget-pkgs."
  echo
  echo "   Ce workflow ne se déclenche qu'à la main. L'avoir lancé veut dire qu'on attend une"
  echo "   soumission : sortir en vert sans rien soumettre annoncerait une publication qui n'a pas"
  echo "   eu lieu."
  echo
  echo "   Poser le secret (PAT « classic », scope public_repo, un fork echonuit/winget-pkgs) :"
  echo "     gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
  echo
  echo "   Détail des prérequis : l'en-tête de .github/workflows/winget.yml."
  exit 1
fi

echo "Garde secret winget : OK (WINGET_TOKEN présent)."
