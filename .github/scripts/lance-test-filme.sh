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

# Ne garder que les images où quelque chose est à l'écran, mesuré IMAGE PAR IMAGE (#3707).
#
# ## Pourquoi pas `blackdetect`, qui semblait fait pour ça
#
# Il ne rend que des plages de noir CONTINU. Or une séance de recette est faite de fenêtres
# brèves entrelacées de noir : les tests ouvrent et ferment leur fenêtre. Trois découpages
# fondés dessus ont échoué, et - le pire - le contrôle censé attraper une coupe ratée
# s'appuyait sur le MÊME filtre : il était donc aveugle à la même chose que la coupe, et a
# rendu vert sur un extrait noir aux trois quarts.
#
# ## La grandeur qui sépare vraiment
#
# `signalstats` rend une luminance moyenne par image. Mesuré sur une séance réelle :
#
#     908 images à 16,01   (le noir)
#      22 images à 216-226 (l'application)
#
# Aucune valeur entre les deux. Le seuil est donc loin des deux modes, et insensible au
# réglage. ⚠️ Une image noire vaut 16 et NON zéro : « différent de zéro » ne marcherait pas.
#
# ## Et le contrôle mesure CE QUE LA COUPE A DÉCIDÉ
#
# C'est la leçon de #3696 : un contrôle qui mesure autre chose que la coupe reproduit son
# angle mort. Ici la coupe retient les images au-dessus du seuil, et le contrôle recompte,
# sur le MONTAGE, la proportion d'images au-dessus du même seuil.
LUMINANCE_SEUIL=50
LUMINANCE_MARGE=0.2      # un peu avant et après : ne pas couper au ras du geste
LUMINANCE_ECART=1.5      # deux segments plus proches que ça n'en font qu'un

# Rend « temps luminance » par image.
profil_luminance() {
    ffmpeg -nostdin -loglevel info -i "$1" \
        -vf "signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 \
        | grep -oE "pts_time:[0-9.]+|YAVG=[0-9.]+" | paste - - \
        | sed 's/pts_time://; s/YAVG=//'
}

# Rend la proportion d'images au-dessus du seuil, entre 0 et 1.
part_utile() {
    profil_luminance "$1" | awk -v s="$LUMINANCE_SEUIL" \
        '{n++; if ($2 > s) u++} END {print (n ? u / n : 0)}'
}

