#!/usr/bin/env bash
# Le jeton du tournage connecté est-il encore vivant ? (#4328, chantier #4291.)
#
# ## Le défaut : « présent » et « valide » ne sont pas le même fait
#
# Le tournage connecté RÉVOQUE son jeton en fin de run (#4305). Le secret, lui, reste posé. Après un
# tournage, `VIGIECHIRO_TOKEN_TOURNAGE` a donc l'air parfaitement valide et ne vaut plus rien.
#
# Le pas de contrôle vérifiait que le secret était non vide, ce qu'il est. Le tournage partait, le banc
# déposait le jeton mort, la modale le soumettait, et la plateforme répondait `401`. Vingt minutes plus
# tard un scénario rougissait sur « le libellé d'identité est vide » : le symptôme à trois pas de la
# cause, et c'est ce qui coûte une demi-heure à quelqu'un.
#
# Vécu deux fois, dont une exprès : le run 32696587259.
#
# ## Trois verdicts, et pas deux
#
# C'est le fond de l'affaire. « Pas 200 » recouvre deux choses qu'il ne faut pas confondre :
#
#   200        le jeton vit. Le tournage part.
#   401        LE JETON EST MORT. En poser un frais, et la commande est donnée.
#   000 5xx *  LA PLATEFORME NE RÉPOND PAS. Poser un jeton frais n'y changerait rien.
#
# Les fondre referait, un cran plus loin, le défaut qu'on corrige : on irait frapper un jeton pendant
# une panne d'Heroku, et on chercherait pourquoi le neuf ne marche pas mieux que l'ancien.
#
# ## ⚠️ La même table que `revoque-jeton.sh`, lue à l'ENVERS
#
# Là-bas, `401` est un **succès** : le jeton ne vaut plus rien, c'était le but. Ici c'est un **refus**.
# Deux scripts voisins qui traitent le même code de façon opposée finissent par s'harmoniser un jour
# par erreur, « pour la cohérence ». Ils ne parlent pas du même moment : l'un demande « ce jeton
# sert-il encore ? » avant de filmer, l'autre « ne sert-il vraiment plus ? » après.
#
# ## Le refus est DUR, contrairement à `api-live.yml`
#
# Chez lui, un jeton mort avertit et le run reste vert : sans plateforme, la suite de contrat n'a rien
# à dire, et la faire rougir ferait lire une dérive de l'API à chaque expiration de secret.
#
# Ici, filmer quand même donnerait un écran hors ligne parfaitement convaincant et **muet sur son
# propre objet** (ADR 4142) : on le regarderait en croyant savoir. C'est le refus que le tournage porte
# déjà quand le secret est absent, étendu au cas où il est présent et mort.
#
# ## Il tourne aussi sous le bash de Windows
#
# `filmer` s'exécute sur le runner de la matrice. D'où `curl` et `[ ]`, et rien qui demande GNU.
#
# Usage : ./.github/scripts/verifie-jeton-vivant.sh [--auto-test]
#         JETON=<jeton> ./.github/scripts/verifie-jeton-vivant.sh
set -uo pipefail

BASE="${VIGIECHIRO_URL:-https://vigiechiro.herokuapp.com/api/v1}"

# Le verdict, séparé de l'appel : c'est la partie qui porte la règle, donc la seule qu'on puisse
# éprouver sans réseau. 0 le jeton vit, 1 il est mort, 2 la plateforme n'a pas répondu.
verdict() { # <code http>
    case "$1" in
        200)
            echo "✓ Jeton vivant : la plateforme le reconnaît. Le tournage parlera à la plateforme réelle, en lecture."
            return 0
            ;;
        401)
            echo "✗ LA PLATEFORME REFUSE CE JETON (HTTP 401) : il est expiré, ou révoqué par un tournage précédent."
            echo "  Le secret est bien posé - c'est sa VALEUR qui ne vaut plus rien, et rien ne le montrait."
            echo "  En poser un frais :"
            echo "      gh secret set VIGIECHIRO_TOKEN_TOURNAGE"
            echo "  (jeton Vigie-Chiro, quatorze jours, révoqué en fin de run : c'est normal de recommencer.)"
            return 1
            ;;
        *)
            echo "✗ LA PLATEFORME NE RÉPOND PAS (HTTP $1) : ce n'est PAS le jeton qui est en cause."
            echo "  Poser un jeton frais n'y changerait rien. Vérifier que ${BASE} répond, puis relancer."
            return 2
            ;;
    esac
}

