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
    for outil in Xvfb xdotool ffmpeg xdpyinfo openbox tesseract; do
        command -v "$outil" >/dev/null 2>&1 || manquants+=("$outil")
    done
    if [ ${#manquants[@]} -gt 0 ]; then
        echo "   - outils absents : ${manquants[*]}"
        echo "     sudo apt-get install -y xvfb xdotool ffmpeg x11-utils openbox tesseract-ocr tesseract-ocr-fra"
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
# Viser un libellé, et refuser si ce n'est pas lui
# ---------------------------------------------------------------------------------------------

# La marge lue autour du point visé. Assez large pour contenir un libellé de carte, assez étroite
# pour ne pas ramasser celui d'à côté.
ZONE_L=200
ZONE_H=26

# ⚠️ `--psm 6` (bloc de texte uniforme), et non le mode par défaut. `--psm 3` est taillé pour des
# documents : sur une capture d'interface il lisait 66 mots au lieu de 117, et manquait les titres
# de cartes. Mesuré, pas supposé (#3887).
#
# L'agrandissement x5 n'est pas cosmétique non plus : un libellé d'interface fait 11 à 13 px de
# haut, très en dessous de ce que tesseract lit correctement.
lire_zone() { # <image> <x> <y> [largeur] [hauteur]
    local image="$1" x="$2" y="$3" l="${4:-$ZONE_L}" h="${5:-$ZONE_H}" tmp
    tmp=$(mktemp -d)
    ffmpeg -v error -y -i "$image" \
        -vf "crop=${l}:${h}:${x}:${y},scale=iw*5:ih*5:flags=lanczos" "$tmp/zone.png" 2>/dev/null
    tesseract "$tmp/zone.png" - -l fra --psm 6 2>/dev/null | tr -d '\n' | sed 's/  */ /g; s/^ //; s/ $//'
    rm -rf "$tmp"
}

# Compare un libellé lu à celui qu'on attendait, en ignorant les espaces.
#
# ⚠️ Ce n'est pas une facilité : à la taille d'une interface, l'OCR PERD les espaces. Mesuré sur le
# fil d'Ariane du produit, qui affiche « Accueil › Mes sites » et se lit :
#
#     [VigieChiro Companion Accueil Accueil » Messites]
#
# « Messites » n'est pas une erreur de lecture, c'est la même chaîne sans son espace. Exiger
# l'égalité stricte ferait échouer des gestes justes, et pousserait à écrire les libellés attendus
# tels que l'OCR les déforme - c'est-à-dire à documenter le défaut au lieu de l'absorber.
#
# Ce qui reste discriminant : les lettres et leur ordre. Deux libellés de l'application qui ne
# différeraient que par leurs espaces n'existent pas, et en existerait-il que ce contrôle ne serait
# pas le bon endroit pour les distinguer.
sans_espaces() {
    printf '%s' "$1" | tr -d '[:space:]'
}

libelle_correspond() { # <lu> <attendu>
    local lu attendu
    lu=$(sans_espaces "$1")
    attendu=$(sans_espaces "$2")
    [ -n "$attendu" ] && [[ "$lu" == *"$attendu"* ]]
}

# ⚠️ Pourquoi ce contrôle existe, et ce qu'il ne fait PAS.
#
# Les scénarios de #2191 cliquaient à des coordonnées nues : `g 203 811`. Au premier changement de
# mise en page, le clic tombe à côté **sans rien dire**, et le film montre un parcours qui n'a pas
# eu lieu - un fichier parfaitement valide, et faux.
#
# Viser par le graphe de scène serait mieux, et c'est impossible : JavaFX n'implémente
# l'accessibilité ni sous GTK ni via `javax.accessibility` (aucun fichier d'accessibilité dans
# `native-glass/gtk`, zéro usage de `javax/accessibility` dans les jars ; côté OpenJFX, aucune issue
# Linux depuis 2014). Le libellé se lit donc à l'écran.
#
# Ce que ce contrôle attrape : le geste qui viserait un endroit où le libellé attendu n'est plus.
# Ce qu'il n'attrape PAS : retrouver où le bouton a bougé. Il dit que le scénario est périmé, il ne
# le répare pas - et c'est le bon partage pour un banc dont le péché serait de filmer du faux.
viser() { # <écran> <x> <y> <libellé attendu> [durée du trajet]
    local ecran="$1" x="$2" y="$3" attendu="$4" duree="${5:-0.55}" tmp lu
    tmp=$(mktemp -d)
    ffmpeg -v error -y -f x11grab -video_size "${LARGEUR}x${HAUTEUR}" -i "$ecran" \
        -frames:v 1 "$tmp/ecran.png" </dev/null 2>/dev/null

    lu=$(lire_zone "$tmp/ecran.png" "$((x - ZONE_L / 2))" "$((y - ZONE_H / 2))")
    rm -rf "$tmp"

    if ! libelle_correspond "$lu" "$attendu"; then
        echo "   - le geste visait « $attendu » en ($x, $y) ; l'écran y porte « $lu »"
        echo "     Le scénario est périmé : la mise en page a changé, ou l'écran n'est pas celui attendu."
        return 1
    fi

    local X Y
    eval "$(DISPLAY="$ecran" xdotool getmouselocation --shell | head -2)"
    DISPLAY="$ecran" xdotool $(python3 "$(dirname "${BASH_SOURCE[0]}")/trajet.py" "$X" "$Y" "$x" "$y" "$duree")
    sleep 0.18
    DISPLAY="$ecran" xdotool click 1
    return 0
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

    # --- lire un libellé à l'écran ---
    # ⚠️ La fixture se FABRIQUE : un banc dont l'auto-test dépendrait d'une capture committée
    # rougirait au premier changement de style, pour une raison étrangère à ce qu'il éprouve.
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=400x60" \
        -vf "drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='Mes sites':fontcolor=black:fontsize=22:x=20:y=18" \
        -frames:v 1 "$bac/libelle.png" </dev/null 2>/dev/null
    essai "un libellé rendu à l'écran se lit"           vert \
        bash -c 'source "$0"; [ "$(lire_zone "$1" 0 0 400 60)" = "Mes sites" ]' "${BASH_SOURCE[0]}" "$bac/libelle.png"
    essai "une zone vide ne rend aucun libellé"         vert \
        bash -c 'source "$0"; [ -z "$(lire_zone "$1" 300 0 90 60)" ]' "${BASH_SOURCE[0]}" "$bac/libelle.png"
    # ⚠️ Le cas qui porte le contrôle : lire le MAUVAIS libellé doit échouer, sinon `viser` laisserait
    # partir n'importe quel clic.
    essai "un libellé ABSENT ne se lit pas quand même"  rouge \
        bash -c 'source "$0"; [ "$(lire_zone "$1" 0 0 400 60)" = "Importer une nuit" ]' "${BASH_SOURCE[0]}" "$bac/libelle.png"

    # --- l'appariement des libellés ---
    essai "un libellé identique correspond"              vert \
        bash -c 'source "$0"; libelle_correspond "Mes sites" "Mes sites"' "${BASH_SOURCE[0]}"
    # ⚠️ Le cas mesuré sur le produit : l'OCR rend « Messites » sans son espace.
    essai "un espace perdu par l'OCR correspond quand même" vert \
        bash -c 'source "$0"; libelle_correspond "Accueil » Messites" "Mes sites"' "${BASH_SOURCE[0]}"
    essai "un libellé DIFFÉRENT ne correspond pas"        rouge \
        bash -c 'source "$0"; libelle_correspond "Mes sites" "Importer une nuit"' "${BASH_SOURCE[0]}"
    # ⚠️ Sans ce cas, un attendu vide correspondrait à tout, et « viser » laisserait partir n'importe
    # quel clic sur un scénario mal écrit.
    essai "un attendu VIDE ne correspond à rien"          rouge \
        bash -c 'source "$0"; libelle_correspond "Mes sites" ""' "${BASH_SOURCE[0]}"

    # --- le trajet de souris ---
    essai "un trajet rend des arguments xdotool"        vert \
        bash -c 'python3 "$(dirname "$0")/trajet.py" 10 10 200 200 0.3 | grep -q "^mousemove "' "${BASH_SOURCE[0]}"
    essai "un trajet nul ne bouge pas pour rien"        vert \
        bash -c '[ "$(python3 "$(dirname "$0")/trajet.py" 40 40 41 40 0.3)" = "mousemove 41 40" ]' "${BASH_SOURCE[0]}"

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