couper_par_luminance() {
    local brut="$1" sortie="$2" segments filtre part
    [ -s "$brut" ] || { echo "⚠️ rien n'a été filmé"; return 1; }

    # Les plages où la luminance dépasse le seuil, élargies puis fusionnées quand elles se
    # touchent presque : sans cela, on obtiendrait une rafale de micro-plans hachés.
    segments=$(profil_luminance "$brut" | awk -v s="$LUMINANCE_SEUIL" -v m="$LUMINANCE_MARGE" \
        -v e="$LUMINANCE_ECART" '
        $2 > s {
            if (debut == "") { debut = $1; fin = $1; next }
            if ($1 - fin > e) { printf "%.2f %.2f\n", (debut - m < 0 ? 0 : debut - m), fin + m; debut = $1 }
            fin = $1
        }
        END { if (debut != "") printf "%.2f %.2f\n", (debut - m < 0 ? 0 : debut - m), fin + m }')

    if [ -z "$segments" ]; then
        echo "⚠️ aucune image au-dessus du seuil de luminance : le brut est conservé tel quel."
        mv -f "$brut" "$sortie"
        return 1
    fi

    filtre=$(printf '%s\n' "$segments" | awk '{printf "%sbetween(t,%s,%s)", (NR>1 ? "+" : ""), $1, $2}')
    ffmpeg -nostdin -loglevel error -i "$brut" \
        -vf "select='${filtre}',setpts=N/FRAME_RATE/TB" -an \
        -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -y "$sortie" >/dev/null 2>&1

    # ⚠️ LE CONTRÔLE, sur la même grandeur que la coupe. Une coupe qui vise à côté rend un
    # fichier COURT, donc d'apparence saine : seule cette mesure la démasque.
    #
    # Le seuil est à 0,6, et non plus haut, parce que 100 % est INATTEIGNABLE : le segment
    # retenu contient lui-même du noir, aux transitions entre deux tests qui ferment et
    # rouvrent leur fenêtre. Mesuré sur une séance réelle, marge comprise :
    #
    #     marge 0,0 s -> 86 %      marge 0,2 s -> 75 %
    #     marge 0,1 s -> 80 %      marge 0,4 s -> 67 %
    #
    # tandis que le défaut à attraper - une coupe qui vise à côté - donnait 25 %. L'écart
    # entre 25 % et 75 % est large : le contrôle se place dedans. Le mettre à 0,8 refuserait
    # une marge légitime, ce qui ferait de lui un garde qui crie sur du bon travail, et donc
    # un garde qu'on apprendrait à ignorer.
    part=$(part_utile "$sortie")
    if awk -v p="$part" 'BEGIN{exit !(p < 0.6)}'; then
        echo "⚠️ la coupe a visé à côté : seules $(awk -v p="$part" 'BEGIN{printf "%.0f", p*100}') % des"
        echo "   images retenues sont au-dessus du seuil. Le brut est conservé tel quel."
        mv -f "$brut" "$sortie"
        return 1
    fi

    rm -f "$brut"
    LC_NUMERIC=C printf '   coupe : %d segment(s), %.0f %% des images retenues sont utiles\n' \
        "$(printf '%s\n' "$segments" | wc -l)" "$(awk -v p="$part" 'BEGIN{print p*100}')"
    return 0
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
    local brut="${sortie%.mkv}-brut.mkv"
    local tube="${sortie}.tube"
    rm -f "$tube"; mkfifo "$tube"

    ffmpeg -loglevel error -f x11grab -framerate 10 -video_size "${TAILLE%x*}" \
        -i "$ECRAN" -t 900 -c:v libx264 -preset ultrafast -crf 26 -g 20 -flush_packets 1 \
        -pix_fmt yuv420p -y "$brut" < "$tube" >/dev/null 2>&1 &
    local film=$!
    exec 3> "$tube"

    # Le journal de repères (#3774) s'écrit en AJOUT, pour survivre à une séance interrompue. Il
    # doit donc être retiré AVANT celle-ci, sans quoi deux séances successives mêleraient leurs
    # instants dans le même fichier - et le montage taillerait des extraits dans le film
    # d'aujourd'hui à des positions calculées sur celui d'hier. Un tel extrait s'ouvre, montre une
    # interface, et ne montre pas le bon geste : rien ne le signalerait.
    rm -f "$RACINE/target/recette-filmee/reperes.tsv"

    ( cd "$RACINE" && DISPLAY="$ECRAN" ./mvnw -B test -Precette-filmee \
        -Dtest="$classe" -DfailIfNoSpecifiedTests=false )
    local code=$?

    printf q >&3
    exec 3>&-
    wait "$film" 2>/dev/null
    rm -f "$tube"

    couper_par_luminance "$brut" "$sortie"

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

    export -f part_utile profil_luminance couper_par_luminance
    export LUMINANCE_SEUIL LUMINANCE_MARGE LUMINANCE_ECART
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

    # --- la coupe par luminance, sur des vidéos FABRIQUÉES dont on connaît le contenu ---
    ffmpeg -nostdin -loglevel error -f lavfi -i color=black:s=320x240:r=10 -t 3 \
        -c:v libx264 -preset ultrafast -pix_fmt yuv420p -y "$tmp/noir.mkv" >/dev/null 2>&1
    ffmpeg -nostdin -loglevel error -f lavfi -i color=white:s=320x240:r=10 -t 3 \
        -c:v libx264 -preset ultrafast -pix_fmt yuv420p -y "$tmp/blanc.mkv" >/dev/null 2>&1

    essai "une vidéo entièrement noire n'a rien d'utile" rouge \
        bash -c 'p=$(part_utile "$1"); awk -v p="$p" "BEGIN{exit !(p > 0.5)}"' _ "$tmp/noir.mkv"
    essai "une vidéo entièrement claire est utile"       vert  \
        bash -c 'p=$(part_utile "$1"); awk -v p="$p" "BEGIN{exit !(p > 0.5)}"' _ "$tmp/blanc.mkv"
    essai "couper une vidéo noire est REFUSÉ"            rouge \
        bash -c 'cp "$1" "$2"; couper_par_luminance "$2" "$3"' _ "$tmp/noir.mkv" "$tmp/n2.mkv" "$tmp/n3.mkv"

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
