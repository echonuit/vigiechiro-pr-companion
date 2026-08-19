#!/usr/bin/env bash
# Garde-fou : aucun workflow n'appelle `apt-get` directement.
#
# ⚠️ Pourquoi. Trois étapes de trois workflows ont pendu le même jour sur la même ligne -
# `apt-get update` - jusqu'au butoir de leur job, y compris sur `main`. Le miroir du runner rendait
# « Ign: » sur toutes ses sources et APT attendait l'archive amont sans rien dire. Chacune portait un
# nom qui parlait d'autre chose : « Aligner la police système », « Installer de quoi afficher et
# filmer », « E2E CLI (bats) sur le fat-jar ».
#
# Le remède a été appliqué à UNE des trois, et les deux autres ont continué de pendre le lendemain.
# C'est le motif qu'on connaît : une leçon apprise à un seul endroit. D'où une porte unique,
# `installer-paquets.sh`, et cette garde pour qu'on ne la contourne pas.
#
# ⚠️ Ce que cette garde ne dit PAS : que la porte suffise. Elle borne et reprend ; un runner sans
# réseau échouera quand même - en une minute et en le disant, au lieu d'immobiliser une PR trois
# quarts d'heure.
#
# Exit 0 si aucun appel direct, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    cas=0
    rouges=0
    verifie() { # <attendu> <libellé>
        cas=$((cas + 1))
        [ "$1" != 0 ] && rouges=$((rouges + 1))
        APT_RACINE="$bac" bash "$ICI/$(basename "${BASH_SOURCE[0]}")" >/dev/null 2>&1
        code=$?
        if [ "$code" = "$1" ]; then
            printf '  [OK   ] %-52s -> %s\n' "$2" "$code"
        else
            printf '  [ÉCHEC] %-52s -> %s (attendu %s)\n' "$2" "$code" "$1"
            echecs=$((echecs + 1))
        fi
    }
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' EXIT
    mkdir -p "$bac/.github/workflows"

    echo "AUTO-TEST"
    printf 'jobs:\n  a:\n    steps:\n      - run: bash .github/scripts/installer-paquets.sh bats\n' \
        > "$bac/.github/workflows/bon.yml"
    verifie 0 "passer par la porte est accepté"

    printf 'jobs:\n  a:\n    steps:\n      - run: sudo apt-get install -y bats\n' \
        > "$bac/.github/workflows/nu.yml"
    verifie 1 "un apt-get direct est refusé"
    rm "$bac/.github/workflows/nu.yml"
    verifie 0 "le dépôt redevient conforme quand on le retire"

    # ⚠️ Un `apt-get` cité dans un COMMENTAIRE explique le défaut : l'interdire pousserait à ne plus
    # l'expliquer, ce qui est le contraire du but.
    printf 'jobs:\n  a:\n    # un apt-get nu pendait ici avant #4031\n    steps:\n      - run: echo ok\n' \
        > "$bac/.github/workflows/commente.yml"
    verifie 0 "un apt-get en COMMENTAIRE reste permis"

    rm -f "$bac/.github/workflows/"*.yml
    verifie 1 "sans aucun workflow, la garde REFUSE au lieu de passer"

    echo ""
    echo "$cas cas, dont $rouges qui DOIVENT rougir."
    [ "$echecs" -eq 0 ] && { echo "Auto-test concluant."; exit 0; }
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de cette garde."
    exit 1
fi

RACINE="${APT_RACINE:-$(cd "$ICI/../.." && pwd)}"
WORKFLOWS="$RACINE/.github/workflows"

if [ ! -d "$WORKFLOWS" ]; then
    echo "✗ $WORKFLOWS introuvable : rien ne peut être vérifié."
    exit 1
fi

fichiers=$(find "$WORKFLOWS" -maxdepth 1 -name '*.yml' | sort)
if [ -z "$fichiers" ]; then
    echo "✗ aucun workflow trouvé : la garde ne peut rien affirmer."
    exit 1
fi

# On retire la PROSE avant de chercher : commentaires et `name:` d'étape. Un `apt-get` cité là
# explique le défaut ou nomme la garde ; l'interdire pousserait à ne plus l'expliquer.
#
# ⚠️ Ce cas n'est pas théorique : la première version se refusait ELLE-MÊME, sur le nom de sa propre
# étape dans `lint.yml` - « Aucun workflow n appelle apt-get directement ». Un garde qui se compte
# parmi ses fautes, comme le compteur d'exigences ce matin.
fautes=$(printf '%s\n' "$fichiers" | while IFS= read -r f; do
    sed -e 's/#.*$//' -e 's/^[[:space:]]*-\{0,1\}[[:space:]]*name:.*$//' "$f" \
        | grep -n "apt-get" | sed "s#^#$(basename "$f"):#"
done)

if [ -n "$fautes" ]; then
    echo "✗ appel(s) direct(s) à apt-get dans un workflow :"
    printf '%s\n' "$fautes" | sed 's/^/   · /'
    echo ""
    echo "  Passer par .github/scripts/installer-paquets.sh : il borne les délais et reprend les"
    echo "  téléchargements coupés. Trois étapes ont pendu jusqu'au butoir de leur job, sur main"
    echo "  comme sur les PR, faute de cette porte."
    exit 1
fi

echo "✓ Aucun appel direct à apt-get : les $(printf '%s\n' "$fichiers" | wc -l) workflows passent par la porte."
