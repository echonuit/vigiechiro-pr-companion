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

# Au-dessus de quoi une image « montre quelque chose ». Le noir vaut 16 ; 20 est le seuil retenu
# par le banc de recette depuis le passage à openbox (#3788).
LUMINANCE_SEUIL=20

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
viser() { # <écran> <x> <y> <libellé attendu> [durée du trajet] [largeur de la zone lue]
    local ecran="$1" x="$2" y="$3" attendu="$4" duree="${5:-0.55}" largeur="${6:-$ZONE_L}" tmp lu
    tmp=$(mktemp -d)
    ffmpeg -v error -y -f x11grab -video_size "${LARGEUR}x${HAUTEUR}" -i "$ecran" \
        -frames:v 1 "$tmp/ecran.png" </dev/null 2>/dev/null

    lu=$(lire_zone "$tmp/ecran.png" "$((x - largeur / 2))" "$((y - ZONE_H / 2))" "$largeur")
    rm -rf "$tmp"

    if ! libelle_correspond "$lu" "$attendu"; then
        echo "   - le geste visait « $attendu » en ($x, $y) ; l'écran y porte « $lu »"
        # ⚠️ Distinguer les deux causes, parce qu'elles ne se corrigent pas pareil. Un libellé plus
        # long que la zone lue est TRONQUÉ, et le refus accuserait alors le scénario d'être périmé
        # alors qu'il est juste - mesuré sur « + Ajouter mon premier site de suivi », rendu
        # « uter mon premier site de: » dans une zone de 200 px.
        if [ -n "$lu" ]; then
            echo "     La zone lue fait ${largeur} px : si le libellé est plus large, il en sort."
            echo "     Élargissez-la (6ᵉ argument), ou visez un fragment plus court."
        fi
        echo "     Sinon le scénario est périmé : la mise en page a changé, ou l'écran n'est pas celui attendu."
        return 1
    fi

    local X Y
    eval "$(DISPLAY="$ecran" xdotool getmouselocation --shell | head -2)"
    DISPLAY="$ecran" xdotool $(python3 "$(dirname "${BASH_SOURCE[0]}")/trajet.py" "$X" "$Y" "$x" "$y" "$duree")
    sleep 0.18
    DISPLAY="$ecran" xdotool click 1
    return 0
}

# Saisit du texte au rythme d'une main. `--delay` en millisecondes par touche : à 0, xdotool colle
# la chaîne d'un bloc et le spectateur ne voit rien se remplir.
taper() { # <écran> <texte>
    DISPLAY="$1" xdotool type --delay 70 "$2"
}

# Un marqueur : l'instant où l'on passe, et son nom. Le montage s'en sert pour savoir ce qu'il peut
# accélérer, et l'index pour dire où commence quoi.
#
# ⚠️ L'instant est celui de l'HORLOGE, pas une position dans le film. La conversion se fait après
# coup, `t0` étant mesuré comme « instant d'arrêt moins durée du fichier » - jamais postulé, comme
# le fait `lance-test-filme.sh`. `monter.py` de #2191 supposait un décalage de 1,5 s ; le banc de
# recette a montré que cette latence varie.
marque() { # <fichier de marques> <nom>
    printf '%s\t%s\n' "$(date +%s.%N)" "$2" >> "$1"
}