# L'appel, séparé lui aussi : c'est la seule partie que l'auto-test puisse éprouver, en la lançant
# contre un port mort de la boucle locale. Rend le code HTTP sur la sortie standard.
#
# ⚠️ `-o /dev/null` : le corps ne nous apprend rien de plus que le code, et l'imprimer ferait passer la
# réponse d'une erreur - ou l'identité du compte - dans un journal public.
#
# ⚠️ `GET /moi` et rien d'autre : c'est une LECTURE, elle ne consomme pas le jeton et ne touche à
# aucune donnée. C'est déjà ce qu'`api-live.yml` interroge pour le même départage.
#
# ⚠️ `|| code=000` et surtout PAS `|| echo 000` : quand la connexion échoue, curl écrit DÉJÀ « 000 »
# et sort non nul. Les deux se concaténaient, et le message annonçait « HTTP 000000 ».
interroger() { # -> code http
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' -u "${JETON}:" "${BASE}/moi") || code=000
    [ -n "${code}" ] || code=000
    echo "${code}"
}

auto_test() {
    local total=0 echecs=0 obtenu
    echo "AUTO-TEST"

    essai() { # <code> <vivant|mort|muette> <libellé>
        local code="$1" attendu="$2" nom="$3" obtenu
        verdict "$code" >/dev/null 2>&1
        case "$?" in
            0) obtenu=vivant ;;
            1) obtenu=mort ;;
            *) obtenu=muette ;;
        esac
        total=$((total + 1))
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-62s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-62s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    essai 200 vivant "200 : la plateforme reconnaît le jeton"
    # ⚠️ Le cas de l'issue. Un contrôle qui ne regardait que la présence du secret le laissait passer.
    essai 401 mort "401 : jeton expiré ou révoqué par le tournage précédent"
    # ⚠️ Et les trois qui séparent les deux causes. Sans eux, un verdict qui rendrait « mort » pour tout
    # ce qui n'est pas 200 passerait, et on irait frapper un jeton pendant une panne de la plateforme.
    essai 000 muette "000 : injoignable, ce n'est pas le jeton"
    essai 503 muette "503 : la plateforme est en panne, ce n'est pas le jeton"
    essai 500 muette "500 : la plateforme a fauté, ce n'est pas le jeton"
    # Le contrôle négatif de l'autre bord : « refusé » ne vaut pas « n'importe quel refus ».
    essai 403 muette "403 : refus d'une autre nature, on ne conclut pas sur le jeton"


    # ⚠️ Le seul cas qui éprouve l'APPEL et non le verdict, et il a servi dès sa première exécution :
    # le message annonçait « HTTP 000000 » sur une plateforme injoignable. Un port mort de la boucle
    # locale suffit, et n'a besoin d'aucun réseau pour rougir.
    total=$((total + 1))
    obtenu=$(JETON=zzz BASE=http://127.0.0.1:1 interroger)
    if [ "${obtenu}" = "000" ]; then
        printf '  [OK   ] %-62s -> %s\n' "l appel rend un code et un seul quand rien ne répond" "${obtenu}"
    else
        printf '  [ÉCHEC] %-62s -> %s (attendu 000)\n' "l appel rend un code et un seul quand rien ne répond" "${obtenu}"
        echecs=$((echecs + 1))
    fi

    echo
    echo "${total} cas, dont quatre qui doivent conclure autre chose que « le jeton est mort »."
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

# ⚠️ L'absence du secret garde son message à elle : « le poser » et « en poser un frais » envoient au
# même endroit, mais ne racontent pas la même histoire à qui lit le journal.
if [ -z "${JETON:-}" ]; then
    echo "::error::Tournage connecté demandé, secret VIGIECHIRO_TOKEN_TOURNAGE absent. Le poser :"
    echo "::error::gh secret set VIGIECHIRO_TOKEN_TOURNAGE (jeton Vigie-Chiro, 14 jours, révoqué en fin de run)."
    exit 1
fi

code=$(interroger)

sortie=$(verdict "${code}")
etat=$?
echo "${sortie}"
echo "${sortie}" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

if [ "${etat}" -eq 1 ]; then
    echo "::error::La plateforme refuse le jeton du tournage (HTTP ${code}) : en poser un frais avec gh secret set VIGIECHIRO_TOKEN_TOURNAGE."
    exit 1
fi
if [ "${etat}" -ne 0 ]; then
    echo "::error::Plateforme injoignable (HTTP ${code}) : le tournage connecté ne peut pas filmer ce qu'il prétend montrer. Ce n'est pas le jeton."
    exit 1
fi
exit 0
