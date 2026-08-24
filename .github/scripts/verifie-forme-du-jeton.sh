#!/usr/bin/env bash
# Un jeton Vigie-Chiro a une FORME, et les textes d'un tournage partent sans que rien la cherche.
# (#4327, chantier #4291.)
#
# ## La forme, mesurée et non déduite
#
# `vigiechiro/xin/auth.py:212-213`, dans le code de la plateforme :
#
#     new_token = ''.join(random.choice(string.ascii_uppercase + string.digits) for x in range(32))
#
# Un jeton est donc **exactement** trente-deux caractères pris dans `[A-Z0-9]`. `verifie-jeton.sh`
# écrit qu'un jeton est « une chaîne opaque, sans préfixe distinctif » : c'est vrai des CATALOGUES de
# fournisseurs, et faux de la forme. Les deux gardes ne cherchent d'ailleurs pas la même chose - lui
# le CONTEXTE dans le contenu versionné, celle-ci la FORME dans ce qui part en artefact.
#
# ## Le motif est resserré d'un cran, et la raison se calcule
#
# `[A-Z0-9]{32}` seul attraperait toute empreinte MD5 écrite en majuscules. On exige donc **au moins
# une lettre hors de `A-F`** : un jeton tiré sur trente-six symboles n'en manque qu'avec une
# probabilité de (16/36)^32, soit de l'ordre de 10^-11. Toute la famille hexadécimale disparaît sans
# que la détection y perde quoi que ce soit.
#
# Et la suite doit faire trente-deux caractères EXACTEMENT, bornes comprises : une empreinte SHA-256
# en majuscules en fait soixante-quatre, et on ne veut pas y découper un faux positif.
#
# ## ⚠️ IL NE PROTÈGE PAS L'IMAGE, ET IL LE DIT
#
# C'est la partie qui compte le plus. Un garde qui rougirait sur `tournage.log` en laissant passer un
# clip ferait croire le canal couvert alors que **le seul qui compte** - celui que le masquage de
# GitHub n'atteint pas, parce que le masquage ne couvre que les journaux - resterait ouvert.
#
# Il **nomme donc ce qu'il n'a pas lu** : combien de fichiers ont été écartés parce qu'ils ne sont pas
# du texte, et le rappel que ceux-là ne sont couverts par personne. Un garde muet sur sa propre portée
# est un faux vert avec des étapes en plus.
#
# ## Où il tourne, et pourquoi pas plus tard
#
# AVANT `Garder les clips en artefact`, dans le job qui filme. L'idée de le mettre dans le job qui
# verse paraît plus sûre - plus tard, plus près de la publication - et c'est l'inverse : sur un dépôt
# **public**, un artefact d'Actions se télécharge SANS AUTHENTIFICATION. Quand le job de versement le
# reprend, il est déjà dehors.
#
# ## Il n'imprime jamais ce qu'il trouve
#
# Le journal d'un dépôt public est public. Rendre un jeton pour prouver qu'on l'a vu le publierait une
# seconde fois, et plus lisiblement. Il en donne les quatre premiers caractères et sa longueur, ce qui
# suffit à le retrouver dans le fichier nommé.
#
# Usage : ./.github/scripts/verifie-forme-du-jeton.sh [--auto-test] [répertoire...]
set -uo pipefail

# Vrai (0) si le fichier est du texte. `-I` fait taire grep sur un binaire : c'est le comportement
# qu'on veut, mais il faut le DEMANDER, sinon un fichier à octets NUL rend un silence qu'on lirait
# comme « rien trouvé » (déjà vécu ailleurs dans ce dépôt).
est_du_texte() { # <fichier>
    # ⚠️ Un fichier VIDE n'est pas un binaire. `grep -qI .` n'y trouve aucune ligne et rend 1, ce qui
    # le rangeait parmi les écartés : le compte de ce que le garde NE COUVRE PAS s'en trouvait gonflé.
    # Mesuré sur un vrai artefact, où `index.lock` pèse zéro octet. Il errait du bon côté, mais un
    # nombre qu'on ne peut pas croire ne sert à rien.
    [ -s "$1" ] || return 0
    LC_ALL=C grep -qI . "$1" 2>/dev/null
}