# Le parcours « déclarer un carré », premier de la documentation (#3887).
#
# ## Pourquoi celui-ci d'abord
#
# `docs/ecrans/sites.md` le pose en porte d'entrée : « vous ne pouvez pas importer une nuit tant
# qu'un site n'est pas déclaré ». Et il n'exige **aucune fixture** - un espace de travail vide
# suffit. L'importation, qui apprend davantage, demande une carte SD : elle viendra ensuite.
#
# ## Ce que chaque geste vérifie
#
# Chaque `viser` porte le libellé qu'il attend à l'écran. Un bouton déplacé ne produit donc pas un
# clic muet au mauvais endroit mais un refus, et le tournage s'arrête là.
parcours_declarer_un_carre() {
    local ecran="$1" marques="$2"

    marque "$marques" debut
    respirer_doc 2.0                                   # l'accueil, le temps de le lire

    viser "$ecran" 238 454 "Mes sites" || return 1
    marque "$marques" mes_sites
    respirer_doc 2.2                                   # l'état vide, et son invitation

    viser "$ecran" 640 501 "Ajouter mon premier site de suivi" 0.6 420 || return 1
    marque "$marques" modale
    respirer_doc 1.6                                   # la modale s'installe

    DISPLAY="$ecran" xdotool mousemove 557 253 click 1
    taper "$ecran" "640380"
    respirer_doc 1.2

    DISPLAY="$ecran" xdotool mousemove 640 348 click 1
    # ⚠️ « Mare » et non « Étang », qui est pourtant l'exemple du produit : `xdotool type` a rendu
    # « étang » minuscule sur le premier tournage - il perd la MAJUSCULE ACCENTUÉE. Un film de
    # documentation ne doit pas montrer une saisie que l'utilisateur n'obtiendrait pas.
    taper "$ecran" "Mare de la Tuiliere"
    respirer_doc 1.4
    marque "$marques" saisi

    viser "$ecran" 842 545 "Créer" || return 1
    marque "$marques" cree
    respirer_doc 2.5                                   # la fiche du carré paraît

    marque "$marques" fin
    return 0
}

# Les respirations du film. Elles ne servent qu'au spectateur : un écran qui change trop vite ne se
# lit pas. Hors tournage, elles ne coûtent rien.
respirer_doc() {
    sleep "$1"
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
    local sortie="${1:-$RACINE/target/doc-video/declarer-un-carre.mkv}"
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
        local marques="${sortie%.mkv}.marques.tsv"
        : > "$marques"
        # ⚠️ Durée fixée d'avance, et généreuse : un ffmpeg tué sans ménagement ne finalise pas son
        # index. Le parcours dure une trentaine de secondes ; on filme 45, et le montage coupera.
        filmer "$ecran" "$sortie" 45 &
        local camera=$!
        parcours_declarer_un_carre "$ecran" "$marques" || code=1
        wait "$camera"
        # ⚠️ APRÈS `wait`, et l'ordre est tout. `t0` se calcule « instant d'arrêt moins durée du
        # fichier » : il faut donc l'instant où la CAMÉRA s'est arrêtée, pas celui où le parcours
        # s'est terminé. Marqué avant le `wait`, les repères se convertissaient en 21,3 s à 45,0 s -
        # un parcours qui n'aurait commencé qu'à la moitié d'un film qu'il occupe en entier. Le
        # montage aurait coupé les mauvaises plages, et le film serait resté parfaitement valide.
        marque "$marques" arret

        if monter "$sortie" "$marques" "${sortie%.mkv}-monte.mkv"; then
            ecrire_index "${sortie%.mkv}-monte.mkv" "$marques" "$(dirname "$sortie")/index.md"
        else
            code=1
        fi
    else
        echo "❌ Le produit n'a pas ouvert de fenêtre : rien à filmer."
        echo "   Journal : $bac/produit.log"
        code=1
    fi

    kill "$produit" "$wm" "$xvfb" 2>/dev/null
    wait 2>/dev/null
    if [ "$code" -eq 0 ]; then
        echo "✅ $sortie ($(du -h "$sortie" | cut -f1))"
        echo "   marques : $(wc -l < "${sortie%.mkv}.marques.tsv") repères"
    fi
    rm -rf "$bac"
    return "$code"
}

# ---------------------------------------------------------------------------------------------
# Le montage
# ---------------------------------------------------------------------------------------------

# La part d'images d'un film où quelque chose est à l'écran. Le noir vaut 16 en luminance ; on
# compte au-dessus d'un seuil bas, comme le fait le banc de recette (#3707, seuil ramené à 20
# depuis openbox).
part_utile() { # <film>
    local mesures total claires
    # ⚠️ PAS de `-v error` ici, et c'est un piège coûteux : `metadata=print` écrit au niveau INFO,
    # si bien qu'abaisser la verbosité fait disparaître la mesure elle-même. On lit alors zéro image
    # partout - un film blanc et un film noir rendent le même verdict.
    mesures=$(ffmpeg -nostdin -i "$1" -vf "signalstats,metadata=print" -f null - 2>&1 \
        | grep -oE "signalstats.YAVG=[0-9.]+")
    total=$(printf '%s\n' "$mesures" | grep -c "YAVG" || true)
    claires=$(printf '%s\n' "$mesures" | awk -F= -v s="$LUMINANCE_SEUIL" '$2 > s' | wc -l)
    [ "$total" -gt 0 ] || { printf '0'; return; }
    LC_NUMERIC=C awk -v c="$claires" -v t="$total" 'BEGIN{printf "%.2f", c / t}'
}

