#!/usr/bin/env bash
# Révocation du jeton d'un tournage connecté (#4305, lot 3 du chantier #4291).
#
# ## Pourquoi révoquer, alors que le jeton expire tout seul
#
# Un jeton Vigie-Chiro vit **quatorze jours**. Le tournage connecté, lui, produit une **image**, et une
# image ne se masque pas : le masquage de GitHub ne couvre que les journaux. À terme ces clips seront
# publiés, et une fuite sur le tag d'une version est définitive.
#
# Révoquer à la fin du run ramène donc la fenêtre d'exposition de quatorze jours à la durée d'un
# tournage. C'est la dernière barrière, celle qui tient encore quand les deux premières - le jeton
# n'entre pas par l'écran, le secret ne dépasse pas son pas - ont manqué.
#
# ## Ce que la plateforme permet, et à quel prix
#
# `POST /logout`, authentifié par le jeton lui-même, fait un `$unset` de **ce jeton et de lui seul**
# (`vigiechiro/xin/auth.py:187-199`). Un compte porte une **carte** de jetons : ni le navigateur de qui
# a posé le secret, ni le jeton du contrat hebdomadaire ne sont atteints. C'est cette propriété, et
# elle seule, qui rend possible un second secret sans rien coûter à `api-live.yml`.
#
# ## La règle qu'on inverse sans s'en apercevoir
#
# **`404` et `401` sont des SUCCÈS.** L'objectif n'est pas « le serveur a répondu 200 » mais « ce
# jeton ne sert plus à personne » :
#
#   200  le jeton a ete retire de la carte du compte
#   404  il n'y etait pas : deja mort, et le but est atteint
#   401  l'authentification a echoue : il ne valait deja plus rien
#   ***  on ne sait pas, et c'est le seul cas qui demande de parler
#
# C'est exactement le genre de règle qu'une relecture distraite retourne, d'où l'auto-test : le verdict
# se prononce dans une fonction, et cette fonction passe par des codes connus à chaque exécution.
#
# **Et `verifie-jeton-vivant.sh` lit la MÊME table à l'envers** (#4328). Chez lui, `401` est un
# refus : il demande « ce jeton sert-il encore ? » AVANT de filmer. Ici on demande « ne sert-il
# vraiment plus ? » APRÈS. Les deux scripts sont voisins, se ressemblent, et traitent le même code de
# façon opposée : les harmoniser « pour la cohérence » casserait l'un des deux en silence.
#
# ## Il ne fait JAMAIS rougir le run
#
# Le tournage, lui, a réussi. Un rouge ici ferait lire un échec pour une raison qui n'est pas celle
# qu'on regarde. Mais se taire laisserait croire à une révocation qui n'a pas eu lieu, et c'est le faux
# vert que ce dépôt s'interdit : le cas incertain sort donc un **avertissement** qui nomme le code
# obtenu et dit comment révoquer à la main.
#
# Usage : ./.github/scripts/revoque-jeton.sh [--auto-test]
#         JETON=<jeton> ./.github/scripts/revoque-jeton.sh
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="${VIGIECHIRO_URL:-https://vigiechiro.herokuapp.com/api/v1}"

# Le verdict, séparé de l'appel : c'est la partie qui porte la règle, donc la seule qu'on puisse
# éprouver sans réseau. Rend 0 quand le jeton est hors d'usage, 1 quand on ne sait pas.
verdict() { # <code http>
    case "$1" in
        200)
            echo "✓ Jeton révoqué : la plateforme l'a retiré de la carte du compte."
            return 0
            ;;
        401 | 404)
            echo "✓ Jeton déjà hors d'usage (HTTP $1) : il n'était plus dans la carte, ou n'était plus accepté."
            return 0
            ;;
        *)
            echo "⚠ Révocation INCERTAINE (HTTP $1) : la plateforme n'a pas confirmé le retrait."
            echo "  Le jeton peut être encore vivant, et il le restera jusqu'à ses quatorze jours."
            echo "  Le révoquer à la main :"
            echo "      curl -X POST -u '<le jeton>:' ${BASE}/logout"
            echo "  puis en poser un frais : gh secret set VIGIECHIRO_TOKEN_TOURNAGE"
            return 1
            ;;
    esac
}

