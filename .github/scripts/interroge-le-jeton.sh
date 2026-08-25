#!/usr/bin/env bash
# Un appel à la plateforme Vigie-Chiro, et son code HTTP. Rien d'autre. (#4385, EPIC #4386.)
#
# ## Pourquoi ce script existe
#
# Trois endroits du dépôt appelaient la plateforme et normalisaient son code, avec la même ligne
# recopiée : le contrôle du jeton du tournage, sa révocation, et le pas « Jeton valide ? »
# d'`api-live.yml`. Deux d'entre eux portaient même une fonction du même nom, écrites à trois heures
# d'intervalle.
#
# C'est ainsi que le défaut du « HTTP 000000 » s'est retrouvé aux trois : `curl -w '%{http_code}'`
# écrit DÉJÀ « 000 » quand la connexion échoue ET sort non nul, si bien que le `|| echo 000` habituel
# en ajoutait un second (#4328). Le classement restait juste, le nombre affiché non - et c'est le genre
# de ligne qui fait douter de tout le reste au moment où on la lit.
#
# ## Ce qu'il ne fait pas, et c'est le point
#
# **Il ne juge pas.** Il rend un code sur la sortie standard et sort 0, même sur `000`. Le verdict
# appartient à l'appelant, parce que les appelants n'ont pas le même :
#
#   api-live.yml           un jeton mort AVERTIT et le run reste vert : sans plateforme, la suite de
#                          contrat n'a rien à dire, et la faire rougir ferait lire une dérive de l'API
#                          à chaque expiration de secret.
#   verifie-jeton-vivant   un jeton mort REFUSE : filmer quand même donnerait un écran hors ligne
#                          parfaitement convaincant et muet sur son propre objet (ADR 4142).
#   revoque-jeton          un `401` est un SUCCÈS : le but n'était pas « le serveur a répondu 200 »
#                          mais « ce jeton ne sert plus à personne ».
#
# Trois lectures du même chiffre, dont deux opposées. Les faire converger « pour la cohérence »
# casserait l'une des trois en silence : c'est pourquoi seule la MESURE descend ici, et pas le verdict.
#
# ## Ce qu'il garantit
#
# - `-o /dev/null` : le corps ne nous apprend rien de plus que le code, et l'imprimer ferait passer la
#   réponse d'une erreur - ou l'identité du compte - dans un journal public.
# - `|| code=000`, jamais `|| echo 000`, pour la raison ci-dessus.
# - Une sortie vide vaut `000` : un code manquant n'est pas un code.
#
# ## Il s'éprouve sans réseau
#
# `--auto-test` le lance contre un **port mort de la boucle locale**. C'est le seul cas qui exerce
# l'APPEL et non un verdict, et c'est celui qui a démasqué le défaut d'origine (ADR 4331 : un garde
# exécute la règle qu'il juge).
#
# Usage : ./.github/scripts/interroge-le-jeton.sh [--auto-test]
#         JETON=<jeton> [VIGIECHIRO_URL=<base>] ./.github/scripts/interroge-le-jeton.sh [chemin] [méthode]
#
#   chemin   défaut `/moi`
#   méthode  défaut `GET`
set -uo pipefail

BASE="${VIGIECHIRO_URL:-https://vigiechiro.herokuapp.com/api/v1}"

interroger() { # <chemin> <méthode> -> code http
    local chemin="${1:-/moi}" methode="${2:-GET}" code
    code=$(curl -s -o /dev/null -w '%{http_code}' -X "${methode}" -u "${JETON:-}:" "${BASE}${chemin}") \
        || code=000
    [ -n "${code}" ] || code=000
    echo "${code}"
}

auto_test() {
    local total=0 echecs=0 obtenu

    echo "AUTO-TEST"

    essai() { # <libellé> <chemin> <méthode>
        total=$((total + 1))
        obtenu=$(JETON=zzz BASE=http://127.0.0.1:1 interroger "$2" "$3")
        if [ "${obtenu}" = "000" ]; then
            printf '  [OK   ] %-58s -> %s\n' "$1" "${obtenu}"
        else
            printf '  [ÉCHEC] %-58s -> %s (attendu 000)\n' "$1" "${obtenu}"
            echecs=$((echecs + 1))
        fi
    }

    # Le cas qui a démasqué le défaut d'origine : un seul « 000 », pas deux collés.
    essai "une lecture qui ne répond pas rend un code, et un seul" /moi GET
    # La même chose sur l'écriture : la révocation passe par un POST, et rien ne garantissait que la
    # normalisation valait pour les deux tant qu'elles vivaient dans deux copies.
    essai "une écriture qui ne répond pas rend un code, et un seul" /logout POST
    # Et le défaut se voit aussi sur un chemin qui n'existe pas : ce n'est pas la route qui normalise.
    essai "un chemin quelconque ne change rien à la normalisation" /nimporte GET

    # Le cas qui garde la promesse centrale : elle rend un code, elle ne le JUGE pas. Sans lui, une
    # sonde devenue jugeante ne faisait rougir personne - et le `run:` d'`api-live.yml` tourne sous
    # `bash -e`, donc son pas ÉCHOUERAIT sur un jeton expiré, ce que son tri refuse explicitement.
    total=$((total + 1))
    JETON=zzz VIGIECHIRO_URL=http://127.0.0.1:1 interroger /moi GET >/dev/null 2>&1
    if [ "$?" -eq 0 ]; then
        printf '  [OK   ] %-58s -> %s\n' "elle rend 0 même quand la plateforme ne répond pas" "ne juge pas"
    else
        printf '  [ÉCHEC] %-58s -> %s\n' "elle rend 0 même quand la plateforme ne répond pas" "ELLE JUGE"
        echecs=$((echecs + 1))
    fi

    echo
    echo "${total} cas, tous contre un port mort : aucun réseau, et le défaut du « 000000 » rougit."
    if [ "${echecs}" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC (${echecs}) : ne pas se fier au code que ce script rend."
        return 1
    fi
    echo "Auto-test concluant."
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

interroger "${1:-/moi}" "${2:-GET}"