# Les suites suspectes d'un fichier, une par ligne : « numéro de ligne <TAB> les 4 premiers ».
#
# ⚠️ awk et non `grep -oE` : il faut la longueur EXACTE de la suite, et `grep -o` découperait
# trente-deux caractères dans une suite de soixante-quatre.
suspectes() { # <fichier>
    LC_ALL=C awk '
        {
            ligne = $0
            n = length(ligne)
            debut = 0
            for (i = 1; i <= n + 1; i++) {
                c = substr(ligne, i, 1)
                estalnum = (i <= n && c ~ /[A-Za-z0-9]/)
                if (estalnum && debut == 0) {
                    debut = i
                } else if (!estalnum && debut != 0) {
                    suite = substr(ligne, debut, i - debut)
                    if (length(suite) == 32 && suite ~ /^[A-Z0-9]+$/ && suite ~ /[G-Z]/) {
                        print NR "\t" substr(suite, 1, 4)
                    }
                    debut = 0
                }
            }
        }
    ' "$1"
}

# Balaie un répertoire. Écrit son compte rendu sur la sortie standard.
# Rend 0 si rien de suspect, 1 sinon.
balayer() { # <répertoire...>
    local lus=0 ecartes=0 trouvailles=0 f
    local liste_ecartes=""
    while IFS= read -r f; do
        if est_du_texte "$f"; then
            lus=$((lus + 1))
            while IFS=$'\t' read -r numero tete; do
                [ -n "${numero}" ] || continue
                trouvailles=$((trouvailles + 1))
                echo "❌ ${f}:${numero} : suite de 32 caractères à la forme d'un jeton (${tete}…)"
            done < <(suspectes "$f")
        else
            ecartes=$((ecartes + 1))
            liste_ecartes="${liste_ecartes}   - ${f}"$'\n'
        fi
    done < <(find "$@" -type f 2>/dev/null | LC_ALL=C sort)

    echo
    echo "Textes lus : ${lus}."

    # ⚠️ Le passage qui empêche ce garde d'être un faux vert. Il ne dit pas « rien trouvé », il dit ce
    # qu'il a regardé ET ce qu'il n'a pas pu regarder.
    if [ "${ecartes}" -gt 0 ]; then
        echo "Fichiers ÉCARTÉS parce qu'ils ne sont pas du texte : ${ecartes}."
        printf '%s' "${liste_ecartes}"
        echo "   ⚠️ Ceux-là ne sont couverts par AUCUN garde. Une image porte ce qu'elle montre, et le"
        echo "      masquage de GitHub ne couvre que les journaux. Ce qui protège un clip est en amont :"
        echo "      le jeton n'entre pas par l'écran, et il est révoqué en fin de tournage (#4305)."
    else
        echo "Aucun fichier écarté : tout ce qui était là était du texte."
    fi

    if [ "${trouvailles}" -gt 0 ]; then
        echo
        echo "Un jeton Vigie-Chiro fait exactement 32 caractères de [A-Z0-9]. Si c'en est un :"
        echo "  1. le RÉVOQUER : curl -X POST -u '<le jeton>:' https://vigiechiro.herokuapp.com/api/v1/logout"
        echo "  2. en poser un frais : gh secret set VIGIECHIRO_TOKEN_TOURNAGE"
        return 1
    fi
    return 0
}

