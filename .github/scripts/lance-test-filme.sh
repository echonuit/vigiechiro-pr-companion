#!/usr/bin/env bash
# Lancer un test TestFX en mode FENÊTRÉ, le piloter, et le filmer (#3696, EPIC #3667).
#
# ## Pourquoi un script, et pas seulement un profil Maven
#
# Un lancement filmé tient à CINQ conditions. Trois sont des propriétés Maven, réunies par le
# profil `recette-filmee`. Les deux autres ne sont pas des propriétés et ne peuvent donc pas y
# vivre. Et surtout, **trois des cinq ne produisent aucune erreur quand elles manquent** :
#
#   | Condition                  | Sans elle                                        | Rougit ? |
#   |----------------------------|--------------------------------------------------|----------|
#   | glass.platform=gtk         | aucune fenêtre, on reste en headless             | NON      |
#   | testfx.robot=awt           | le robot Glass appelle le portail et s'y bloque  | oui      |
#   | java.awt.headless=false    | HeadlessException au premier clic                | oui      |
#   | WAYLAND_DISPLAY retiré     | le java.awt.Robot du JDK route par le portail :  | **NON**  |
#   |                            | le clic est émis et n'atterrit jamais            |          |
#   | un gestionnaire de fenêtres| le pointeur ne bouge pas, même pour xdotool      | **NON**  |
#
# Oublier une condition rend donc un vert qui ne pilote rien. Ce script les réunit, et les
# VÉRIFIE avant de lancer plutôt que de faire confiance.
#
# ## Ce que ce script vérifie, et comment
#
# Pour le gestionnaire de fenêtres, il ne cherche pas s'il tourne : il vérifie que **le pointeur
# bouge**. C'est la propriété qui compte, et la seule que le reste du dispositif consomme. Un
# gestionnaire présent mais inopérant passerait un contrôle de présence, pas celui-ci.
#
# Pour le profil, il lit le `pom.xml` : les trois propriétés y sont-elles aux bonnes valeurs. Un
# profil vidé de sa substance laisserait le script « marcher » en headless, sans fenêtre à filmer.
#
# Usage :
#   lance-test-filme.sh <ClasseDeTest> [sortie.mkv]
#   lance-test-filme.sh --verifier            # les préconditions, sans rien lancer
#   lance-test-filme.sh --auto-test           # éprouve les vérifications elles-mêmes

set -uo pipefail

# Se relancer SANS WAYLAND_DISPLAY plutôt que d'exiger que l'appelant y pense : l'objet de ce
# script est de tenir en une commande. La vérification qui suit garde tout son sens, puisqu'elle
# porte sur l'environnement réellement remis à Maven, et non sur une intention.
# Le drapeau évite une boucle si l'environnement le repose (ce qu'aucun cas connu ne fait, mais
# une relance infinie serait un défaut bien plus coûteux que cette ligne).
if [ -n "${WAYLAND_DISPLAY:-}" ] && [ -z "${RECETTE_RELANCE:-}" ]; then
    exec env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE RECETTE_RELANCE=1 \
        bash "${BASH_SOURCE[0]}" "$@"
fi

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POM="${POM_A_VERIFIER:-$RACINE/pom.xml}"
ECRAN="${ECRAN_RECETTE:-:97}"
TAILLE="1280x900x24"

# --------------------------------------------------------------------------------------------
# Les vérifications. Chacune rend 0 (bon) ou 1 (mauvais) et explique.
# --------------------------------------------------------------------------------------------

