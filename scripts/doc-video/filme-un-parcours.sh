#!/usr/bin/env bash
# Filme un parcours de DOCUMENTATION sur le produit livré (#3887, EPIC #3667).
#
# ## Ce que ce banc n'est pas
#
# Ce n'est pas `lance-test-filme.sh`. Celui-là filme des tests TestFX pour la RECETTE : l'assertion
# fait foi, la vidéo est la pièce jointe. Ici c'est l'inverse - ce qu'on VOIT est le livrable, et
# aucune assertion ne dira si un enchaînement « paraît naturel ».
#
# ## Ce qu'il pilote, et pourquoi pas le Flatpak
#
# Le **fat-jar** du dépôt, désigné par `VIGIECHIRO_JAR` - la convention que `src/test/bats` emploie
# déjà. Le corps de #3887 prescrivait le paquet installé ; remesuré, ce choix ne tenait pas :
#
#   | | Flatpak | fat-jar |
#   |---|---|---|
#   | lancements ayant mappé une fenêtre | 2 sur 5 | 3 sur 3 |
#   | version filmée | celle installée sur la machine | celle du dépôt qu'on documente |
#
# ⚠️ Ce que ce choix abandonne : les défauts propres à l'emballage (police absente du Flatpak, module
# oublié au jlink). Ils restent couverts par `verifie-demarrage-emballage.sh` et
# `verifie-affichage-flatpak.sh`, et par elles seules.
#
# Usage : filme-un-parcours.sh [--auto-test]
set -uo pipefail

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# La cadence du film. 12 images/s suffit à lire un enchaînement, et divise par deux le poids du
# fichier par rapport aux 25 d'usage : une vidéo de documentation se regarde, elle ne s'analyse pas
# image par image comme un clip de recette.
CADENCE=12

# La fenêtre du produit, telle que l'utilisateur l'ouvre.
LARGEUR=1280
HAUTEUR=860

# ---------------------------------------------------------------------------------------------
# Les conditions du lancement. Chacune se vérifie, et chacune sait DIRE ce qui manque : sans cela,
# un banc qui ne pilote rien rend un fichier vidéo parfaitement valide et vide (#3707).
# ---------------------------------------------------------------------------------------------

