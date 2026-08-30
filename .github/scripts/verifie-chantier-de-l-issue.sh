#!/usr/bin/env bash
#
# Une issue prise appartient à un chantier, et rien ne le tenait (#4649).
#
# La règle vit dans `ouvrir-une-issue` depuis #4644, c'est-à-dire dans ce qu'un agent lit s'il pense
# à le lire. C'est exactement ainsi qu'elle a été manquée : elle était déjà écrite à trois endroits
# le 27 août 2026, et trois sessions l'ont enfreinte le même jour.
#
# Usage : verifie-chantier-de-l-issue.sh "<corps de la PR>"
#         verifie-chantier-de-l-issue.sh --auto-test
set -euo pipefail

export LC_ALL=C

readonly SAS=4562

# Les issues que le corps déclare fermer. Seuls les mots-clés que la FORGE reconnaît comptent :
# `close`, `fix`, `resolve` et leurs flexions. « Ferme #N » ne ferme rien, et c'est le garde du
# corps de PR qui refuse cette forme-là.
issuesFermees() {
    printf '%s' "${1-}" \
        | grep -oiE '\b(close[sd]?|closing|fix(e[sd])?|fixing|resolve[sd]?|resolving)[[:space:]]+#[0-9]+' \
        | grep -oE '[0-9]+' \
        | sort -u \
        || true
}

# Le parent d'une issue, « AUCUN » s'il n'y en a pas. Injectable pour l'auto-test : sans cela ses
# cas exigeraient la forge, et un garde dont les cas ne tournent pas hors ligne ne se relance jamais.
parentDe() {
    local numero="$1"
    if [ -n "${CHANTIER_ISSUES_FICHIER-}" ]; then
        jq -r --arg n "${numero}" '.[$n] // "AUCUN"' "${CHANTIER_ISSUES_FICHIER}"
        return
    fi
    if ! command -v gh > /dev/null 2>&1; then
        echo "REFUS : « gh » est absent. Ce garde ne conclut pas sur ce qu'il n'a pas lu." >&2
        exit 2
    fi
    gh issue view "${numero}" --json parent -q '.parent.number // "AUCUN"' 2> /dev/null \
        || { echo "REFUS : la forge n'a pas répondu pour #${numero}." >&2; exit 2; }
}

juge() {
    local corps="$1" numeros fautives="" numero parent code

    numeros=$(issuesFermees "${corps}")
    if [ -z "${numeros}" ]; then
        echo "Cette demande de fusion ne ferme aucune issue : rien à juger."
        return 0
    fi

    for numero in ${numeros}; do
        code=0
        # L affectation est séparée de la déclaration : `local x=$(...)` rend toujours 0 et
        # avalerait le refus de la forge, qui deviendrait un vert.
        parent=$(parentDe "${numero}") || code=$?
        [ "${code}" != 0 ] && exit 2
        case "${parent}" in
            AUCUN) fautives="${fautives}  #${numero} : aucun chantier
" ;;
            "${SAS}") fautives="${fautives}  #${numero} : ne pend qu au sas #${SAS}, d ou rien ne se traite
" ;;
        esac
    done

    if [ -n "${fautives}" ]; then
        echo "Une issue prise appartient à un chantier, et celle-ci n en a pas."
        echo
        printf '%s' "${fautives}"
        echo
        echo "Ouvrez le chantier qui traite sa CAUSE s il n existe pas, puis :"
        echo "    gh issue edit <n> --parent <EPIC>"
        exit 1
    fi

    echo "Chaque issue fermée par cette demande appartient à un chantier."
}

if [ "${1-}" = "--auto-test" ]; then
    echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "${bac}"' EXIT

    # Les trois cas du 27 août 2026, reconstitués. Ils ne sont plus lisibles sur la forge : leurs
    # blocs ont été corrigés le 28, les issues sont closes, et leur `parent` d'aujourd'hui a été
    # posé par #4829. S'en servir en direct reviendrait à mesurer ce travail-ci.
    printf '{"4571":"AUCUN","4554":"AUCUN","4617":"AUCUN","4649":4643,"4795":4562}\n' > "${bac}/issues.json"

    cas=0
    rouges=0
    joue() { # <attendu: ok|rouge|refus> <libellé> <corps> [fichier-issues]
        local attendu="$1" libelle="$2" corps="$3" fic="${4:-${bac}/issues.json}"
        cas=$((cas + 1))
        [ "${attendu}" != ok ] && rouges=$((rouges + 1))
        local code=0
        CHANTIER_ISSUES_FICHIER="${fic}" "$0" "${corps}" > /dev/null 2>&1 || code=$?
        local obtenu=ok
        [ "${code}" = 1 ] && obtenu=rouge
        [ "${code}" = 2 ] && obtenu=refus
        if [ "${obtenu}" = "${attendu}" ]; then
            echo "  ✔ ${libelle}"
        else
            echo "  ✘ ${libelle} : attendu ${attendu}, obtenu ${obtenu}"
            echecs=1
        fi
    }

    # Les cas qui comptent : sans eux, tous les verts ne valent rien.
    joue rouge "une issue sans chantier est refusée" "$(printf 'Ce lot fait X.\n\nCloses #4571')"
    joue rouge "les trois blocs du 27 août, reconstitués" "Closes #4554"
    joue rouge "une issue qui ne pend qu au sas est refusée, rien ne s y traite" "Closes #4795"
    joue ok "une issue rattachée à un chantier passe" "Closes #4649"
    joue ok "une PR qui ne ferme rien n est pas jugée" "Un lot de l EPIC. Refs #4643"
    joue ok "un renvoi sans mot-clé ne compte pas" "Voir #4571 pour le detail."
    joue rouge "la casse du mot-clé ne sauve pas" "closes #4571"
    joue rouge "« Fixes » compte aussi" "Fixes #4617"
    joue rouge "une des deux fermetures suffit à refuser" "$(printf 'Closes #4649\nCloses #4571')"
    joue refus "une issue absente de la forge fait REFUSER, pas conclure" \
        "Closes #4571" "${bac}/nulle-part.json"

    echo
    echo "${cas} cas, dont ${rouges} qui DOIVENT refuser."
    [ "${echecs}" = 0 ] && echo "Auto-test concluant : le garde voit une issue sans chantier." \
        || echo "Auto-test EN ÉCHEC."
    exit "${echecs}"
fi

juge "${1-}"
