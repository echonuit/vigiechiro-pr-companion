#!/usr/bin/env bash
# Ce qu'un tournage a vraiment donné : combien de cas ont rougi (#4351).
#
# ## Le défaut qu'il ferme
#
# Le tournage lance ses tests avec `-Dmaven.test.failure.ignore=true`, et c'est délibéré : « on veut les
# CLIPS. Un cas qui rougit produit son clip comme les autres, et c'est même celui-là qu'on veut
# regarder. » L'oracle prend ensuite son verdict sur les cas INDEXÉS, pas sur leur succès.
#
# Conséquence mesurée sur le run 32696587259, tiré exprès avec un jeton révoqué : le scénario a rougi,
# le job est resté VERT, et le clip du cas échoué s'est versé sur la pré-version sans aucune marque. Le
# seul endroit où l'échec existait était une ligne de Maven dans un journal de trois mille.
#
# Ce script ne juge pas - il RAPPORTE, là où on regarde.
#
# ## Il lit le XML, jamais le `.txt`
#
# Les rapports `.txt` de surefire mentent sur les classes à `@Nested` : ils annoncent « Tests run: 0 »
# et rendent 0 alors que des cas ont tourné. Le XML porte les attributs `tests`, `failures`, `errors`
# et `skipped` par classe, et c'est la seule source qui ne se trompe pas.
#
# ## Ce qu'il ne fait pas
#
# Il ne fait jamais rougir le tournage : un run rouge sur un cas rouge reviendrait sur
# `failure.ignore`, dont les raisons tiennent. Il rend un texte, et l'appelant décide où le poser.
#
# Usage : ./.github/scripts/verdict-du-tournage.sh [dossier-des-rapports]
#         ./.github/scripts/verdict-du-tournage.sh --auto-test
set -uo pipefail

verdict() { # <dossier des rapports surefire>
    python3 - "$1" <<'PY'
import glob
import os
import sys
import xml.etree.ElementTree as ET

# La sortie de Python suit l'encodage de la CONSOLE, et sous Windows c'est cp1252, où le « ✓ » de la
# ligne de verdict n'existe pas. Le tournage `windows-latest` mourait donc sur un caractère
# d'ornement, et le rouge accusait le banc alors que les clips étaient bien là (#5195). Reconfigurer
# ici plutôt que de poser `PYTHONIOENCODING` sur l'étape : ce script est appelé de plusieurs
# endroits, et un remède porté par l'appelant s'oublie au prochain appel.
sys.stdout.reconfigure(encoding="utf-8")

dossier = sys.argv[1]
fichiers = sorted(glob.glob(os.path.join(dossier, "TEST-*.xml")))

if not fichiers:
    # « Aucun rapport » n'est PAS « aucun échec ». Un tournage dont les tests n'ont pas démarré
    # rendrait sinon le même texte qu'un tournage parfait, ce qui est exactement le faux vert que ce
    # script existe pour empêcher.
    print("⚠️ AUCUN rapport de test : impossible de dire ce que ce tournage a donné.")
    sys.exit(0)

total = rouges = sautes = 0
fautives = []
for chemin in fichiers:
    try:
        racine = ET.parse(chemin).getroot()
    except ET.ParseError:
        fautives.append(os.path.basename(chemin) + " (illisible)")
        continue
    tests = int(racine.get("tests") or 0)
    echecs = int(racine.get("failures") or 0) + int(racine.get("errors") or 0)
    total += tests
    rouges += echecs
    sautes += int(racine.get("skipped") or 0)
    if echecs:
        fautives.append(f"{racine.get('name')} : {echecs} sur {tests}")

if rouges == 0 and not fautives and sautes == 0:
    print(f"✓ {total} cas joués, aucun rouge.")
    raise SystemExit(0)

if rouges == 0 and not fautives:
    # Pas de ✓ : un cas SAUTÉ n'a rien montré. Depuis #4447 un scénario peut abandonner quand la
    # précondition de son geste manque - le compte n'a plus de nuit à rapatrier, par exemple - et
    # mener avec un ✓ ferait lire « tout a été montré » à qui s'arrête au premier signe.
    print(f"◻ {total} cas joués, aucun rouge, mais {sautes} SAUTÉ(S) : leur geste n'a pas eu lieu.")
    print()
    print("Un cas sauté n'est ni un succès ni un défaut : c'est un geste que le banc n'a pas pu")
    print("jouer, et dont le clip ne montre donc pas ce que le cas promet. Le journal du pas de")
    print("tournage en donne la raison, et l'index ne les compte pas comme couverts.")
    sys.exit(0)

print(f"⚠️ **{rouges} cas ont ROUGI** sur {total} joués ({sautes} sauté(s)).")
print()
for f in fautives:
    print(f"- {f}")
print()
print("Leurs clips sont versés comme les autres, et c'est voulu : un cas qui rougit est celui")
print("qu'on veut regarder. Mais ils ne montrent PAS ce que leur cas promet.")
PY
}