# L'appel descend dans `interroge-le-jeton.sh` (#4385), partagé avec les deux autres appelants. Il ne
# juge rien, et c'est ce qui permet de le partager : ici un `401` vaut SUCCÈS - le jeton ne sert plus
# à personne, c'était le but - alors qu'il refuse le départ d'un tournage.
interroger() { # -> code http
    JETON="${JETON:-}" VIGIECHIRO_URL="${BASE}" "${ICI}/interroge-le-jeton.sh" /logout POST
}

auto_test() {
    local total=0 echecs=0 surs=0 obtenu
    echo "AUTO-TEST"

    essai() { # <code> <sur|incertain> <libellé>
        local code="$1" attendu="$2" nom="$3" obtenu=sur
        verdict "$code" >/dev/null 2>&1 || obtenu=incertain
        total=$((total + 1))
        [ "$attendu" = sur ] && surs=$((surs + 1))
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-58s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-58s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    essai 200 sur "200 : le serveur a retiré le jeton"
    # Les deux cas qui font tout l'intérêt de cet auto-test. Les lire comme des échecs ferait
    # avertir à chaque tournage dont le jeton avait déjà expiré, et on apprendrait à ignorer la ligne.
    essai 404 sur "404 : le jeton n'était plus dans la carte, donc mort"
    essai 401 sur "401 : le jeton n'était plus accepté, donc mort"
    # Et les contrôles négatifs, sans lesquels une fonction qui rendrait TOUJOURS 0 passerait.
    essai 500 incertain "500 : la plateforme a fauté, on ne sait pas"
    essai 000 incertain "000 : injoignable, on ne sait pas"
    essai 302 incertain "302 : réponse inattendue, on ne sait pas"


    # Le cas qui exerçait l'APPEL vit désormais chez `interroge-le-jeton.sh` (#4385). Celui-ci
    # vérifie seulement que le chemin d'appel de CE script y mène bien.
    total=$((total + 1))
    obtenu=$(JETON=zzz VIGIECHIRO_URL=http://127.0.0.1:1 "${ICI}/interroge-le-jeton.sh" /logout POST)
    if [ "${obtenu}" = "000" ]; then
        printf '  [OK   ] %-58s -> %s\n' "la sonde partagée rend bien un code, et un seul" "${obtenu}"
    else
        printf '  [ÉCHEC] %-58s -> %s (attendu 000)\n' "la sonde partagée rend bien un code, et un seul" "${obtenu}"
        echecs=$((echecs + 1))
    fi

    echo
    echo "${total} cas, dont ${surs} qui doivent conclure « hors d'usage »."
    if [ "$echecs" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC (${echecs}) : ne pas se fier au verdict de ce script."
        return 1
    fi
    echo "Auto-test concluant."
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

if [ -z "${JETON:-}" ]; then
    echo "Aucun jeton à révoquer : la variable JETON est vide."
    echo "Ce n'est pas une anomalie si le tournage n'était pas connecté."
    exit 0
fi

code=$(interroger)

verdict "${code}" | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

# Le verdict sort AUSSI en sortie de pas, parce que quelqu'un en dépend : le versement des clips
# connectés n'a lieu que si le retrait est CONFIRMÉ. Depuis #4324 le clip montre le jeton - un jeton
# mort n'est pas un secret, mais « il est mort » doit être un fait, pas un espoir.
if verdict "${code}" >/dev/null 2>&1; then
    echo "revoque=oui" >> "${GITHUB_OUTPUT:-/dev/null}"
else
    echo "revoque=incertain" >> "${GITHUB_OUTPUT:-/dev/null}"
fi

# Toujours 0. Le tournage a réussi ; un rouge ici ferait lire un échec pour une raison qui n'est pas
# celle qu'on regarde. L'incertitude est portée par l'avertissement ci-dessous, pas par le code de
# sortie.
if ! verdict "${code}" >/dev/null 2>&1; then
    echo "::warning::Révocation incertaine (HTTP ${code}) : révoquer le jeton à la main, cf. le résumé du run."
fi
exit 0