verifier_profil() {
    local manques=()
    local bloc
    # Le bloc du profil, des balises <id>recette-filmee</id> à la fin du profil.
    bloc=$(sed -n '/<id>recette-filmee<\/id>/,/<\/profile>/p' "$POM" 2>/dev/null)

    [ -z "$bloc" ] && { echo "   - le profil recette-filmee est ABSENT du pom"; return 1; }

    printf '%s' "$bloc" | grep -q '<recette.glass.platform>gtk<' \
        || manques+=("recette.glass.platform devrait valoir gtk")
    printf '%s' "$bloc" | grep -q '<recette.testfx.robot>awt<' \
        || manques+=("recette.testfx.robot devrait valoir awt")
    printf '%s' "$bloc" | grep -q '<recette.awt.headless>false<' \
        || manques+=("recette.awt.headless devrait valoir false")

    [ ${#manques[@]} -eq 0 ] && return 0
    printf '   - %s\n' "${manques[@]}"
    return 1
}

verifier_wayland() {
    if [ -n "${WAYLAND_DISPLAY:-}" ]; then
        echo "   - WAYLAND_DISPLAY est posé (${WAYLAND_DISPLAY}) : le java.awt.Robot du JDK"
        echo "     routera par le portail RemoteDesktop, et le clic n'atterrira jamais."
        echo "     Lancer avec : env -u WAYLAND_DISPLAY"
        return 1
    fi
    return 0
}

verifier_pointeur() {
    local ecran="$1" cible_x=137 cible_y=241 lu
    if ! DISPLAY="$ecran" xdpyinfo >/dev/null 2>&1; then
        echo "   - aucun serveur X sur $ecran"
        return 1
    fi
    DISPLAY="$ecran" xdotool mousemove "$cible_x" "$cible_y" 2>/dev/null
    lu=$(DISPLAY="$ecran" xdotool getmouselocation --shell 2>/dev/null | head -2 | tr '\n' ' ')
    if [ "$lu" != "X=$cible_x Y=$cible_y " ]; then
        echo "   - le pointeur de $ecran ne bouge pas (lu : ${lu:-rien})."
        echo "     Il manque un gestionnaire de fenêtres : sans lui, même xdotool n'y arrive pas."
        return 1
    fi
    return 0
}

verifier_outils() {
    local manques=()
    for o in Xvfb xdotool ffmpeg matchbox-window-manager; do
        command -v "$o" >/dev/null 2>&1 || manques+=("$o")
    done
    [ ${#manques[@]} -eq 0 ] && return 0
    echo "   - outils absents : ${manques[*]}"
    return 1
}

# --------------------------------------------------------------------------------------------

verifier_tout() {
    local ecran="${1:-$ECRAN}" defauts=0
    echo "Préconditions d'un lancement filmé :"
    verifier_outils  || defauts=$((defauts + 1))
    verifier_profil  || defauts=$((defauts + 1))
    verifier_wayland || defauts=$((defauts + 1))
    verifier_pointeur "$ecran" || defauts=$((defauts + 1))
    if [ "$defauts" -eq 0 ]; then
        echo "✅ Les cinq conditions sont réunies."
        return 0
    fi
    echo "❌ $defauts condition(s) manquante(s) : le lancement piloterait dans le vide."
    return 1
}

# --------------------------------------------------------------------------------------------

# On NE COUPE PAS. On DIT où regarder.
#
# Trois tentatives de découpage automatique ont échoué, et surtout : le contrôle censé attraper
# une coupe ratée a lui aussi rendu vert sur un extrait noir aux trois quarts. Les deux
# s'appuyaient sur `blackdetect`, qui exige une durée de noir CONTINUE - or le contenu est fait
# de fenêtres brèves entrelacées de noir, que ce filtre ne voit pas.
#
# Plutôt que d'empiler un troisième garde sur un instrument qui ne mesure pas la bonne chose, on
# retire le composant : la vidéo est livrée entière, et le script ANNONCE la plage où quelque
# chose se passe. Un fichier long mais complet, avec un repère juste, vaut mieux qu'un extrait
# court et faux. Découper proprement demandera une mesure de luminance image par image ; c'est
# une suite, pas un correctif.
situer_le_contenu() {
    local f="$1" plages duree fin_du_noir debut_du_noir
    [ -s "$f" ] || { echo "⚠️ rien n'a été filmé"; return 1; }
    duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f" 2>/dev/null)
    plages=$(ffmpeg -nostdin -loglevel info -i "$f" -vf "blackdetect=d=0.4:pic_th=0.98" \
             -f null - 2>&1 | grep -oE "black_start:[0-9.]+ black_end:[0-9.]+")

    if [ -z "$plages" ]; then
        LC_NUMERIC=C printf '   contenu : sur toute la durée (%.1f s)\n' "${duree:-0}"
        return 0
    fi
    fin_du_noir=$(printf '%s' "$plages" | head -1 | grep -oE "black_end:[0-9.]+" | cut -d: -f2)
    debut_du_noir=$(printf '%s' "$plages" | tail -1 | grep -oE "black_start:[0-9.]+" | cut -d: -f2)
    LC_NUMERIC=C printf '   contenu : à regarder entre %.1f s et %.1f s (sur %.1f s filmés)\n' \
        "${fin_du_noir:-0}" "${debut_du_noir:-$duree}" "${duree:-0}"
    echo "   ⚠️ le noir intercalé n'est pas retiré : les tests ouvrent et ferment leur fenêtre."
}

lancer() {
    local classe="$1"
    # Deux `local` séparés, et non `local a=… b=…$a…` : bash développe TOUS les mots avant
    # d'affecter, si bien que la seconde référence lirait une variable encore vide.
    local sortie="${2:-$RACINE/target/recette/${classe}.mkv}"
    mkdir -p "$(dirname "$sortie")"

    Xvfb "$ECRAN" -screen 0 "$TAILLE" -nolisten tcp >/dev/null 2>&1 &
    local xvfb=$!
    sleep 2
    matchbox-window-manager -display "$ECRAN" >/dev/null 2>&1 &
    local wm=$!
    sleep 2
    # Guillemets DOUBLES : les numéros de processus sont gravés dans le trap à sa pose. En
    # simples, il lirait `$wm` et `$xvfb` au déclenchement, or ce sont des `local` : à la sortie
    # du script ils n'existent plus, `set -u` avorte le trap, et Xvfb comme le gestionnaire de
    # fenêtres survivent en orphelins. Constaté.
    trap "kill $wm $xvfb 2>/dev/null" EXIT

    verifier_tout "$ECRAN" || return 1


    # ON FILME TÔT, et on coupe le noir ensuite. Ne déclencher qu'à l'apparition d'une fenêtre
    # a été essayé : la caméra court après un test d'une seconde et demie, ffmpeg reçoit l'ordre
    # d'arrêt alors qu'il s'initialise encore, et rend un fichier vide. Filmer tôt lui laisse le
    # temps ; la coupe se fait au montage, où rien ne court après rien.
    #
    # ⚠️ L'ARRÊT NE PASSE PAS PAR UN SIGNAL. Un travail lancé en arrière-plan depuis un shell
    # NON INTERACTIF ignore SIGINT (c'est POSIX), et SIGTERM n'a pas mieux marché ici. Trois
    # stratégies de signal ont échoué, dont deux en laissant croire au succès.
    # On emploie donc l'arrêt DOCUMENTÉ de ffmpeg : la commande `q` sur son entrée standard,
    # servie par un tube nommé. Il finalise alors proprement, en une seconde, et la durée
    # annoncée par le fichier est juste - ce qu'aucun `kill` n'a donné.
    # Corollaire : pas de `-nostdin`, puisque c'est justement par là qu'on lui parle. Le tube
    # reste ouvert côté écrivain (fd 3), sans quoi ffmpeg lirait EOF et s'arrêterait aussitôt.
    local tube="${sortie}.tube"
    rm -f "$tube"; mkfifo "$tube"

    ffmpeg -loglevel error -f x11grab -framerate 10 -video_size "${TAILLE%x*}" \
        -i "$ECRAN" -t 900 -c:v libx264 -preset ultrafast -crf 26 -g 20 -flush_packets 1 \
        -pix_fmt yuv420p -y "$sortie" < "$tube" >/dev/null 2>&1 &
    local film=$!
    exec 3> "$tube"

    ( cd "$RACINE" && DISPLAY="$ECRAN" ./mvnw -B test -Precette-filmee \
        -Dtest="$classe" -DfailIfNoSpecifiedTests=false )
    local code=$?

    printf q >&3
    exec 3>&-
    wait "$film" 2>/dev/null
    rm -f "$tube"

    situer_le_contenu "$sortie"

    local duree=0
    [ -s "$sortie" ] && duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$sortie" 2>/dev/null)
    # LC_NUMERIC=C : ffprobe rend « 7.900000 », que la locale française refuse (elle attend
    # une virgule). Sans cela, printf échoue et affiche un nombre FAUX, ici 7,0 pour 7,9.
    LC_NUMERIC=C printf 'Verdict du test : %s · vidéo : %s (%.1f s)\n' "$code" "$sortie" "${duree:-0}"
    return "$code"
}

# --------------------------------------------------------------------------------------------
# Auto-test : chaque condition retirée POUR DE VRAI, une à la fois.
# --------------------------------------------------------------------------------------------

auto_test() {
    local tmp total=0 rouges=0 echecs=0
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"; kill ${aux:-} 2>/dev/null' RETURN

    essai() {
        local nom="$1" attendu="$2"; shift 2
        total=$((total + 1)); [ "$attendu" = rouge ] && rouges=$((rouges + 1))
        local obtenu=vert
        "$@" >/dev/null 2>&1 || obtenu=rouge
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-46s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-46s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    echo "AUTO-TEST"

    # --- le profil, sur des poms fabriqués ---
    cp "$POM" "$tmp/complet.xml"
    sed 's|<recette.glass.platform>gtk<|<recette.glass.platform>Headless<|' "$POM" > "$tmp/sans-gtk.xml"
    sed 's|<recette.testfx.robot>awt<|<recette.testfx.robot>glass<|' "$POM" > "$tmp/sans-awt.xml"
    sed '/<id>recette-filmee<\/id>/,/<\/profile>/d' "$POM" > "$tmp/sans-profil.xml"

    essai "profil complet" vert   env POM_A_VERIFIER="$tmp/complet.xml"    bash -c 'source "$0"; verifier_profil' "${BASH_SOURCE[0]}"
    essai "glass.platform laissé en Headless" rouge env POM_A_VERIFIER="$tmp/sans-gtk.xml" bash -c 'source "$0"; verifier_profil' "${BASH_SOURCE[0]}"
    essai "robot laissé en glass" rouge env POM_A_VERIFIER="$tmp/sans-awt.xml" bash -c 'source "$0"; verifier_profil' "${BASH_SOURCE[0]}"
    essai "profil entièrement absent" rouge env POM_A_VERIFIER="$tmp/sans-profil.xml" bash -c 'source "$0"; verifier_profil' "${BASH_SOURCE[0]}"

    # --- WAYLAND_DISPLAY ---
    essai "WAYLAND_DISPLAY retiré" vert  env -u WAYLAND_DISPLAY bash -c 'source "$0"; verifier_wayland' "${BASH_SOURCE[0]}"
    essai "WAYLAND_DISPLAY posé"   rouge env WAYLAND_DISPLAY=wayland-0 bash -c 'source "$0"; verifier_wayland' "${BASH_SOURCE[0]}"

    # --- le pointeur, sur de VRAIS serveurs X ---
    Xvfb :91 -screen 0 "$TAILLE" -nolisten tcp >/dev/null 2>&1 &
    local nu=$!
    Xvfb :92 -screen 0 "$TAILLE" -nolisten tcp >/dev/null 2>&1 &
    local avec=$!
    sleep 2
    matchbox-window-manager -display :92 >/dev/null 2>&1 &
    local wm=$!
    aux="$nu $avec $wm"
    sleep 2

    essai "écran AVEC gestionnaire de fenêtres" vert  verifier_pointeur :92
    essai "écran SANS gestionnaire de fenêtres" rouge verifier_pointeur :91
    essai "écran inexistant"                    rouge verifier_pointeur :77

    kill "$nu" "$avec" "$wm" 2>/dev/null

    echo
    echo "$total cas, dont $rouges qui DOIVENT rougir."
    if [ "$echecs" -eq 0 ]; then echo "Auto-test concluant."; return 0; fi
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de ce script."
    return 1
}

# --------------------------------------------------------------------------------------------

case "${1:---aide}" in
    --auto-test) auto_test ;;
    --verifier)  verifier_tout ;;
    --aide|-h)   sed -n '/^# Usage/,/^$/p' "${BASH_SOURCE[0]}" | sed 's/^# \?//' ;;
    *)           lancer "$@" ;;
esac