# `t0` : l'instant, sur l'horloge, de la première image du film.
#
# ⚠️ Il se MESURE - « instant d'arrêt moins durée du fichier » - et ne se postule pas. `monter.py`
# de #2191 écrivait `DECALAGE = 1.5` ; mesurée ici, la latence d'initialisation d'ffmpeg vaut 0,3 s.
# Un facteur cinq sur une constante en dur, et personne ne l'aurait vu : un montage décalé rend un
# film parfaitement valide, qui montre autre chose que ce que son index annonce.
origine_du_film() { # <fichier de marques> <durée du film>
    LC_NUMERIC=C awk -F'\t' -v d="$2" '$2 == "arret" {printf "%.3f", $1 - d}' "$1"
}

instant_du_repere() { # <fichier de marques> <t0> <nom>
    LC_NUMERIC=C awk -F'\t' -v t0="$2" -v n="$3" '$2 == n {printf "%.3f", $1 - t0}' "$1"
}

# Coupe le brut sur ce que le parcours occupe vraiment.
#
# ## Ce que ce montage fait, et ce qu'il ne fait pas
#
# Il **coupe**, il n'accélère rien. Le parcours « déclarer un carré » ne comporte aucune attente
# machine : la création est instantanée, et les seuls temps morts sont les respirations posées POUR
# le spectateur - les comprimer irait contre leur raison d'être.
#
# ⚠️ Écrire l'accélération maintenant produirait un dispositif qu'aucun cas ne peut faire rougir,
# ce que #3886 vient de reprocher à seize auto-tests. Elle viendra avec le parcours d'importation,
# qui a de vraies attentes : la scrutation de la carte et l'import.
monter() { # <brut> <marques> <sortie>
    local brut="$1" marques="$2" sortie="$3" duree t0 debut fin part
    duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$brut" 2>/dev/null)
    [ -n "$duree" ] || { echo "   - durée du brut illisible : pas de montage"; return 1; }

    t0=$(origine_du_film "$marques" "$duree")
    [ -n "$t0" ] || { echo "   - aucun repère « arret » : t0 ne peut pas se mesurer"; return 1; }

    debut=$(instant_du_repere "$marques" "$t0" debut)
    fin=$(instant_du_repere "$marques" "$t0" fin)
    if [ -z "$debut" ] || [ -z "$fin" ]; then
        echo "   - repères « debut » ou « fin » manquants : pas de montage"
        return 1
    fi
    # Le repère de début tombe une fraction AVANT la première image ; on borne à zéro.
    LC_NUMERIC=C awk -v d="$debut" 'BEGIN{exit !(d < 0)}' && debut=0

    ffmpeg -nostdin -v error -i "$brut" -ss "$debut" -to "$fin" -an \
        -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -y "$sortie" 2>/dev/null

    # ⚠️ Le contrôle, et il porte sur le RÉSULTAT, pas sur les repères. Un montage qui viserait à
    # côté rendrait un film noir - valide, lisible, et vide. C'est la seule chose qu'on ne peut pas
    # laisser passer en silence.
    part=$(part_utile "$sortie")
    if LC_NUMERIC=C awk -v p="$part" 'BEGIN{exit !(p < 0.5)}'; then
        LC_NUMERIC=C printf '   - le montage vise à côté : %.0f %% seulement du film coupé montre quelque chose\n' \
            "$(LC_NUMERIC=C awk -v p="$part" 'BEGIN{print p * 100}')"
        return 1
    fi
    LC_NUMERIC=C printf '   ✔ montage : %.1f s -> %.1f s, %.0f %% d\u2019images utiles\n' \
        "$duree" "$(LC_NUMERIC=C awk -v a="$debut" -v b="$fin" 'BEGIN{print b - a}')" \
        "$(LC_NUMERIC=C awk -v p="$part" 'BEGIN{print p * 100}')"
    return 0
}