auto_test() {
    local total=0 echecs=0 racine
    racine=$(mktemp -d)
    echo "AUTO-TEST"

    essai() { # <attendu rouge|vert> <libellé>
        local attendu="$1" nom="$2" obtenu=vert
        balayer "${racine}" >/dev/null 2>&1 || obtenu=rouge
        total=$((total + 1))
        if [ "${obtenu}" = "${attendu}" ]; then
            printf '  [OK   ] %-64s -> %s\n' "${nom}" "${obtenu}"
        else
            printf '  [ÉCHEC] %-64s -> %s (attendu %s)\n' "${nom}" "${obtenu}" "${attendu}"
            echecs=$((echecs + 1))
        fi
    }

    # ⚠️ Les trois cas qui suivent lisent la SORTIE du balayage, capturée d'abord.
    #
    # Écrits `balayer ... | grep -q ...`, ils ne prouvaient rien : sous `pipefail`, le code d'un tube
    # est celui du membre le plus à gauche qui échoue, et `balayer` rend 1 dès qu'il trouve quelque
    # chose. Le grep ne décidait donc de rien. La case « il ne réimprime pas le jeton » passait au
    # vert sans jamais regarder, et celle du message rougissait alors que le message était juste.
    dit() { # <libellé> <ce qui doit apparaître> <mot si oui> <mot si non>
        local sortie
        sortie=$(balayer "${racine}" 2>/dev/null)
        total=$((total + 1))
        if printf '%s' "${sortie}" | grep -q "$2"; then
            printf '  [OK   ] %-64s -> %s\n' "$1" "$3"
        else
            printf '  [ÉCHEC] %-64s -> %s\n' "$1" "$4"
            echecs=$((echecs + 1))
        fi
    }

    tait() { # <libellé> <ce qui ne doit PAS apparaître> <mot si absent> <mot si présent>
        local sortie
        sortie=$(balayer "${racine}" 2>/dev/null)
        total=$((total + 1))
        if printf '%s' "${sortie}" | grep -q "$2"; then
            printf '  [ÉCHEC] %-64s -> %s\n' "$1" "$4"
            echecs=$((echecs + 1))
        else
            printf '  [OK   ] %-64s -> %s\n' "$1" "$3"
        fi
    }

    # Un jeton de forme exacte, tel que la plateforme les frappe.
    printf 'session ouverte, jeton=K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAU\n' > "${racine}/tournage.log"
    essai rouge "un jeton de forme exacte dans un journal"

    # Les contrôles négatifs : tout ce qui ressemble sans en être.
    rm -f "${racine}/tournage.log"
    {
        printf 'empreinte 9f2c1ab34de5678901234567890abcdef1234567890abcdef1234567890abcd\n'
        # ⚠️ EXACTEMENT 32 caractères, et rien que de l'hexadécimal : c'est le seul contrôle qui
        # exerce la règle « au moins une lettre hors de A-F ». Il en faisait 33 à l'écriture, et
        # la mutation qui retire cette règle ne faisait alors RIEN rougir.
        printf 'empreinte MD5 majuscule 9F2C1AB34DE5678901234567890ABCDE\n'
        printf 'identifiant mongo 5f2b8c1e9a3d7f4b2e6c1a09\n'
        printf 'sha-256 majuscule ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789\n'
        printf 'base64 aGVsbG8gd29ybGQgdGhpcyBpcyBub3QgYSB0b2tlbg==\n'
        printf 'trop court K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SA\n'
        printf 'trop long K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAUX\n'
    } > "${racine}/index.md"
    essai vert "empreintes, identifiants, base64 et longueurs voisines"

    # Un fichier vide se compte comme un texte lu, pas comme un binaire écarté.
    : > "${racine}/index.lock"
    dit "un fichier vide compte parmi les textes, pas parmi les écartés" \
        "Aucun fichier écarté" "compté" "ÉCARTÉ À TORT"
    rm -f "${racine}/index.lock"

    # ⚠️ LE contrôle de ce garde. Une image qui contient le motif dans ses octets doit le laisser
    # VERT : il ne sait pas lire une image, et prétendre le contraire serait pire que ne rien faire.
    printf '\211PNG\r\n\032\n\000\000K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAU\000\000' > "${racine}/clip.png"
    essai vert "une image portant le motif ne rougit pas : il ne sait pas la lire"

    # ... et il doit DIRE ce qu'il a écarté.
    dit "et il nomme le fichier qu il n a pas lu" \
        "Fichiers ÉCARTÉS parce qu'ils ne sont pas du texte : 1" "dit" "MUET"

    # Le message doit nommer le fichier et la ligne : c'est lui qu'on lira, pas ce script.
    rm -f "${racine}/index.md" "${racine}/clip.png"
    printf 'rien\nrien\njeton=K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAU\n' > "${racine}/tournage.log"
    dit "le message nomme le fichier ET la ligne" "tournage.log:3 :" "nommé" "vague"

    # Et il ne rend JAMAIS le jeton entier : le journal d'un dépôt public est public.
    tait "il ne réimprime pas le jeton qu il a trouvé" \
        "K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAU" "masqué" "PUBLIÉ"

    rm -rf "${racine}"
    echo
    echo "${total} cas, dont trois qui doivent rester VERTS et un qui vérifie que le garde avoue sa portée."
    if [ "${echecs}" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC (${echecs}) : ne pas se fier au verdict de ce script."
        return 1
    fi
    echo "Auto-test concluant."
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

if [ "$#" -eq 0 ]; then
    echo "Usage : $0 [--auto-test] <répertoire...>"
    exit 2
fi

balayer "$@"
etat=$?
if [ "${etat}" -ne 0 ]; then
    echo "::error::Forme de jeton Vigie-Chiro dans un fichier qui allait partir en artefact. Rien n est versé."
fi
exit "${etat}"