auto_test() {
    local bac total=0 echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' RETURN
    echo "AUTO-TEST"

    essai() { # <nom> <motif attendu> <contenu du rapport, ou vide>
        local nom="$1" motif="$2" contenu="${3:-}" obtenu
        rm -rf "${bac:?}/rapports" && mkdir -p "$bac/rapports"
        [ -n "$contenu" ] && printf '%s\n' "$contenu" > "$bac/rapports/TEST-essai.xml"
        obtenu=$(verdict "$bac/rapports")
        total=$((total + 1))
        if printf '%s' "$obtenu" | grep -qF "$motif"; then
            printf '  [OK   ] %-54s\n' "$nom"
        else
            printf '  [ÉCHEC] %-54s -> %s\n' "$nom" "$(printf '%s' "$obtenu" | head -1)"
            echecs=$((echecs + 1))
        fi
    }

    essai "un tournage tout vert le dit" "aucun rouge" \
'<testsuite name="fr.essai.Vert" tests="4" failures="0" errors="0" skipped="0"/>'

    essai "un échec est compté et nommé" "1 cas ont ROUGI" \
'<testsuite name="fr.essai.Rouge" tests="3" failures="1" errors="0" skipped="0"/>'

    # Une ERREUR n'est pas une défaillance pour surefire, et c'est ainsi qu'un cas qui explose au
    # montage - le banc qui refuse faute de jeton - passerait sous un compteur qui ne lirait que
    # `failures`.
    essai "une erreur compte autant qu'une défaillance" "1 cas ont ROUGI" \
'<testsuite name="fr.essai.Erreur" tests="2" failures="0" errors="1" skipped="0"/>'

    # Le cas du geste ABANDONNÉ (#4447). Un scénario dont la précondition manque saute au lieu de
    # rougir - le compte n'a plus de nuit à rapatrier, donc « connexion-longue » n'a pas eu lieu.
    # Mener avec un ✓ ferait lire « tout a été montré » à qui s'arrête au premier signe.
    essai "un cas sauté ne mène pas avec un ✓" "SAUTÉ(S)" \
'<testsuite name="fr.essai.Saute" tests="1" failures="0" errors="0" skipped="1"/>'

    # Et le contrôle de l'autre bord : sans saut, le ✓ revient.
    essai "sans saut, le tournage garde son ✓" "✓" \
'<testsuite name="fr.essai.ToutVert" tests="2" failures="0" errors="0" skipped="0"/>'

    # Le contrôle qui empêche ce script de rassurer sur du vide.
    essai "aucun rapport n'est pas aucun échec" "AUCUN rapport" ""

    essai "un rapport illisible se dit" "illisible" \
'<testsuite name="tronque" tests="1"'

    echo
    echo "${total} cas, dont 2 contrôles négatifs."
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

verdict "${1:-target/surefire-reports}"
