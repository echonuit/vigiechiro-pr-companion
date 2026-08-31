#!/usr/bin/env bash
# Quels cas un tournage n'a pas rendus, et non pas combien (#5012).
#
# ## Le défaut qu'il ferme
#
# L'oracle du tournage comparait deux nombres : « 111 cas sur 116 : le tournage est incomplet ». Il
# refusait donc à raison, sans jamais dire QUOI corriger, et il fallait refaire son calcul à la main
# pour l'apprendre.
#
# Le compte attendu incluait par ailleurs les cas des scénarios `recette-connectee`, exclus du build
# sans jeton : un tournage non connecté se voyait demander un nombre que sa configuration lui
# interdisait d'atteindre. La release a cessé de verser ses clips Java du 26 au 31 août sans que rien
# ne le dise, la publication réussissant par ailleurs. La quatrième colonne de
# `sessions-a-filmer.tsv` porte désormais les cas qu'un tournage non connecté doit rendre, et ce
# script les confronte à l'index.
#
# ## Ce qu'il ne fait pas
#
# Il ne juge pas les clips : un cas peut être indexé par un clip vide, et c'est l'affaire du seuil de
# luminance et de `uneFenetreAParu`. Il répond à une seule question, celle qui manquait : lesquels.
#
# Sorties :
#   0, rien sur la sortie standard  : tous les cas attendus sont indexés
#   1, un cas par ligne             : ceux qui manquent
#   2                               : il n'a pas pu lire, et ne conclut pas
#
# Usage : ./.github/scripts/cas-manquants-du-tournage.sh <cas-attendus-separes-par-virgule> <index.md>
#         ./.github/scripts/cas-manquants-du-tournage.sh --auto-test
set -uo pipefail

export LC_ALL=C

# Les cas indexés, dédoublonnés. Un cas joué par deux tests porte deux lignes, et n'est pas manquant
# deux fois. La capture précède le comptage : sous `pipefail`, un `grep` en bout de tube ne dit rien
# du code de ce qui est à sa gauche, et un index illisible se lirait comme un index vide.
indexes() { # <index.md>
    local index="$1"
    grep -E '^\| S' "${index}" | cut -d'|' -f2 | tr -d ' ' | sort -u
}

manquants() { # <attendus-csv> <index.md>
    local attendus="$1" index="$2"
    if [ ! -s "${index}" ]; then
        echo "REFUS : « ${index} » est absent ou vide. Ce garde ne conclut pas sur ce qu'il n'a pas lu." >&2
        return 2
    fi
    # Un attendu VIDE est légitime : une session dont tous les cas sont connectés n'a rien à rendre
    # hors connexion. Comm sur un ensemble vide rendrait alors tout l'index comme manquant.
    if [ -z "${attendus}" ]; then
        return 0
    fi
    local absents
    absents=$(comm -23 <(printf '%s\n' "${attendus}" | tr ',' '\n' | sed '/^$/d' | sort -u) <(indexes "${index}"))
    if [ -n "${absents}" ]; then
        printf '%s\n' "${absents}"
        return 1
    fi
    return 0
}

auto_test() {
    local echecs=0 tmp
    tmp=$(mktemp -d)
    trap 'rm -rf "${tmp}"' RETURN

    printf '| S1-01 | x |\n| S1-02 | x |\n| S1-02 | y |\n' > "${tmp}/index.md"

    verifier() { # <intitulé> <attendu> <sortie voulue> <code voulu>
        local sortie code
        sortie=$(manquants "$2" "${tmp}/index.md" 2>/dev/null)
        code=$?
        if [ "${sortie}" = "$3" ] && [ "${code}" = "$4" ]; then
            echo "  ✔ $1"
        else
            echo "  ✗ $1 : code=${code} (voulu $4), sortie=« ${sortie} » (voulue « $3 »)"
            echecs=1
        fi
    }

    verifier "tout indexé : rien à dire, code 0" "S1-01,S1-02" "" 0
    verifier "un cas absent : il est NOMMÉ, code 1" "S1-01,S1-03" "S1-03" 1
    verifier "deux absents : les deux sont nommés" "S1-04,S1-05" "S1-04
S1-05" 1
    verifier "un cas indexé deux fois ne manque pas" "S1-02" "" 0
    verifier "aucun attendu : légitime, code 0" "" "" 0

    local sortie code
    sortie=$(manquants "S1-01" "${tmp}/absent.md" 2>/dev/null)
    code=$?
    if [ "${code}" = "2" ] && [ -z "${sortie}" ]; then
        echo "  ✔ index illisible : REFUSE au lieu de conclure, code 2"
    else
        echo "  ✗ index illisible : code=${code} (voulu 2)"
        echecs=1
    fi

    [ "${echecs}" = "0" ] && echo "Auto-test : tous les cas passent." || echo "Auto-test : ÉCHEC."
    return "${echecs}"
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

if [ "$#" -ne 2 ]; then
    echo "Usage : $0 <cas-attendus-separes-par-virgule> <index.md>" >&2
    exit 2
fi

manquants "$1" "$2"