verifier_outils() {
    local manquants=()
    for outil in Xvfb xdotool ffmpeg xdpyinfo openbox; do
        command -v "$outil" >/dev/null 2>&1 || manquants+=("$outil")
    done
    if [ ${#manquants[@]} -gt 0 ]; then
        echo "   - outils absents : ${manquants[*]}"
        echo "     sudo apt-get install -y xvfb xdotool ffmpeg x11-utils openbox"
        return 1
    fi
    return 0
}

# ⚠️ openbox, et NON matchbox : matchbox MAXIMISE tout ce qu'il affiche (#3788). Une vidéo de
# documentation tournée sous matchbox montrerait une mise en page que personne ne verra jamais.
# On ne se contente pas de vérifier qu'un gestionnaire tourne : on vérifie qu'il HONORE la taille
# demandée, ce qu'un contrôle de présence ne dirait pas.
verifier_dimensions_honorees() {
    local ecran="$1" large
    large=$(DISPLAY="$ecran" xdotool search --onlyvisible --name "VigieChiro" getwindowgeometry %@ 2>/dev/null \
        | grep -oE "Geometry: [0-9]+x[0-9]+" | grep -oE "[0-9]+x" | tr -d x | head -1)
    if [ -z "$large" ]; then
        echo "   - impossible de lire la géométrie de la fenêtre"
        return 1
    fi
    if [ "$large" -gt $((LARGEUR + 40)) ]; then
        echo "   - la fenêtre fait ${large} px de large pour ${LARGEUR} demandés : le gestionnaire"
        echo "     de fenêtres la maximise. Le film montrerait une mise en page que personne ne voit."
        return 1
    fi
    return 0
}

# Le jar à filmer. `VIGIECHIRO_JAR` d'abord - la convention de `src/test/bats` - puis la cible Maven.
resoudre_le_jar() {
    if [ -n "${VIGIECHIRO_JAR:-}" ]; then
        printf '%s' "$VIGIECHIRO_JAR"
        return 0
    fi
    ls "$RACINE"/target/vigiechiro-*-shaded.jar 2>/dev/null | head -1
}

verifier_le_jar() {
    local jar="$1"
    if [ -z "$jar" ] || [ ! -f "$jar" ]; then
        echo "   - aucun fat-jar : posez VIGIECHIRO_JAR, ou lancez ./mvnw -DskipTests package"
        return 1
    fi
    return 0
}

# ⚠️ La condition NEUVE, que la recette filmée n'avait pas. Elle pilote une JVM de test ; celui-ci
# pilote le produit livré, qui écrit dans l'espace de travail RÉEL de l'utilisateur
# (`~/Documents/VigieChiro-Companion`). Filmer un parcours d'importation y toucherait de vraies
# nuits. `Workspace.resolu()` donne la priorité à la propriété système, ce qui permet de l'écarter
# sans toucher au produit.
verifier_bac_jetable() {
    local bac="$1"
    if [ -z "$bac" ]; then
        echo "   - aucun espace de travail jetable : le produit écrirait chez l'utilisateur"
        return 1
    fi
    case "$bac" in
        "$HOME"/Documents/*)
            echo "   - « $bac » est dans l'espace de travail réel : refusé"
            return 1
            ;;
    esac
    return 0
}

# ⚠️ La condition qui porte tout le reste. Un film sans fenêtre est un fichier valide et VIDE -
# c'est le faux vert que #3707 a corrigé pour la recette, et il se reproduirait ici sans ce contrôle.
attendre_la_fenetre() {
    local ecran="$1" secondes="${2:-40}" i
    for ((i = 0; i < secondes; i++)); do
        if DISPLAY="$ecran" xdotool search --onlyvisible --name "VigieChiro" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    echo "   - aucune fenêtre « VigieChiro » après ${secondes} s"
    return 1
}

# ---------------------------------------------------------------------------------------------
# Le tournage
# ---------------------------------------------------------------------------------------------

# ⚠️ Durée FIXÉE d'avance, et non un signal d'arrêt. Un `ffmpeg` en MP4 tué sans ménagement ne
# finalise pas son index : le fichier est irrécupérable. C'est le piège que le tournage de #2191
# avait rencontré, et la raison du MKV ici.
filmer() {
    local ecran="$1" sortie="$2" secondes="$3"
    ffmpeg -y -loglevel error -f x11grab -framerate "$CADENCE" \
        -video_size "${LARGEUR}x${HAUTEUR}" -i "$ecran" -t "$secondes" \
        -c:v libx264 -preset veryfast -pix_fmt yuv420p "$sortie" </dev/null
}

# Le parcours minimal : ouvrir le produit et le regarder paraître. Il ne documente rien - il prouve
# que la chaîne tient de bout en bout, ce qui est le seul objet de ce premier lot.
parcours_ouverture() {
    local ecran="$1"
    DISPLAY="$ecran" xdotool search --onlyvisible --name "VigieChiro" windowactivate --sync %@ 2>/dev/null || true
    sleep 3
}

tourner() {
    local sortie="${1:-$RACINE/target/doc-video/ouverture.mkv}"
    local bac ecran=":87" jar code=0
    jar=$(resoudre_le_jar)
    bac=$(mktemp -d)

    echo "Conditions du tournage"
    verifier_outils || code=1
    verifier_le_jar "$jar" || code=1
    verifier_bac_jetable "$bac" || code=1
    if [ "$code" -ne 0 ]; then
        echo "❌ Le tournage est refusé : voir ci-dessus."
        rm -rf "$bac"
        return 1
    fi
    echo "   ✔ outils, jar et bac jetable"

    mkdir -p "$(dirname "$sortie")"
    Xvfb "$ecran" -screen 0 "${LARGEUR}x${HAUTEUR}x24" >/dev/null 2>&1 &
    local xvfb=$!
    sleep 2
    DISPLAY="$ecran" openbox --sm-disable >/dev/null 2>&1 &
    local wm=$!
    sleep 1

    DISPLAY="$ecran" java --enable-native-access=ALL-UNNAMED \
        -Dvigiechiro.workspace="$bac" -jar "$jar" >"$bac/produit.log" 2>&1 &
    local produit=$!

    if attendre_la_fenetre "$ecran"; then
        verifier_dimensions_honorees "$ecran" || code=1
        filmer "$ecran" "$sortie" 6 &
        local camera=$!
        parcours_ouverture "$ecran"
        wait "$camera"
    else
        echo "❌ Le produit n'a pas ouvert de fenêtre : rien à filmer."
        echo "   Journal : $bac/produit.log"
        code=1
    fi

    kill "$produit" "$wm" "$xvfb" 2>/dev/null
    wait 2>/dev/null
    if [ "$code" -eq 0 ]; then
        echo "✅ $sortie ($(du -h "$sortie" | cut -f1))"
    fi
    rm -rf "$bac"
    return "$code"
}

# ---------------------------------------------------------------------------------------------
# Auto-test. Chaque condition doit savoir ROUGIR : une condition qui ne peut pas échouer ne garde
# rien, et c'est précisément ce que #3696 a découvert sur le banc de recette - trois de ses cinq
# conditions échouaient en silence.
# ---------------------------------------------------------------------------------------------

auto_test() {
    local cas=0 rouges=0 echecs=0 bac
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' RETURN

    essai() { # <libellé> <vert|rouge> <commande…>
        local libelle="$1" attendu="$2" obtenu
        shift 2
        cas=$((cas + 1))
        if [ "$attendu" = rouge ]; then rouges=$((rouges + 1)); fi
        if "$@" >/dev/null 2>&1; then obtenu=vert; else obtenu=rouge; fi
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-54s -> %s\n' "$libelle" "$obtenu"
        else
            printf '  [ÉCHEC] %-54s -> %s (attendu %s)\n' "$libelle" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    echo "AUTO-TEST"

    # --- le jar ---
    : > "$bac/faux.jar"
    essai "un jar désigné et présent est accepté"        vert  verifier_le_jar "$bac/faux.jar"
    essai "un jar désigné mais ABSENT est refusé"        rouge verifier_le_jar "$bac/nexiste-pas.jar"
    essai "aucun jar du tout est refusé"                 rouge verifier_le_jar ""
    essai "VIGIECHIRO_JAR l'emporte sur la cible Maven"  vert \
        bash -c 'source "$0"; VIGIECHIRO_JAR=/tmp/x.jar; [ "$(resoudre_le_jar)" = /tmp/x.jar ]' "${BASH_SOURCE[0]}"

    # --- le bac jetable ---
    essai "un bac hors de l'espace utilisateur est accepté" vert  verifier_bac_jetable "$bac"
    essai "un bac DANS l'espace utilisateur est refusé"     rouge \
        verifier_bac_jetable "$HOME/Documents/VigieChiro-Companion/essai"
    essai "un bac vide est refusé"                          rouge verifier_bac_jetable ""

    # --- la fenêtre ---
    # ⚠️ Le cas qui porte le banc : sans écran, aucune fenêtre ne peut paraître, et l'attente doit
    # le DIRE plutôt que laisser filmer six secondes de néant.
    essai "sans écran, l'attente de fenêtre refuse"      rouge attendre_la_fenetre ":89" 2

    # --- les outils ---
    essai "les outils du poste sont là"                  vert  verifier_outils

    echo
    if [ "$rouges" -eq 1 ]; then verbe=DOIT; else verbe=DOIVENT; fi
    echo "${cas} cas, dont ${rouges} qui ${verbe} rougir."
    if [ "$echecs" -eq 0 ]; then
        echo "Auto-test concluant."
        return 0
    fi
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de ce banc."
    return 1
}

# ⚠️ Rien ne s'exécute quand ce fichier est SOURCÉ, et ce garde n'est pas décoratif : l'auto-test
# ci-dessus se source lui-même pour éprouver `resoudre_le_jar` en isolation. Sans cette condition,
# ce `source` déclencherait un tournage - six secondes de film au milieu d'un auto-test.
#
# Le banc de recette a payé exactement cette étourderie dans l'autre sens : son cas
# « WAYLAND_DISPLAY posé » se sourçait et relançait le script, si bien qu'il n'éprouvait plus rien
# hors d'une session Wayland (#3883). Un fichier qui se source doit dire ce qu'il fait à ce
# moment-là : ici, rien.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    if [ "${1:-}" = "--auto-test" ]; then
        auto_test
        exit $?
    fi
    tourner "${1:-}"
fi