# ---------------------------------------------------------------------------------------------
# L'index
# ---------------------------------------------------------------------------------------------

# La fiche d'écran que ce parcours illustre. Écrite ici, à côté du parcours, et non dans l'index :
# l'index se DÉRIVE, il ne se tient pas à la main - c'est la leçon de #3885, où une page portait
# trois inventaires du même fait et où deux avaient dérivé.
FICHE_DU_PARCOURS="docs/ecrans/sites.md"

# Écrit l'index d'un film à partir de ses repères. Rien n'y est saisi : la durée vient du fichier,
# les étapes des marqueurs, la part d'images utiles de la mesure.
#
# ⚠️ Ce que l'index dit de lui-même compte autant que ce qu'il liste. Un lecteur doit savoir que ce
# film prouve un ENCHAÎNEMENT et rien d'autre : qu'il soit clair, lisible, bien rythmé, aucune
# machine ne le dit. C'est le troisième état de l'ADR 3764, transposé à la documentation.
ecrire_index() { # <film monté> <marques> <index>
    local film="$1" marques="$2" index="$3" duree t0 brut_duree part
    duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$film" 2>/dev/null)
    brut_duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "${film%-monte.mkv}.mkv" 2>/dev/null)
    t0=$(origine_du_film "$marques" "$brut_duree")
    part=$(part_utile "$film")

    {
        echo "# Parcours filmés de la documentation"
        echo
        echo "Cette page est **dérivée** : elle se réécrit à chaque tournage, depuis les repères posés"
        echo "par le scénario et la mesure du fichier. Rien n'y est saisi à la main."
        echo
        echo "## Déclarer un carré"
        echo
        printf '| | |\n|---|---|\n'
        printf '| film | `%s` |\n' "$(basename "$film")"
        LC_NUMERIC=C printf '| durée | %.1f s (brut : %.1f s) |\n' "$duree" "$brut_duree"
        LC_NUMERIC=C printf '| images utiles | %.0f %% |\n' "$(LC_NUMERIC=C awk -v p="$part" 'BEGIN{print p * 100}')"
        printf '| illustre | [%s](../../%s) |\n' "$FICHE_DU_PARCOURS" "$FICHE_DU_PARCOURS"
        echo
        echo "### Où en est le parcours"
        echo
        printf '| étape | dans le film |\n|---|---|\n'
        # ⚠️ Les positions se comptent dans le film MONTÉ, pas dans le brut. Le montage a coupé au
        # repère « debut » : il faut donc retrancher cette origine, sans quoi l'index annonce un
        # « debut » à -0,2 s dans un film qui commence à zéro - une petite fausseté, sur la page
        # dont tout l'intérêt est de dire où regarder.
        local coupe nom brute relative
        coupe=$(instant_du_repere "$marques" "$t0" debut)
        LC_NUMERIC=C awk -v c="$coupe" 'BEGIN{exit !(c < 0)}' && coupe=0
        while IFS=$'\t' read -r _ nom; do
            [ "$nom" = arret ] && continue
            brute=$(instant_du_repere "$marques" "$t0" "$nom")
            relative=$(LC_NUMERIC=C awk -v b="$brute" -v c="$coupe" 'BEGIN{v = b - c; if (v < 0) v = 0; printf "%.1f", v}')
            printf '| %s | %s s |\n' "$nom" "$relative"
        done < "$marques"
        echo
        echo "### ⚠️ Ce que ce film ne prouve pas"
        echo
        echo "Qu'il soit **clair**. Le banc sait dire que l'enchaînement a eu lieu - chaque geste a"
        echo "vérifié son libellé avant de partir - et que le montage tombe sur du contenu. Il ne sait"
        echo "pas si un lecteur comprend ce qu'il voit, ni si le rythme lui convient."
        echo
        echo "C'est le troisième état de l'[ADR 3764](../../dev-docs/decisions/3764-un-cas-joue-n-est-pas-un-cas-prouve.md) :"
        echo "joué n'est pas prouvé. Ce verdict-là revient à qui regarde."
    } > "$index"
    echo "   ✔ index : $(basename "$index")"
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

    # ⚠️ Le cas qui a coûté un aller-retour : un libellé plus large que la zone est tronqué, et le
    # refus doit le DIRE au lieu d'accuser le scénario.
    essai "un libellé tronqué par la zone ne correspond pas" rouge \
        bash -c 'source "$0"; libelle_correspond "uter mon premier site de:" "Ajouter mon premier site de suivi"' "${BASH_SOURCE[0]}"

    # --- le trajet de souris ---
    essai "un trajet rend des arguments xdotool"        vert \
        bash -c 'python3 "$(dirname "$0")/trajet.py" 10 10 200 200 0.3 | grep -q "^mousemove "' "${BASH_SOURCE[0]}"
    essai "un trajet nul ne bouge pas pour rien"        vert \
        bash -c '[ "$(python3 "$(dirname "$0")/trajet.py" 40 40 41 40 0.3)" = "mousemove 41 40" ]' "${BASH_SOURCE[0]}"

    # --- le montage ---
    # ⚠️ Les repères se fabriquent, avec un « arret » qui sait où est la fin : c'est la grandeur
    # dont tout le montage dépend, et la seule que #2191 postulait.
    printf '1000.0\tdebut\n1002.0\tfin\n1010.0\tarret\n' > "$bac/m.tsv"
    essai "t0 se mesure : arret moins duree"             vert \
        bash -c 'source "$0"; [ "$(origine_du_film "$1" 10)" = "1000.000" ]' "${BASH_SOURCE[0]}" "$bac/m.tsv"
    essai "un repere se convertit en position de film"   vert \
        bash -c 'source "$0"; [ "$(instant_du_repere "$1" 1000 fin)" = "2.000" ]' "${BASH_SOURCE[0]}" "$bac/m.tsv"
    # ⚠️ Sans repere « arret », t0 est indéterminable : le montage doit refuser, pas deviner.
    printf '1000.0\tdebut\n1002.0\tfin\n' > "$bac/sans-arret.tsv"
    essai "sans repere « arret », t0 est refuse"         rouge \
        bash -c 'source "$0"; [ -n "$(origine_du_film "$1" 10)" ]' "${BASH_SOURCE[0]}" "$bac/sans-arret.tsv"
    # Un film clair rend une part utile de 1 ; un film noir, de 0. Les deux bornes, pas un reglage.
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=160x120:d=2:r=10" "$bac/clair.mkv" </dev/null 2>/dev/null
    ffmpeg -v error -y -f lavfi -i "color=c=black:s=160x120:d=2:r=10" "$bac/noir.mkv" </dev/null 2>/dev/null
    essai "un film clair rend une part utile de 1"       vert \
        bash -c 'source "$0"; [ "$(part_utile "$1")" = "1.00" ]' "${BASH_SOURCE[0]}" "$bac/clair.mkv"
    essai "un film noir rend une part utile de 0"        vert \
        bash -c 'source "$0"; [ "$(part_utile "$1")" = "0.00" ]' "${BASH_SOURCE[0]}" "$bac/noir.mkv"

    # --- l'index ---
    # ⚠️ Le cas qui garde l'honnêteté de la page. « Ce que ce film ne prouve pas » est la première
    # section qu'on retire quand on veut faire propre, et c'est la seule qui empêche de lire l'index
    # comme un certificat. Un index sans elle annoncerait « 100 % d'images utiles » à un lecteur qui
    # comprendrait « ce film est bon ».
    printf '1000.0\tdebut\n1002.0\tfin\n1010.0\tarret\n' > "$bac/mi.tsv"
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=160x120:d=2:r=10" "$bac/f.mkv" </dev/null 2>/dev/null
    cp "$bac/f.mkv" "$bac/f-monte.mkv"
    essai "l index dit ce que le film ne prouve PAS"     vert \
        bash -c 'source "$0"; ecrire_index "$1" "$2" "$3" >/dev/null; grep -q "ne prouve pas" "$3"' \
        "${BASH_SOURCE[0]}" "$bac/f-monte.mkv" "$bac/mi.tsv" "$bac/i.md"
    essai "l index nomme la fiche d ecran illustree"     vert \
        bash -c 'source "$0"; grep -q "docs/ecrans" "$3"' \
        "${BASH_SOURCE[0]}" "$bac/f-monte.mkv" "$bac/mi.tsv" "$bac/i.md"

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
