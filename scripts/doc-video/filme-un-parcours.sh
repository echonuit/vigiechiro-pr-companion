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

# L'écran virtuel du tournage. Constante, et hissée ici pour que le calage puisse s'y brancher.
ECRAN_DU_TOURNAGE=":87"

# ---------------------------------------------------------------------------------------------
# Les conditions du lancement. Chacune se vérifie, et chacune sait DIRE ce qui manque : sans cela,
# un banc qui ne pilote rien rend un fichier vidéo parfaitement valide et vide (#3707).
# ---------------------------------------------------------------------------------------------

# Ce que le banc réclame, en DEUX listes.
#
# ⚠️ Une seule liste coûtait cher au mauvais endroit. `udisksctl` vient du paquet `udisks2`, qui
# n'a rien à faire sur un runner : l'auto-test ne monte aucune carte, il éprouve les REFUS du
# montage - étiquette étrangère, point vide, source sans brut - qui sont du raisonnement pur. Exiger
# l'outil pour lancer les cas revenait à installer un service de disques pour vérifier des chaînes
# de caractères.
#
# La distinction est celle du parcours : un film qui monte une carte a besoin des trois derniers,
# les autres non. `tourner` réclame donc la seconde liste **seulement** pour un parcours qui déclare
# une spec.
OUTILS_DU_BANC="Xvfb xdotool ffmpeg xdpyinfo openbox tesseract"
OUTILS_DE_LA_CARTE="udisksctl mkfs.vfat mcopy"

# ⚠️ Le remède doit couvrir CE qui manque. Le message ne nommait que les paquets de la première
# liste, en vérifiant les neuf outils : à qui manquait `mcopy`, il conseillait d'installer
# tesseract. Un message qui nomme le problème sans nommer son remède fait chercher ailleurs.
verifier_outils() { # [--avec-carte]
    local manquants=() attendus="$OUTILS_DU_BANC"
    [ "${1:-}" = --avec-carte ] && attendus="$attendus $OUTILS_DE_LA_CARTE"
    for outil in $attendus; do
        command -v "$outil" >/dev/null 2>&1 || manquants+=("$outil")
    done
    if [ ${#manquants[@]} -eq 0 ]; then
        return 0
    fi
    echo "   - outils absents : ${manquants[*]}"
    echo "     sudo apt-get install -y xvfb xdotool ffmpeg x11-utils openbox \\"
    echo "         tesseract-ocr tesseract-ocr-fra dosfstools mtools udisks2"
    return 1
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
# L'environnement dans lequel le produit est lancé pour un tournage.
#
# ⚠️ Pourquoi HOME doit être jetable, et pas seulement l'espace de travail. Le sélecteur de dossier
# est un sélecteur GTK ordinaire : il ouvre sur le dossier courant, liste le dossier personnel, et
# sa barre latérale porte les emplacements de l'utilisateur. La première sonde de cet écran a filmé
# en clair « nedjar », « sandbox », « R202 », « CLAUDE.md », « maj-notes-scodoc.py », « plop.md » et
# les dossiers de cours. Un film publié dans `docs/` aurait montré l'arborescence privée de qui l'a
# tourné - et rien dans le banc ne l'aurait dit, le fichier étant par ailleurs parfait.
#
# ⚠️ Ce qu'un HOME jetable ne cache PAS, et il faut le savoir avant de tourner : les VOLUMES MONTÉS
# restent dans la barre latérale. Celui du banc, mais aussi ceux de l'utilisateur, sous leur
# étiquette de volume - « Volume de 31 GB » sur le poste où ceci est écrit. C'est un nom générique,
# sans rien du contenu, et démonter les cartes de l'utilisateur pour tourner serait pire que le mal.
# Le banc le signale plutôt que de le corriger.
poser_environnement_jetable() { # <bac>
    local bac="${1:-}"
    [ -n "$bac" ] || return 1
    mkdir -p "$bac/home/.config" "$bac/home/.cache" "$bac/home/.local/share" || return 1
    export HOME="$bac/home"
    export XDG_CONFIG_HOME="$bac/home/.config"
    export XDG_CACHE_HOME="$bac/home/.cache"
    export XDG_DATA_HOME="$bac/home/.local/share"
    cd "$bac/home" || return 1
}

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
    # ⚠️ Une image absente rendait une chaîne VIDE, indiscernable de « rien d'écrit à cet endroit ».
    # J'ai calibré six mesures sur un fichier qui n'existait pas et conclu que le texte n'y était
    # pas. Une mesure vide n'est pas un zéro : elle doit se signaler.
    if [ ! -f "$image" ]; then
        echo "lire_zone : image introuvable : $image" >&2
        return 1
    fi
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
# Les décalages verticaux essayés quand la lecture au point visé ne correspond pas.
#
# ⚠️ Pourquoi un balayage, et pourquoi il se DIT. Une feuille de style du socle (#4023) a rehaussé
# l'en-tête de huit pixels : « Mes sites », visé en y=454, s'est retrouvé en y=462, et la zone lue
# ramassait le bas de l'icône - « | Mecs citec » au lieu de « Mes sites ». Le scénario était juste,
# le libellé était là, et le tournage s'arrêtait.
#
# Un banc qui refuse à chaque reflux de deux lignes de CSS ne sert personne. Mais un banc qui
# absorberait le glissement en SILENCE laisserait ses scénarios pourrir sans que personne ne le
# sache : au bout de quelques décalages, les coordonnées ne veulent plus rien dire. Il balaie donc,
# et il ANNONCE l'écart qu'il a dû prendre.
# ⚠️ Le pas est de QUATRE pixels, pas de huit. Mesuré : le bouton « + Ajouter » du modal était à
# dix pixels du point visé ; à l'écart +8 la fenêtre de lecture le rognait et rendait « uler | | »,
# à +10 elle rendait « + Ajouter ». Deux pixels séparaient le refus de la lecture juste, parce que
# la fenêtre ne fait que 26 px de haut pour un libellé de 13. Un pas trop grand ne balaie rien.
DECALAGES_ESSAYES="0 -4 4 -8 8 -12 12 -16 16 -20 20 -24 24"

# L'ordonnée RÉELLE d'un libellé, corrigée du glissement de la mise en page.
#
# ⚠️ Extraite de `viser` pour le parcours « multi-nuits », qui doit LIRE à un endroit et CLIQUER à un
# autre : la case « Importer » d'une ligne de la table des nuits n'a aucun texte, donc rien à viser.
# La recopier dans le parcours aurait donné deux balayages à maintenir, et celui du parcours aurait
# vieilli en silence.
#
# Rend l'ordonnée corrigée sur la sortie standard ; tout le reste part sur l'erreur standard, pour
# que l'appelant puisse capturer le nombre sans capturer les explications.
ordonnee_du_libelle() { # <écran> <x> <y> <libellé attendu> [largeur de la zone lue]
    local ecran="$1" x="$2" y="$3" attendu="$4" largeur="${5:-$ZONE_L}"
    local tmp lu ecart trouve=""
    tmp=$(mktemp -d)
    ffmpeg -v error -y -f x11grab -video_size "${LARGEUR}x${HAUTEUR}" -i "$ecran" \
        -frames:v 1 "$tmp/ecran.png" </dev/null 2>/dev/null

    for ecart in $DECALAGES_ESSAYES; do
        lu=$(lire_zone "$tmp/ecran.png" "$((x - largeur / 2))" "$((y + ecart - ZONE_H / 2))" "$largeur")
        if libelle_correspond "$lu" "$attendu"; then
            trouve="$ecart"
            break
        fi
    done
    rm -rf "$tmp"

    if [ -z "$trouve" ]; then
        echo "   - le geste visait « $attendu » en ($x, $y) ; l'écran y porte « $lu »" >&2
        # ⚠️ Distinguer les deux causes, parce qu'elles ne se corrigent pas pareil. Un libellé plus
        # long que la zone lue est TRONQUÉ, et le refus accuserait alors le scénario d'être périmé
        # alors qu'il est juste - mesuré sur « + Ajouter mon premier site de suivi », rendu
        # « uter mon premier site de: » dans une zone de 200 px.
        if [ -n "$lu" ]; then
            echo "     La zone lue fait ${largeur} px : si le libellé est plus large, il en sort." >&2
            echo "     Élargissez-la, ou visez un fragment plus court." >&2
        fi
        echo "     Sinon le scénario est périmé : la mise en page a changé, ou l'écran n'est pas celui attendu." >&2
        return 1
    fi

    if [ "$trouve" != 0 ]; then
        echo "   ~ « $attendu » trouvé à $trouve px du point visé : la mise en page a glissé," >&2
        echo "     le scénario reste juste. À recaler si l'écart grandit." >&2
    fi
    echo "$((y + trouve))"
}

# Amène le pointeur en (x, y) et clique.
main_vers() { # <écran> <x> <y> [durée du trajet]
    local ecran="$1" x="$2" y="$3" duree="${4:-0.55}" X Y
    eval "$(DISPLAY="$ecran" xdotool getmouselocation --shell | head -2)"
    DISPLAY="$ecran" xdotool $(python3 "$(dirname "${BASH_SOURCE[0]}")/trajet.py" "$X" "$Y" "$x" "$y" "$duree")
    sleep 0.18
    DISPLAY="$ecran" xdotool click 1
}

viser() { # <écran> <x> <y> <libellé attendu> [durée du trajet] [largeur de la zone lue]
    local ecran="$1" x="$2" y="$3" attendu="$4" duree="${5:-0.55}" largeur="${6:-$ZONE_L}"
    local reel
    reel=$(ordonnee_du_libelle "$ecran" "$x" "$y" "$attendu" "$largeur") || return 1
    main_vers "$ecran" "$x" "$reel" "$duree"
    return 0
}

# Clique à un endroit REPÉRÉ PAR UN AUTRE : le libellé se lit en (x_lu, y), le clic tombe en
# (x_clic, y corrigé). C'est la seule façon d'atteindre un contrôle sans texte - une case à cocher -
# sans revenir au clic aveugle, celui qui a rendu un film de trente-quatre secondes où rien n'arrive.
viser_a_cote_de() { # <écran> <x du libellé> <y> <libellé attendu> <x du clic> [durée] [largeur lue]
    local ecran="$1" x_lu="$2" y="$3" attendu="$4" x_clic="$5" duree="${6:-0.55}" largeur="${7:-$ZONE_L}"
    local reel
    reel=$(ordonnee_du_libelle "$ecran" "$x_lu" "$y" "$attendu" "$largeur") || return 1
    main_vers "$ecran" "$x_clic" "$reel" "$duree"
    return 0
}

# Exige qu'un libellé SOIT à l'écran, sans rien cliquer.
#
# ⚠️ Pourquoi cette fonction existe, et ce qu'elle répare. Le banc ne vérifiait que ses GESTES : un
# `viser` réussi prouve que le bouton était là et qu'on a cliqué dessus, jamais que le clic a **fait
# quelque chose**. Le premier tournage « sans journal » a rendu ✅ sur un film où rien n'arrive : la
# liste des points s'ouvrait, le clic sur son item tombait à côté - le seul geste du parcours qui ne
# passait pas par `viser` - et le film montrait trente-quatre secondes d'un formulaire jamais rempli,
# bouton d'import grisé jusqu'au bout. Fichier valide, montage propre, index juste.
#
# Un banc qui éprouve les gestes et pas les RÉSULTATS produit exactement le genre de faux qu'il
# existe pour empêcher. Chaque parcours doit donc exiger ce qu'il a promis de montrer.
#
# ⚠️ UNE exigence par parcours, à la fin, et non une par geste. J'ai d'abord voulu contrôler aussi
# le choix du point d'écoute en cours de route ; il a fallu trois réglages de coordonnées, parce que
# la cible se déplace avec le défilement et que la valeur d'une liste - « A1 » - fait deux
# caractères dont l'OCR ne tire rien. Or l'exigence finale la SUBSUME : sans point rattaché, l'import
# ne part pas, et « Import terminé » n'apparaît jamais. Un contrôle qui en implique un autre rend
# l'autre inutile - et deux contrôles fragiles valent moins qu'un seul qui tienne.
#
# La barre d'état est le bon endroit : elle ne défile pas. Exiger un texte dans le corps de la page
# dépendrait de la position du défilement, donc du contenu - un contrôle qui varierait avec ce qu'il
# contrôle.
exiger_a_l_ecran() { # <écran> <x> <y> <libellé attendu> [largeur]
    local ecran="$1" x="$2" y="$3" attendu="$4" largeur="${5:-$ZONE_L}" tmp lu ecart
    tmp=$(mktemp -d)
    ffmpeg -v error -y -f x11grab -video_size "${LARGEUR}x${HAUTEUR}" -i "$ecran" \
        -frames:v 1 "$tmp/ecran.png" </dev/null 2>/dev/null
    for ecart in $DECALAGES_ESSAYES; do
        lu=$(lire_zone "$tmp/ecran.png" "$((x - largeur / 2))" "$((y + ecart - ZONE_H / 2))" "$largeur")
        if libelle_correspond "$lu" "$attendu"; then
            rm -rf "$tmp"
            return 0
        fi
    done
    rm -rf "$tmp"
    echo "   - le parcours attendait « $attendu » en ($x, $y) ; l'écran y porte « $lu »"
    echo "     Le geste précédent n'a pas produit son effet : le film montrerait un écran inerte."
    return 1
}

# Relit, SUR L'ÉCRAN DU PRODUIT, le dossier que le sélecteur a retenu.
#
# ⚠️ Pourquoi ce contrôle existe. Un premier essai désignait la carte en tapant son chemin dans la
# barre d'emplacement du sélecteur (`Ctrl+L`) : le dialogue ENTRAIT dans le dossier et repartait
# avec son premier enfant, `…/sd-nominale/bruts`, un cran trop bas. Le film montrait alors une
# importation parfaitement valide, sur le mauvais dossier, et rien ne le disait. Choisir la carte
# par la barre latérale évite ce piège ; ce contrôle le prouve à chaque tournage plutôt que de
# faire confiance au geste.
#
# Ce qu'il compare : le DERNIER SEGMENT du chemin, pas le chemin entier. C'est ce segment que le
# défaut change, et c'est celui que l'OCR rend le plus sûrement - une longue ligne de chemin se lit
# avec des séparateurs incertains. Il ne dirait donc rien d'un dossier homonyme ailleurs, et ce
# n'est pas ce qu'on lui demande.
verifier_dossier_retenu() { # <écran> <x> <y> <chemin attendu> [largeur]
    local ecran="$1" x="$2" y="$3" attendu="$4" largeur="${5:-360}" tmp lu segment
    segment=$(basename "$attendu")
    tmp=$(mktemp -d)
    ffmpeg -v error -y -f x11grab -video_size "${LARGEUR}x${HAUTEUR}" -i "$ecran" \
        -frames:v 1 "$tmp/ecran.png" </dev/null 2>/dev/null
    lu=$(lire_zone "$tmp/ecran.png" "$((x - largeur / 2))" "$((y - ZONE_H / 2))" "$largeur")
    rm -rf "$tmp"

    if ! libelle_correspond "$lu" "$segment"; then
        echo "   - le sélecteur devait retenir « $attendu » ; l'écran porte « $lu »"
        echo "     Un chemin plus PROFOND que celui visé est le défaut connu : le dialogue est"
        echo "     entré dans le dossier au lieu de le retenir."
        return 1
    fi
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
    calage "$2"
}

# Le mode de calage : une image de l'écran à chaque repère, dans le dossier que CALAGE_BANC désigne.
#
# ⚠️ Il existe parce qu'écrire un scénario demande de connaître des COORDONNÉES, et qu'on ne les
# connaît qu'en regardant l'écran du tournage. Les deux premiers parcours ont été calés en insérant
# un `ffmpeg` à la main dans le corps de la fonction, puis en le déplaçant, puis en le retirant avant
# de committer - trois tournages pour deux mesures, et une fois l'insertion posée dans le mauvais
# parcours parce que l'ancre existait en plusieurs exemplaires.
#
# Chaque repère donne son image. Écrire un scénario devient : poser les repères, tourner une fois,
# ouvrir les images, écrire les coordonnées.
#
# ⚠️ Sans la variable, cette fonction ne fait RIEN et ne coûte rien : pas de saisie d'écran, pas de
# fichier. Un tournage ordinaire ne doit pas payer un outil de mise au point.
calage() { # <nom du repère>
    [ -n "${CALAGE_BANC:-}" ] || return 0
    mkdir -p "$CALAGE_BANC"
    saisir_ecran "$CALAGE_BANC/$1.png"
}

# ⚠️ La saisie est une fonction à part, et pas une ligne dans `calage`, pour que le cas puisse la
# REMPLACER. Sa première version éprouvait « sans la variable, aucun fichier n apparaît » : c était
# un faux vert, parce que sans écran `ffmpeg` échoue de toute façon et qu aucun fichier n apparaît
# non plus quand le garde a disparu. L absence d effet ne prouve pas l absence de décision. Le cas
# observe donc l APPEL, en substituant cette fonction-ci.
saisir_ecran() { # <fichier de sortie>
    ffmpeg -v error -y -f x11grab -video_size "${LARGEUR}x${HAUTEUR}" -i "$ECRAN_DU_TOURNAGE" \
        -frames:v 1 "$1" </dev/null 2>/dev/null || true
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

# La préparation du parcours d'importation. Elle N'EST PAS FILMÉE.
#
# ⚠️ Pourquoi une préparation séparée, et non un film qui montre tout. L'importation a besoin d'un
# carré ET d'un point d'écoute - l'assistant refuse de rattacher une nuit sans point. Filmer les
# deux déclarations avant d'arriver au sujet donnerait un film qui met une minute à commencer, et
# qui redit ce que `declarer-un-carre.mkv` montre déjà. Les gestes sont donc joués caméra éteinte.
#
# Ils restent VÉRIFIÉS : un `viser` qui refuse ici arrête le tournage avant qu'une pellicule soit
# entamée, ce qui est le bon moment pour s'arrêter.
preparation_importer_une_nuit() { # <écran>
    local ecran="$1"

    parcours_declarer_un_carre "$ecran" /dev/null || return 1

    # la fiche du carré, par le chevron de sa carte
    viser "$ecran" 195 180 "Carré 640380" || return 1
    sleep 1.5

    viser "$ecran" 324 237 "Ajouter un point" || return 1
    sleep 1.5
    DISPLAY="$ecran" xdotool mousemove 549 143 click 1
    # ⚠️ « A1 » sans accent ni minuscule accentuée : `xdotool type` perd les MAJUSCULES ACCENTUÉES,
    # mesuré sur « Étang » rendu « étang » au premier tournage.
    taper "$ecran" "A1"
    sleep 0.8
    # ⚠️ Zone de 110 px, et non les 200 par défaut. À 200 elle englobe « Annuler » juste à côté :
    # l'OCR en mode bloc reçoit alors deux fonds - texte sombre sur blanc ET texte blanc sur indigo -
    # et rend une bouillie qui ne contient ni l'un ni l'autre. Une zone qui chevauche deux contrôles
    # n'en lit aucun.
    viser "$ecran" 849 765 "Ajouter" 0.55 110 || return 1
    sleep 2
    return 0
}

# Le parcours « importer une nuit », deuxième de la documentation (#3887).
#
# ## Ce qu'il montre, et que rien d'autre ne montre
#
# Le geste que la documentation décrit en mots : la carte SD apparaît sous son étiquette dans la
# barre latérale du sélecteur, comme sur le poste d'un naturaliste, et l'application lit ce qu'elle
# y trouve - journal du capteur, relevé climatique, nombre d'enregistrements - avant de rien
# modifier.
#
# ## Les deux attentes de machine
#
# L'inspection du dossier et l'importation elle-même sont les deux moments où le spectateur regarde
# la machine travailler. Ils sont ENCADRÉS de repères, pour que le montage puisse les accélérer :
# c'est le seul endroit du film où le temps réel n'apprend rien.
parcours_importer_une_nuit() { # <écran> <marques> <point de montage de la carte>
    local ecran="$1" marques="$2" carte="$3"

    marque "$marques" debut
    respirer_doc 2.0                                   # la fiche du carré, telle qu'on l'a laissée

    viser "$ecran" 493 108 "Importer une nuit" || return 1
    marque "$marques" assistant
    respirer_doc 2.5                                   # l'assistant, et son étape 1 seule

    viser "$ecran" 693 210 "Parcourir" || return 1
    marque "$marques" selecteur
    respirer_doc 2.0                                   # le sélecteur s'ouvre sur un dossier vide

    # ⚠️ La carte se prend dans la BARRE LATÉRALE, sous son étiquette, et non en tapant son chemin :
    # c'est le geste réel, et c'est celui qui retient le bon niveau (cf. `verifier_dossier_retenu`).
    viser "$ecran" 168 178 "$ETIQUETTE_CARTE" 0.6 150 || return 1
    respirer_doc 1.6
    viser "$ecran" 1136 831 "Ouvrir" || return 1
    marque "$marques" inspection_debut

    respirer_doc 3.5                                   # l'application lit la carte
    marque "$marques" inspection_fin

    verifier_dossier_retenu "$ecran" 376 210 "$carte" || return 1
    respirer_doc 2.5                                   # l'étape 2 paraît, et dit ce qu'elle a trouvé

    # ⚠️ Zone élargie à 230 px : « Choisissez un point » déborde des 200 px par défaut et se lit
    # « hoisissez un point ». Mesuré au premier tournage, pas supposé.
    viser "$ecran" 801 562 "Choisissez un point" 0.55 230 || return 1
    respirer_doc 1.2
    # ⚠️ Au CLAVIER, et non au pixel. Le clic sur l'item d'une liste déroulante était le seul geste
    # du parcours à ne pas passer par un libellé vérifié : sa position dépend de celle de la liste,
    # qui dépend de la hauteur de tout ce qui la précède. Une feuille de style du socle (#4023) a
    # déplacé la liste de quelques pixels, le clic est tombé à côté, et le film a montré
    # trente-quatre secondes d'un formulaire jamais rempli - sans que rien ne rougisse.
    #
    # `Down` puis `Return` prend le premier item quelle que soit sa position. C'est aussi un geste
    # qu'un utilisateur au clavier ferait, donc rien d'artificiel à l'écran.
    DISPLAY="$ecran" xdotool key Down
    sleep 0.4
    DISPLAY="$ecran" xdotool key Return
    marque "$marques" point_choisi
    respirer_doc 1.8

    DISPLAY="$ecran" xdotool mousemove 640 700
    DISPLAY="$ecran" xdotool click --repeat 10 5
    respirer_doc 2.0                                   # le préfixe qui sera appliqué, en clair

    viser "$ecran" 201 769 "Importer cette nuit" || return 1
    marque "$marques" import_debut
    respirer_doc 8.0                                   # la machine travaille
    marque "$marques" import_fin
    # ⚠️ La BARRE D'ÉTAT, en pied de fenêtre : elle ne défile pas. Exiger le compte rendu dans le
    # corps de la page dépendrait de la position du défilement, donc du contenu - un contrôle qui
    # varierait avec ce qu'il contrôle.
    exiger_a_l_ecran "$ecran" 640 829 "Import termin" 460 || return 1
    respirer_doc 3.0                                   # ce qu'on obtient

    marque "$marques" fin
    return 0
}

# Le parcours « deux enregistreurs dans le même dossier » (#4013).
#
# ## Ce qu'il montre, et que la prose rend mal
#
# `docs/ecrans/importation.md` explique en trois paragraphes qu'un dossier mélangé déclenche un
# avertissement **sans bloquer l'import**, et que seuls les enregistrements portant la série du
# journal sont retenus. C'est une nuance : ni un refus, ni un import ordinaire. Le film la montre
# d'un coup - le bandeau **paraît** pendant l'inspection, il **nomme les deux séries**, et le bouton
# d'import reste ouvert.
#
# ## Les coordonnées ne sont PAS celles du parcours nominal
#
# ⚠️ Le bandeau ajoute de la hauteur : la liste des points d'écoute descend de 562 à 636. Un
# scénario recopié tel quel viserait le vide - ou pire, le bon libellé au mauvais endroit. C'est la
# raison pour laquelle chaque `viser` porte son libellé attendu.
parcours_melange_de_capteurs() { # <écran> <marques> <point de montage de la carte>
    local ecran="$1" marques="$2" carte="$3"

    marque "$marques" debut
    respirer_doc 2.0

    viser "$ecran" 493 108 "Importer une nuit" || return 1
    marque "$marques" assistant
    respirer_doc 2.2

    viser "$ecran" 693 210 "Parcourir" || return 1
    marque "$marques" selecteur
    respirer_doc 2.0

    viser "$ecran" 168 178 "$ETIQUETTE_CARTE" 0.6 150 || return 1
    respirer_doc 1.6
    viser "$ecran" 1136 831 "Ouvrir" || return 1
    marque "$marques" inspection_debut

    respirer_doc 3.5                                   # l'application lit la carte
    marque "$marques" inspection_fin

    verifier_dossier_retenu "$ecran" 376 210 "$carte" || return 1

    # ⚠️ Le cœur du film. Le bandeau doit être LU, pas entrevu : c'est la seule chose que ce
    # parcours apporte, et un spectateur qui le manque a regardé un import ordinaire.
    viser "$ecran" 393 437 "mélange plusieurs enregistreurs" 0.7 420 || return 1
    marque "$marques" melange_annonce
    respirer_doc 4.0                                   # le temps de lire les deux séries

    viser "$ecran" 801 636 "Choisissez un point" 0.55 230 || return 1
    respirer_doc 1.2
    # ⚠️ Au CLAVIER, et non au pixel. Le clic sur l'item d'une liste déroulante était le seul geste
    # du parcours à ne pas passer par un libellé vérifié : sa position dépend de celle de la liste,
    # qui dépend de la hauteur de tout ce qui la précède. Une feuille de style du socle (#4023) a
    # déplacé la liste de quelques pixels, le clic est tombé à côté, et le film a montré
    # trente-quatre secondes d'un formulaire jamais rempli - sans que rien ne rougisse.
    #
    # `Down` puis `Return` prend le premier item quelle que soit sa position. C'est aussi un geste
    # qu'un utilisateur au clavier ferait, donc rien d'artificiel à l'écran.
    DISPLAY="$ecran" xdotool key Down
    sleep 0.4
    DISPLAY="$ecran" xdotool key Return
    marque "$marques" point_choisi
    respirer_doc 1.8

    DISPLAY="$ecran" xdotool mousemove 640 700
    DISPLAY="$ecran" xdotool click --repeat 10 5
    respirer_doc 2.0

    # ⚠️ Et l'import se fait. C'est le fait que la documentation peine à rendre : l'avertissement
    # n'interdit rien, il informe. Un film qui s'arrêterait au bandeau dirait le contraire.
    viser "$ecran" 201 769 "Importer cette nuit" || return 1
    marque "$marques" import_debut
    respirer_doc 8.0
    marque "$marques" import_fin
    # ⚠️ La BARRE D'ÉTAT, en pied de fenêtre : elle ne défile pas. Exiger le compte rendu dans le
    # corps de la page dépendrait de la position du défilement, donc du contenu - un contrôle qui
    # varierait avec ce qu'il contrôle.
    exiger_a_l_ecran "$ecran" 640 829 "Import termin" 460 || return 1
    respirer_doc 4.0                                   # le compte rendu, et ce qu'il a écarté

    marque "$marques" fin
    return 0
}

# Le parcours « aucun journal du capteur » (#4013).
#
# ## Pourquoi celui-ci avant les autres cartes dégradées
#
# C'est l'une des trois situations que `docs/ecrans/importation.md` décrit en mots **sans aucune
# capture** - mesuré en confrontant les neuf specs aux images que la page référence vraiment. La
# prose y est donc seule à porter une nuance difficile : l'absence de journal est signalée, et
# l'import reste **possible**, en mode dégradé. Ni un refus, ni un import ordinaire.
parcours_sans_journal() { # <écran> <marques> <point de montage de la carte>
    local ecran="$1" marques="$2" carte="$3"

    marque "$marques" debut
    respirer_doc 2.0

    viser "$ecran" 493 108 "Importer une nuit" || return 1
    marque "$marques" assistant
    respirer_doc 2.2

    viser "$ecran" 693 210 "Parcourir" || return 1
    marque "$marques" selecteur
    respirer_doc 2.0

    viser "$ecran" 168 178 "$ETIQUETTE_CARTE" 0.6 150 || return 1
    respirer_doc 1.6
    viser "$ecran" 1136 831 "Ouvrir" || return 1
    marque "$marques" inspection_debut

    respirer_doc 3.5
    marque "$marques" inspection_fin

    verifier_dossier_retenu "$ecran" 376 210 "$carte" || return 1

    # ⚠️ Le cœur du film, et il tient en une ligne d'écran : l'avertissement dit « mode dégradé »
    # pendant que les deux autres contrôles restent au vert. Un spectateur qui ne le lit pas a
    # regardé un import ordinaire - or c'est précisément la différence qu'il est venu voir.
    # ⚠️ On vise un FRAGMENT, pas la phrase entière. « Aucun journal LogPR : import en mode dégradé
    # (enregistreur déduit des fichiers, paramètres limités) » fait 657 px : une fenêtre de 420
    # centrée sur elle en rate le début et lit « PR : import en mode dégradé… ». Élargir la fenêtre
    # jusqu'à 700 px la ferait déborder sur les contrôles voisins - le défaut d'à côté. Un fragment
    # court et distinctif est plus robuste que la phrase.
    viser "$ecran" 477 341 "import en mode" 0.7 420 || return 1
    marque "$marques" degrade_annonce
    respirer_doc 4.0

    viser "$ecran" 801 562 "Choisissez un point" 0.55 230 || return 1
    respirer_doc 1.2
    # ⚠️ Au CLAVIER, et non au pixel. Le clic sur l'item d'une liste déroulante était le seul geste
    # du parcours à ne pas passer par un libellé vérifié : sa position dépend de celle de la liste,
    # qui dépend de la hauteur de tout ce qui la précède. Une feuille de style du socle (#4023) a
    # déplacé la liste de quelques pixels, le clic est tombé à côté, et le film a montré
    # trente-quatre secondes d'un formulaire jamais rempli - sans que rien ne rougisse.
    #
    # `Down` puis `Return` prend le premier item quelle que soit sa position. C'est aussi un geste
    # qu'un utilisateur au clavier ferait, donc rien d'artificiel à l'écran.
    DISPLAY="$ecran" xdotool key Down
    sleep 0.4
    DISPLAY="$ecran" xdotool key Return
    respirer_doc 1.2
    marque "$marques" point_choisi
    respirer_doc 1.2

    DISPLAY="$ecran" xdotool mousemove 640 700
    DISPLAY="$ecran" xdotool click --repeat 10 5
    respirer_doc 2.0

    # ⚠️ Et l'import se fait. C'est tout ce que ce film a à dire : l'avertissement n'interdit rien.
    viser "$ecran" 201 769 "Importer cette nuit" || return 1
    marque "$marques" import_debut
    respirer_doc 8.0
    marque "$marques" import_fin
    # ⚠️ La BARRE D'ÉTAT, en pied de fenêtre : elle ne défile pas. Exiger le compte rendu dans le
    # corps de la page dépendrait de la position du défilement, donc du contenu - un contrôle qui
    # varierait avec ce qu'il contrôle.
    exiger_a_l_ecran "$ecran" 640 829 "Import termin" 460 || return 1
    respirer_doc 4.0

    marque "$marques" fin
    return 0
}

# Les parcours d'import qui n'exigent RIEN de leur résultat, nommés.
#
# ⚠️ Ce garde remplace un compteur verrouillé qui cherchait « exiger_a_l_ecran … 640 829 » et exigeait
# d'en trouver exactement trois. Il disait donc « trois parcours ont une exigence à cette
# coordonnée-là », ce qui n'est pas la règle qu'on voulait tenir, et il rougissait dès qu'un parcours
# en portait DEUX - ce que fait « multi-nuits », qui exige l'import ET le compte de passages.
#
# La règle réelle : un parcours qui désigne une carte (`verifier_dossier_retenu`) va jusqu'à un
# résultat, donc il doit l'exiger. Ce qui compte n'est ni le nombre de parcours ni l'endroit de
# l'exigence, c'est qu'aucun n'en soit dépourvu.
parcours_sans_exigence() { # <fichier du banc>
    local fichier="$1" nom corps
    for nom in $(grep -o '^parcours_[a-z_]*()' "$fichier" | tr -d '()'); do
        corps=$(sed -n "/^$nom() {/,/^}/p" "$fichier")
        case "$corps" in
            *verifier_dossier_retenu*)
                case "$corps" in
                    *exiger_a_l_ecran*) ;;
                    *) printf '%s\n' "$nom" ;;
                esac ;;
        esac
    done
}

# Le parcours « grosse carte » (#4055).
#
# ## Ce qu'il montre, et ce qu'il devait montrer
#
# ⚠️ #4013 attendait de ce cas « la progression et le parallélisme, invisibles sur une capture ».
# Cette attente est FAUSSE, et c'est le tournage qui l'a montrée : entre le clic sur « Importer » et
# une demi-seconde plus tard, le compte rendu affiche déjà 60/60 (100 %) et les deux barres pleines.
# Mesuré en échantillonnant le film brut toutes les demi-secondes, pas déduit.
#
# Soixante WAV de 0,1 s pèsent quatre méga-octets : il n'y a pas de charge, donc pas de progression à
# filmer. Le premier montage donnait trente-cinq secondes d'un écran figé sur un import déjà fini,
# sous un titre qui promettait du mouvement.
#
# Ce que le film montre donc, et qui vaut d'être vu : le volume est ANNONCÉ - « 60 enregistrement(s)
# WAV détecté(s) » -, et l'import est **instantané**, avec son décompte complet et le volume lu sur
# la carte comme écrit sur le disque. Un lecteur qui s'attend à patienter apprend qu'il n'a pas à le
# faire.
#
# ⚠️ Ses repères d'import s'appellent tout de même `import_commence` et `import_acheve`, et non
# `import_debut` / `import_fin` : le montage accélère les plages déclarées par une paire
# `<nom>_debut` / `<nom>_fin`, et accélérer un import déjà instantané le rendrait invisible.
# L'inspection, elle, garde sa paire - scruter soixante fichiers est une attente qui n'apprend rien.
parcours_grosse_carte() { # <écran> <marques> <point de montage de la carte>
    local ecran="$1" marques="$2" carte="$3"

    marque "$marques" debut
    respirer_doc 2.0

    viser "$ecran" 493 108 "Importer une nuit" || return 1
    marque "$marques" assistant
    respirer_doc 2.2

    viser "$ecran" 693 210 "Parcourir" || return 1
    marque "$marques" selecteur
    respirer_doc 2.0

    viser "$ecran" 168 178 "$ETIQUETTE_CARTE" 0.6 150 || return 1
    respirer_doc 1.6
    viser "$ecran" 1136 831 "Ouvrir" || return 1
    marque "$marques" inspection_debut

    respirer_doc 5.0
    marque "$marques" inspection_fin

    verifier_dossier_retenu "$ecran" 376 210 "$carte" || return 1

    # ⚠️ Le volume, annoncé par l'inspection. Le fragment se vise à SON abscisse, pas au centre de sa
    # ligne : c'est le piège qui a fait rougir deux exigences du parcours « multi-nuits » alors que
    # l'écran portait bien ce qu'elles attendaient.
    exiger_a_l_ecran "$ecran" 224 393 "60 enregistrement" 220 || return 1
    marque "$marques" volume_annonce
    respirer_doc 3.0

    viser "$ecran" 801 562 "Choisissez un point" 0.55 230 || return 1
    respirer_doc 1.2
    DISPLAY="$ecran" xdotool key Down
    sleep 0.4
    DISPLAY="$ecran" xdotool key Return
    marque "$marques" point_choisi
    respirer_doc 1.5

    DISPLAY="$ecran" xdotool mousemove 640 700
    DISPLAY="$ecran" xdotool click --repeat 10 5
    respirer_doc 2.0

    viser "$ecran" 201 769 "Importer" || return 1
    marque "$marques" import_commence
    respirer_doc 6.0
    marque "$marques" import_acheve

    exiger_a_l_ecran "$ecran" 520 829 "Import termin" 460 || return 1
    exiger_a_l_ecran "$ecran" 520 829 "60 s" 460 || return 1
    respirer_doc 4.0

    marque "$marques" fin
    return 0
}

# Le parcours « plusieurs nuits » (#4055).
#
# ## Ce qu'il montre, et qu'aucune image fixe ne montre
#
# Une carte laissée tourner trois nuits donne trois passages distincts. La table des nuits paraît
# pendant l'inspection, et les numéros de passage y sont proposés d'office : 2, 3, 4. Décocher une
# nuit ne la retire pas seulement de la liste, elle **renumérote les autres** pour qu'ils restent
# consécutifs. C'est ce mouvement-là que la capture `apercu-import-multi-nuits.png` ne peut pas
# rendre : elle montre un état, pas une conséquence.
#
# ## Comment ce parcours atteint une case à cocher
#
# La case « Importer » d'une ligne n'a aucun texte. La viser au pixel serait le clic aveugle qui a
# déjà rendu un film de trente-quatre secondes où rien n'arrive. `viser_a_cote_de` lit donc la DATE
# de la ligne - « 2026-07-04 », que rien d'autre ne porte à l'écran - et clique à l'abscisse de la
# case, à l'ordonnée corrigée du même balayage que `viser`. Si la table glisse, la ligne suit.
#
# ⚠️ Le balayage vaut 24 px de part et d'autre, pour un pas de ligne de 32 : il pourrait donc lire
# une ligne voisine. Il ne s'y trompe pas parce que les trois dates DIFFÈRENT - c'est ce qui rend ce
# repérage sûr ici, et ce qui ne le rendrait pas sûr sur une colonne aux valeurs répétées.
#
# ## Ce qu'il exige
#
# Le compte rendu final annonce « Import terminé : N passage(s) créé(s) ». Avec une nuit décochée sur
# trois, N vaut 2. C'est une conséquence que SEUL un décochage effectif produit : un clic tombé à
# côté rendrait 3, et le parcours rougirait au lieu de publier un film qui montre autre chose que ce
# qu'il annonce.
parcours_multi_nuits() { # <écran> <marques> <point de montage de la carte>
    local ecran="$1" marques="$2" carte="$3"

    marque "$marques" debut
    respirer_doc 2.0

    viser "$ecran" 493 108 "Importer une nuit" || return 1
    marque "$marques" assistant
    respirer_doc 2.2

    viser "$ecran" 693 210 "Parcourir" || return 1
    marque "$marques" selecteur
    respirer_doc 2.0

    viser "$ecran" 168 178 "$ETIQUETTE_CARTE" 0.6 150 || return 1
    respirer_doc 1.6
    viser "$ecran" 1136 831 "Ouvrir" || return 1
    marque "$marques" inspection_debut

    respirer_doc 3.5
    marque "$marques" inspection_fin

    verifier_dossier_retenu "$ecran" 376 210 "$carte" || return 1

    # ⚠️ La phrase qui annonce le découpage, avant la table elle-même. Elle est longue - « Plusieurs
    # nuits détectées sur cette carte : chacune deviendra un passage distinct… » - donc on en vise un
    # fragment court et distinctif, comme pour le mode dégradé.
    # ⚠️ Le fragment se vise à SON abscisse, pas au centre de sa ligne. La phrase « Plusieurs nuits
    # détectées sur cette carte : chacune deviendra un passage distinct… » fait 969 px de large. Une
    # fenêtre de 420 px centrée au milieu de la ligne lit « sur cette carte : chacune deviendra » et
    # ne voit jamais le début : le premier tournage a refusé pour ça, en accusant le geste précédent
    # de n'avoir rien produit alors que la table était bien là.
    exiger_a_l_ecran "$ecran" 215 440 "nuits détectées" 220 || return 1
    marque "$marques" table_parait
    respirer_doc 4.0

    # Les trois nuits sont là, chacune avec sa date.
    exiger_a_l_ecran "$ecran" 248 494 "2026-07-03" 150 || return 1
    exiger_a_l_ecran "$ecran" 248 558 "2026-07-05" 150 || return 1
    respirer_doc 2.5

    # ⚠️ Le point d'écoute AVANT le décochage, et l'ordre n'est pas indifférent. Tant qu'aucun point
    # n'est choisi, la colonne « Passage n° » porte « — » sur les trois lignes : il n'y a pas encore
    # de numéro à attribuer, donc rien à renuméroter. Décocher d'abord aurait filmé un tiret qui
    # remplace un tiret. Mesuré sur une capture du tournage, pas déduit.
    viser "$ecran" 801 735 "Choisissez un point" 0.55 230 || return 1
    respirer_doc 1.2
    DISPLAY="$ecran" xdotool key Down
    sleep 0.4
    DISPLAY="$ecran" xdotool key Return
    marque "$marques" point_choisi
    respirer_doc 3.0

    # ⚠️ LE geste du film : décocher la nuit du milieu, repérée par SA date. La case n'a aucun texte,
    # donc `viser_a_cote_de` lit « 2026-07-04 » et clique à l'abscisse de la case, à l'ordonnée que le
    # balayage a corrigée.
    viser_a_cote_de "$ecran" 248 526 "2026-07-04" 172 0.7 150 || return 1
    marque "$marques" nuit_decochee
    respirer_doc 5.0

    DISPLAY="$ecran" xdotool mousemove 640 700
    DISPLAY="$ecran" xdotool click --repeat 14 5
    respirer_doc 2.0

    viser "$ecran" 201 769 "Importer" || return 1
    marque "$marques" import_debut
    respirer_doc 10.0
    marque "$marques" import_fin

    # ⚠️ DEUX exigences, et la seconde porte tout. « Import terminé » prouve que l'import a eu lieu ;
    # « 2 passage » prouve que le décochage a PORTÉ. Sans elle, un clic tombé à côté de la case
    # publierait un film où trois nuits s'importent sous un titre qui promet qu'on en retire une.
    # ⚠️ Et le MÊME piège qu'en haut, une seconde fois dans le même parcours : la fenêtre de lecture
    # est centrée sur l'abscisse qu'on lui donne. Le compte rendu multi-nuits est long - « Import
    # terminé : 2 passage(s) créé(s) (nuits du 03/07/2026 au 05/07/2026), 4 séquence(s) produite(s). »
    # - et commence à 358 px. Une fenêtre de 460 px centrée au milieu de la barre lit à partir de
    # 410 : elle voit le compte, jamais le « Import terminé ». Le parcours accusait donc l'import de
    # n'avoir rien fait alors qu'il venait de réussir. On centre sur le DÉBUT de la phrase.
    exiger_a_l_ecran "$ecran" 520 829 "Import termin" 460 || return 1
    exiger_a_l_ecran "$ecran" 520 829 "2 passage" 460 || return 1
    respirer_doc 4.0

    marque "$marques" fin
    return 0
}

# Le parcours « journal illisible » (#4013).
#
# ## Le seul de la famille qui ne se termine PAS par un import
#
# Les autres films d'importation montrent une nuance : l'application prévient et laisse passer. Ici
# elle **refuse**, et c'est ce refus qu'il faut montrer - l'étape « 2. Inspection » ne paraît même
# pas, l'assistant reste à « 1 » puis « 3 », et le bouton d'import demeure fermé.
#
# ⚠️ Son exigence de fin n'est donc pas « Import terminé » mais le message de refus lui-même. Un
# parcours qui exigerait l'import ici échouerait toujours ; un parcours qui n'exigerait rien
# publierait un film montrant un écran quelconque.
#
# ⚠️ Une observation à ne pas perdre : le motif du refus s'affiche à côté du BOUTON, tout en bas,
# à quelque sept cents pixels sous la zone d'inspection où l'utilisateur vient de regarder. Qui
# désigne sa carte et lit le haut de la page voit « 1. » puis « 3. » sans explication. Ce n'est pas
# l'objet de ce film ; c'est noté ici pour qui passera après.
parcours_journal_illisible() { # <écran> <marques> <point de montage de la carte>
    local ecran="$1" marques="$2" carte="$3"

    marque "$marques" debut
    respirer_doc 2.0

    viser "$ecran" 493 108 "Importer une nuit" || return 1
    marque "$marques" assistant
    respirer_doc 2.2

    viser "$ecran" 693 210 "Parcourir" || return 1
    marque "$marques" selecteur
    respirer_doc 2.0

    viser "$ecran" 168 178 "$ETIQUETTE_CARTE" 0.6 150 || return 1
    respirer_doc 1.6
    viser "$ecran" 1136 831 "Ouvrir" || return 1
    marque "$marques" inspection_debut

    respirer_doc 3.5
    marque "$marques" inspection_fin

    verifier_dossier_retenu "$ecran" 376 210 "$carte" || return 1
    respirer_doc 2.0                                   # l'étape 2 ne paraît pas : elle a échoué

    # ⚠️ Le cœur du film, et l'exigence de ce parcours : le motif du refus, nommé.
    exiger_a_l_ecran "$ecran" 515 828 "inexploitable" 460 || return 1
    marque "$marques" refus_annonce
    respirer_doc 5.0                                   # le temps de lire ce qui cloche

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

# Ce que le banc sait tourner. Un parcours porte son nom de fichier, la durée de pellicule qu'on
# lui accorde, et s'il lui faut une carte SD montée.
#
# ⚠️ La pellicule est FIXÉE d'avance et généreuse : un `ffmpeg` tué sans ménagement ne finalise pas
# son index, et le film est alors irrécupérable. Le montage coupera ce qui dépasse.
parcours_connu() { # <nom>
    case "$1" in
        declarer-un-carre) printf '45\tnon\n' ;;
        importer-une-nuit) printf '120\trecette/fixtures/spec/sd-nominale.yaml\n' ;;
        melange-de-capteurs) printf '120\trecette/fixtures/spec/sd-melange.yaml\n' ;;
        sans-journal) printf '120\trecette/fixtures/spec/sd-sans-journal.yaml\n' ;;
        journal-illisible) printf '90\trecette/fixtures/spec/sd-journal-corrompu.yaml\n' ;;
        multi-nuits) printf '160\trecette/fixtures/spec/sd-multi-nuits.yaml\n' ;;
        grosse-carte) printf '150\trecette/fixtures/spec/sd-grosse.yaml\n' ;;
        *) return 1 ;;
    esac
}

tourner() { # [nom du parcours] [sortie]
    local nom="${1:-declarer-un-carre}"
    local fiche pellicule spec_carte
    if ! fiche=$(parcours_connu "$nom"); then
        echo "❌ Parcours inconnu : « $nom »."
        echo "   Connus : declarer-un-carre, importer-une-nuit, melange-de-capteurs, sans-journal,"
        echo "            journal-illisible, multi-nuits, grosse-carte."
        return 1
    fi
    pellicule=$(printf '%s' "$fiche" | cut -f1)
    spec_carte=$(printf '%s' "$fiche" | cut -f2)

    local sortie="${2:-$RACINE/target/doc-video/$nom.mkv}"
    local bac ecran="$ECRAN_DU_TOURNAGE" jar code=0
    local atelier="" carte="" point="" peripherique=""
    jar=$(resoudre_le_jar)
    bac=$(mktemp -d)

    echo "Conditions du tournage"
    if [ "$spec_carte" = non ]; then verifier_outils || code=1; else verifier_outils --avec-carte || code=1; fi
    verifier_le_jar "$jar" || code=1
    verifier_bac_jetable "$bac" || code=1
    if [ "$code" -ne 0 ]; then
        echo "❌ Le tournage est refusé : voir ci-dessus."
        rm -rf "$bac"
        return 1
    fi
    echo "   ✔ outils, jar et bac jetable"

    if [ "$spec_carte" != non ]; then
        atelier=$(mktemp -d)
        if ! carte=$(preparer_la_carte "$atelier" "$spec_carte") \
            || ! image_de_la_carte "$carte" "$atelier/carte.img" \
            || ! fiche=$(monter_la_carte "$atelier/carte.img"); then
            echo "❌ La carte du parcours n'a pas pu être montée."
            rm -rf "$bac" "$atelier"
            return 1
        fi
        point=$(printf '%s' "$fiche" | cut -f1)
        peripherique=$(printf '%s' "$fiche" | cut -f2)
        echo "   ✔ carte montée : $point"
    fi

    mkdir -p "$(dirname "$sortie")"
    Xvfb "$ecran" -screen 0 "${LARGEUR}x${HAUTEUR}x24" >/dev/null 2>&1 &
    local xvfb=$!
    sleep 2
    DISPLAY="$ecran" openbox --sm-disable >/dev/null 2>&1 &
    local wm=$!
    sleep 1

    # ⚠️ Dans un SOUS-SHELL : `poser_environnement_jetable` exporte HOME et change de dossier, et
    # le banc lui-même a besoin des siens - ne serait-ce que pour écrire le film à sa destination.
    (
        poser_environnement_jetable "$bac" || exit 1
        DISPLAY="$ecran" java --enable-native-access=ALL-UNNAMED \
            -Dvigiechiro.workspace="$bac" -jar "$jar"
    ) >"$bac/produit.log" 2>&1 &
    local produit=$!

    if attendre_la_fenetre "$ecran"; then
        verifier_dimensions_honorees "$ecran" || code=1
        local marques="${sortie%.mkv}.marques.tsv"
        : > "$marques"

        # ⚠️ La préparation tourne CAMÉRA ÉTEINTE, et son refus arrête le tournage avant qu'une
        # pellicule soit entamée - le bon moment pour s'arrêter.
        if [ "$code" -eq 0 ] && [ "$spec_carte" != non ]; then
            echo "   … préparation (non filmée) : un carré et son point d'écoute"
            preparation_importer_une_nuit "$ecran" || code=1
        fi

        if [ "$code" -eq 0 ]; then
            filmer "$ecran" "$sortie" "$pellicule" &
            local camera=$!
            case "$nom" in
                declarer-un-carre) parcours_declarer_un_carre "$ecran" "$marques" || code=1 ;;
                importer-une-nuit) parcours_importer_une_nuit "$ecran" "$marques" "$point" || code=1 ;;
                melange-de-capteurs) parcours_melange_de_capteurs "$ecran" "$marques" "$point" || code=1 ;;
                sans-journal) parcours_sans_journal "$ecran" "$marques" "$point" || code=1 ;;
                journal-illisible) parcours_journal_illisible "$ecran" "$marques" "$point" || code=1 ;;
                multi-nuits) parcours_multi_nuits "$ecran" "$marques" "$point" || code=1 ;;
                grosse-carte) parcours_grosse_carte "$ecran" "$marques" "$point" || code=1 ;;
            esac
            wait "$camera"
            # ⚠️ APRÈS `wait`, et l'ordre est tout. `t0` se calcule « instant d'arrêt moins durée du
            # fichier » : il faut donc l'instant où la CAMÉRA s'est arrêtée, pas celui où le parcours
            # s'est terminé. Marqué avant le `wait`, les repères se convertissaient en 21,3 s à 45,0 s -
            # un parcours qui n'aurait commencé qu'à la moitié d'un film qu'il occupe en entier. Le
            # montage aurait coupé les mauvaises plages, et le film serait resté parfaitement valide.
            marque "$marques" arret

            # ⚠️ Le montage sort en MP4, le brut reste en MKV, et ce n'est pas une inconséquence.
            # Le MKV protège le TOURNAGE : un `ffmpeg` tué sans ménagement laisse un MP4 sans index,
            # donc irrécupérable - le piège de #2191. Le montage, lui, est un ré-encodage propre qui
            # va jusqu'à son terme, et il doit se lire dans un navigateur : aucun n'affiche le
            # Matroska.
            if monter "$sortie" "$marques" "${sortie%.mkv}-monte.mp4"; then
                ecrire_index "$(dirname "$sortie")" "$(dirname "$sortie")/index.md"
            else
                code=1
            fi
        fi
    else
        echo "❌ Le produit n'a pas ouvert de fenêtre : rien à filmer."
        echo "   Journal : $bac/produit.log"
        code=1
    fi

    kill "$produit" "$wm" "$xvfb" 2>/dev/null
    wait 2>/dev/null
    [ -n "$point" ] && demonter_la_carte "$point" "$peripherique"
    if [ "$code" -eq 0 ]; then
        echo "✅ $sortie ($(du -h "$sortie" | cut -f1))"
        echo "   marques : $(wc -l < "${sortie%.mkv}.marques.tsv") repères"
    fi
    rm -rf "$bac" ${atelier:+"$atelier"}
    return "$code"
}

# ---------------------------------------------------------------------------------------------
# La carte SD du parcours d'importation
# ---------------------------------------------------------------------------------------------

# ⚠️ La spec de carte est une propriété du PARCOURS, pas du banc. Elle a d'abord été un global
# (`SPEC_CARTE`), ce qui convenait tant qu'un seul film montait une carte ; le deuxième l'aurait
# fait mentir en silence - le film du mélange aurait été tourné sur la carte nominale, et rien
# n'aurait signalé qu'il ne montre pas ce que son nom annonce. Elle se lit maintenant dans
# `parcours_connu`. Les neuf cartes dégradées de `recette/fixtures/spec/` attendent leur scénario
# (#4013).

# Matérialise la carte SD, en octets DÉTERMINISTES, par le goal que le dépôt porte déjà.
#
# ## Ce que cela remplace
#
# La vidéo de #2191 filmait une **vraie** carte, montée par `udisksctl` sous `/media`. C'était le bon
# choix pour convaincre un relecteur Flathub : la scène montrait le geste réel d'un naturaliste.
#
# ⚠️ Pour un banc versionné, c'est l'inverse qu'il faut : une carte qu'on refabrique à l'identique.
# `GenerateurCartesSD` rend les mêmes octets d'une exécution à l'autre. Depuis #3996 ces octets
# sont versés dans une image FAT montée en boucle, pour que le film montre le chemin qu'un
# naturaliste voit vraiment : les deux se cumulent, la fixture reste déterministe.
#
# ⚠️ J'avais chiffré ce travail à « trois mécanismes neufs » avant de regarder. Le pom porte le goal
# `generer-sd` depuis longtemps, documenté dans `dev-docs/recette/fixtures.md`. Un coût supposé, et
# faux d'un ordre de grandeur.
# Le contrôle, SÉPARÉ de la fabrication pour pouvoir être éprouvé sans lancer Maven.
#
# ⚠️ Il porte sur ce qui EXISTE, pas sur le code de retour de Maven : `exec:java` rend zéro sur bien
# des façons de ne rien produire. Une carte utilisable a un dossier, des bruts, et au moins un WAV -
# sans quoi le film montrerait une importation qui ne trouve rien, et le fichier serait valide.
carte_utilisable() { # <dossier de destination>
    local dest="$1" carte bruts
    carte=$(find "$dest" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1)
    if [ -z "$carte" ]; then
        echo "   - aucune carte matérialisée sous $dest" >&2
        return 1
    fi
    if [ ! -d "$carte/bruts" ]; then
        echo "   - la carte $carte n'a pas de dossier « bruts »" >&2
        return 1
    fi
    bruts=$(find "$carte/bruts" -name '*.wav' 2>/dev/null | wc -l)
    if [ "$bruts" -eq 0 ]; then
        echo "   - la carte $carte ne contient aucun brut" >&2
        return 1
    fi
    printf '%s' "$carte"
    return 0
}

# Matérialise la carte, puis la soumet au contrôle ci-dessus.
preparer_la_carte() { # <dossier de destination> <spec, relative à la racine>
    local dest="$1" relative="${2:-}" spec
    if [ -z "$relative" ]; then
        echo "   - aucune spec de carte demandée" >&2
        return 1
    fi
    spec="$RACINE/$relative"
    if [ ! -f "$spec" ]; then
        echo "   - spec de carte introuvable : $spec" >&2
        return 1
    fi
    ( cd "$RACINE" && ./mvnw -q test-compile exec:java@generer-sd \
        -Dexec.args="$relative $dest" ) >/dev/null 2>&1
    carte_utilisable "$dest"
}

# ---------------------------------------------------------------------------------------------
# La carte montée là où une vraie carte se monte
# ---------------------------------------------------------------------------------------------

# L'étiquette de NOTRE image. Elle sert deux fois : à ce que le film montre un nom de volume
# plausible, et à ce que le démontage ne s'en prenne qu'à elle.
ETIQUETTE_CARTE="VIGIECHIRO"

# ⚠️ Pourquoi monter, plutôt que de désigner un dossier.
#
# Un banc de recette se moque de l'endroit : il vérifie un comportement. Un film de documentation,
# lui, EST le livrable, et son réalisme en fait partie. Un naturaliste branche sa carte et la voit
# apparaître sous `/media/<lui>/<étiquette>` ; il ne voit jamais `/tmp/quelquechose`.
#
# J'avais écarté ce montage en jugeant sur le déterminisme du banc - le bon critère pour la recette,
# le mauvais ici. Les deux se cumulent sans rien perdre : `GenerateurCartesSD` produit les octets,
# on les verse dans une image FAT étiquetée, et `udisksctl` la monte là où une vraie carte se monte.
image_de_la_carte() { # <dossier de la carte> <image à écrire>
    local carte="$1" image="$2" bruts
    # ⚠️ Vérifier la SOURCE avant de fabriquer. Sans ce contrôle, une carte absente - `/tmp` nettoyé
    # entre deux tournages, cela s'est produit - donne une image FAT parfaitement valide et VIDE.
    # Le film montrerait alors « 0 enregistrement détecté », et le fichier serait irréprochable.
    if [ ! -d "$carte" ]; then
        echo "   - carte source introuvable : $carte" >&2
        return 1
    fi
    bruts=$(find "$carte" -name '*.wav' 2>/dev/null | wc -l)
    if [ "$bruts" -eq 0 ]; then
        echo "   - carte source sans aucun brut : $carte" >&2
        return 1
    fi
    rm -f "$image"
    truncate -s 64M "$image" || return 1
    mkfs.vfat -n "$ETIQUETTE_CARTE" "$image" >/dev/null 2>&1 || return 1
    ( cd "$carte" && mcopy -i "$image" -s ./* :: ) >/dev/null 2>&1 || {
        # `cd` puis `./*` : `mcopy -s "$carte"/*` recopierait le chemin absolu dans l'image.
        mcopy -i "$image" -s "$carte"/* :: >/dev/null 2>&1 || return 1
    }
    return 0
}

# ⚠️ Le garde qui protège les cartes de l'utilisateur.
#
# Cette machine porte de VRAIES cartes montées - au moment d'écrire ceci, `/run/media/<user>/72CA-9E54`.
# Un banc qui démonterait « le dernier volume apparu » pourrait s'en prendre à elles. On n'agit donc
# que sur un point de montage dont le nom est NOTRE étiquette, et sur rien d'autre.
notre_montage() { # <point de montage>
    case "$(basename "${1:-}")" in
        "$ETIQUETTE_CARTE") return 0 ;;
        *) return 1 ;;
    esac
}

# Monte l'image et rend le point de montage. `udisks2` monte de lui-même après `loop-setup` : on
# n'appelle donc pas `udisksctl mount`, qui répondrait « already mounted ».
monter_la_carte() { # <image>
    local image="$1" sortie dev point i
    sortie=$(udisksctl loop-setup -f "$image" 2>&1) || { echo "   - loop-setup refusé : $sortie" >&2; return 1; }
    dev=$(printf '%s' "$sortie" | grep -oE '/dev/loop[0-9]+')
    [ -n "$dev" ] || { echo "   - aucun périphérique boucle obtenu" >&2; return 1; }
    for i in 1 2 3 4 5 6 7 8; do
        point=$(lsblk -no MOUNTPOINT "$dev" 2>/dev/null | grep -v '^$' | head -1)
        [ -n "$point" ] && break
        sleep 1
    done
    if [ -z "$point" ]; then
        echo "   - $dev n'a été monté nulle part après 8 s" >&2
        return 1
    fi
    if ! notre_montage "$point"; then
        echo "   - $point ne porte pas l'étiquette $ETIQUETTE_CARTE : on n'y touche pas" >&2
        return 1
    fi
    printf '%s\t%s' "$point" "$dev"
    return 0
}

# ⚠️ On démonte, on ne DÉTACHE pas. `udisksctl loop-delete` réclame une élévation par polkit : sur
# un poste graphique, il fait surgir une boîte d'authentification chez qui travaille - vécu. Et il
# est inutile : `loop-setup` pose l'autoclear, le périphérique s'en va de lui-même au démontage.
demonter_la_carte() { # <point de montage> <périphérique>
    local point="$1" dev="$2"
    notre_montage "$point" || { echo "   - refus de démonter $point : ce n'est pas notre carte" >&2; return 1; }
    udisksctl unmount -b "$dev" >/dev/null 2>&1
    sleep 1
    if losetup -l 2>/dev/null | grep -q "^$dev "; then
        echo "   ⚠️ $dev subsiste : le détacher demanderait une élévation, on s'en abstient." >&2
    fi
    return 0
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
# Les plages où l'on regarde la machine travailler, accélérées au montage.
#
# ⚠️ Pourquoi celles-là et pas le reste. Un film de documentation se juge au temps qu'il réclame.
# Les gestes doivent garder leur rythme - le spectateur les refera -, mais l'inspection de la carte
# et l'import lui-même n'apprennent rien en temps réel : ce sont des barres qui avancent.
#
# ⚠️ Elles s'accélèrent, elles ne se COUPENT PAS. Un import qui disparaîtrait au montage laisserait
# croire qu'il est instantané ; le lecteur qui attend huit secondes devant son écran croirait alors
# que quelque chose ne va pas chez lui.
FACTEUR_ACCELERATION=4

# Les plages, en positions du film COUPÉ (donc après retrait de l'origine), une par ligne.
#
# Une plage se déclare par deux repères jumeaux, `<nom>_debut` et `<nom>_fin`. Un `_debut` sans son
# `_fin` est ignoré : mieux vaut un film au rythme réel qu'un montage qui accélère jusqu'à la fin
# sur un repère manquant.
plages_a_accelerer() { # <marques> <t0> <coupe>
    local marques="$1" t0="$2" coupe="$3" nom base d f
    while IFS=$'\t' read -r _ nom; do
        case "$nom" in
            *_debut) base="${nom%_debut}" ;;
            *) continue ;;
        esac
        grep -q "	${base}_fin$" "$marques" || continue
        d=$(instant_du_repere "$marques" "$t0" "${base}_debut")
        f=$(instant_du_repere "$marques" "$t0" "${base}_fin")
        [ -n "$d" ] && [ -n "$f" ] || continue
        LC_NUMERIC=C awk -v d="$d" -v f="$f" -v c="$coupe" \
            'BEGIN{a = d - c; b = f - c; if (a < 0) a = 0; if (b > a) printf "%.3f\t%.3f\n", a, b}'
    done < "$marques"
}

# Le filtre qui coupe le film et accélère ses plages de machine. Vide s'il n'y a rien à accélérer :
# le montage reprend alors sa coupe simple, plus rapide et plus sûre.
#
# ⚠️ Il découpe en segments ALTERNÉS - normal, accéléré, normal… - et les recolle. Une seule passe,
# donc pas de fichiers intermédiaires : le brut n'est lu qu'une fois, et c'est ce que `concat`
# attend.
filtre_de_montage() { # <début> <fin> <plages>
    local debut="$1" fin="$2" plages="$3"
    [ -n "$plages" ] || return 0
    LC_NUMERIC=C awk -v d="$debut" -v f="$fin" -v k="$FACTEUR_ACCELERATION" '
        { a[NR] = $1; b[NR] = $2 }
        END {
            n = 0; curseur = 0; filtre = ""; enchainement = ""
            for (i = 1; i <= NR; i++) {
                if (a[i] > curseur) {
                    filtre = filtre sprintf("[0:v]trim=start=%.3f:end=%.3f,setpts=PTS-STARTPTS[s%d];", d + curseur, d + a[i], n)
                    enchainement = enchainement sprintf("[s%d]", n); n++
                }
                filtre = filtre sprintf("[0:v]trim=start=%.3f:end=%.3f,setpts=(PTS-STARTPTS)/%d[s%d];", d + a[i], d + b[i], k, n)
                enchainement = enchainement sprintf("[s%d]", n); n++
                curseur = b[i]
            }
            if (d + curseur < f) {
                filtre = filtre sprintf("[0:v]trim=start=%.3f:end=%.3f,setpts=PTS-STARTPTS[s%d];", d + curseur, f, n)
                enchainement = enchainement sprintf("[s%d]", n); n++
            }
            printf "%s%sconcat=n=%d:v=1:a=0[sortie]", filtre, enchainement, n
        }' <<< "$plages"
}

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

    local plages filtre
    plages=$(plages_a_accelerer "$marques" "$t0" "$debut")
    filtre=$(filtre_de_montage "$debut" "$fin" "$plages")

    if [ -n "$filtre" ]; then
        ffmpeg -nostdin -v error -i "$brut" -filter_complex "$filtre" -map "[sortie]" -an \
            -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -movflags +faststart \
            -y "$sortie" 2>/dev/null
    else
        ffmpeg -nostdin -v error -i "$brut" -ss "$debut" -to "$fin" -an \
            -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -movflags +faststart \
            -y "$sortie" 2>/dev/null
    fi

    # ⚠️ Le contrôle, et il porte sur le RÉSULTAT, pas sur les repères. Un montage qui viserait à
    # côté rendrait un film noir - valide, lisible, et vide. C'est la seule chose qu'on ne peut pas
    # laisser passer en silence.
    part=$(part_utile "$sortie")
    if LC_NUMERIC=C awk -v p="$part" 'BEGIN{exit !(p < 0.5)}'; then
        LC_NUMERIC=C printf '   - le montage vise à côté : %.0f %% seulement du film coupé montre quelque chose\n' \
            "$(LC_NUMERIC=C awk -v p="$part" 'BEGIN{print p * 100}')"
        return 1
    fi
    # ⚠️ La durée annoncée se MESURE sur le fichier produit. Calculée comme « fin moins début »,
    # elle ignorait l'accélération des plages de machine et annonçait un film plus long que celui
    # qu'on venait d'écrire - un chiffre faux sur la ligne même qui dit que le montage a réussi.
    local montee accelerees
    montee=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$sortie" 2>/dev/null)
    accelerees=$(printf '%s' "$plages" | grep -c . || true)
    LC_NUMERIC=C printf '   ✔ montage : %.1f s -> %.1f s, %.0f %% d\u2019images utiles' \
        "$duree" "$montee" "$(LC_NUMERIC=C awk -v p="$part" 'BEGIN{print p * 100}')"
    if [ "${accelerees:-0}" -gt 0 ]; then
        printf ' (%s plage(s) de machine accélérée(s) x%s)' "$accelerees" "$FACTEUR_ACCELERATION"
    fi
    echo
    return 0
}

# ---------------------------------------------------------------------------------------------
# L'index
# ---------------------------------------------------------------------------------------------

# L'index se DÉRIVE, il ne se tient pas à la main : la durée vient du fichier, les étapes des
# marqueurs, la part d'images utiles de la mesure. C'est la leçon de #3885, où une page portait
# trois inventaires du même fait et où deux avaient dérivé.
#
# ⚠️ Ce que l'index dit de lui-même compte autant que ce qu'il liste. Un lecteur doit savoir que ce
# film prouve un ENCHAÎNEMENT et rien d'autre : qu'il soit clair, lisible, bien rythmé, aucune
# machine ne le dit. C'est le troisième état de l'ADR 3764, transposé à la documentation.
# La position d'un repère dans le film MONTÉ, une fois les plages de machine accélérées.
#
# ⚠️ Sans cette conversion, l'index pointe APRÈS la fin du film. Mesuré : il annonçait « fin à
# 39,8 s » sur un fichier de 31,2 s. C'est une page dont tout l'objet est de dire où regarder, et
# elle envoyait le lecteur dans le vide - en ayant l'air, comme toujours, parfaitement calculée.
position_dans_le_montage() { # <position dans la coupe> <plages> <facteur>
    LC_NUMERIC=C awk -v t="$1" -v k="$3" '
        { a[NR] = $1; b[NR] = $2 }
        END {
            v = t
            for (i = 1; i <= NR; i++) {
                if (t >= b[i]) { v -= (b[i] - a[i]) * (1 - 1 / k) }
                else if (t > a[i]) { v -= (t - a[i]) * (1 - 1 / k); break }
                else break
            }
            if (v < 0) v = 0
            printf "%.1f", v
        }' <<< "$2"
}

# Le titre et la page de documentation qu'un parcours illustre.
#
# ⚠️ Cette table existe parce que l'index a menti. Écrit pour un seul parcours, il titrait
# « Déclarer un carré » au-dessus du film d'IMPORTATION, et le renvoyait vers `sites.md`. Une page
# dérivée qui se trompe de sujet est pire qu'une page absente : elle a l'air d'avoir été vérifiée.
fiche_du_parcours() { # <nom>
    case "$1" in
        declarer-un-carre) printf 'Déclarer un carré\tdocs/ecrans/sites.md\n' ;;
        importer-une-nuit) printf 'Importer une nuit\tdocs/ecrans/importation.md\n' ;;
        melange-de-capteurs) printf 'Deux enregistreurs dans le même dossier\tdocs/ecrans/importation.md\n' ;;
        sans-journal) printf 'Une carte sans journal du capteur\tdocs/ecrans/importation.md\n' ;;
        journal-illisible) printf 'Un journal illisible : l assistant refuse\tdocs/ecrans/importation.md\n' ;;
        multi-nuits) printf 'Trois nuits sur une carte : decouper en passages\tdocs/ecrans/importation.md\n' ;;
        grosse-carte) printf 'Soixante enregistrements, et c est instantane\tdocs/ecrans/importation.md\n' ;;
        *) return 1 ;;
    esac
}

# La section d'un parcours dans l'index. Rien si son film n'est pas là.
section_du_parcours() { # <dossier> <nom>
    local dossier="$1" nom="$2"
    local film="$dossier/$nom-monte.mp4" brut="$dossier/$nom.mkv" marques="$dossier/$nom.marques.tsv"
    [ -f "$film" ] && [ -f "$marques" ] || return 0

    local fiche titre page duree brut_duree t0 part
    fiche=$(fiche_du_parcours "$nom") || fiche="$nom	"
    titre=$(printf '%s' "$fiche" | cut -f1)
    page=$(printf '%s' "$fiche" | cut -f2)
    duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$film" 2>/dev/null)
    brut_duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$brut" 2>/dev/null)
    t0=$(origine_du_film "$marques" "$brut_duree")
    part=$(part_utile "$film")

    echo "## $titre"
    echo
    printf '| | |\n|---|---|\n'
    printf '| film | `%s` |\n' "$(basename "$film")"
    LC_NUMERIC=C printf '| durée | %.1f s (brut : %.1f s) |\n' "$duree" "$brut_duree"
    LC_NUMERIC=C printf '| images utiles | %.0f %% |\n' "$(LC_NUMERIC=C awk -v p="$part" 'BEGIN{print p * 100}')"
    [ -n "$page" ] && printf '| illustre | [%s](../../%s) |\n' "$page" "$page"
    echo
    echo "### Où en est le parcours"
    echo
    printf '| étape | dans le film |\n|---|---|\n'
    # ⚠️ Les positions se comptent dans le film MONTÉ, pas dans le brut. Le montage a coupé au
    # repère « debut » : il faut donc retrancher cette origine, sans quoi l'index annonce un
    # « debut » à -0,2 s dans un film qui commence à zéro - une petite fausseté, sur la page
    # dont tout l'intérêt est de dire où regarder.
    local coupe etape brute relative plages
    coupe=$(instant_du_repere "$marques" "$t0" debut)
    LC_NUMERIC=C awk -v c="$coupe" 'BEGIN{exit !(c < 0)}' && coupe=0
    plages=$(plages_a_accelerer "$marques" "$t0" "$coupe")
    while IFS=$'\t' read -r _ etape; do
        [ "$etape" = arret ] && continue
        brute=$(instant_du_repere "$marques" "$t0" "$etape")
        relative=$(LC_NUMERIC=C awk -v b="$brute" -v c="$coupe" 'BEGIN{v = b - c; if (v < 0) v = 0; printf "%.3f", v}')
        relative=$(position_dans_le_montage "$relative" "$plages" "$FACTEUR_ACCELERATION")
        printf '| %s | %s s |\n' "$etape" "$relative"
    done < "$marques"
    echo
}

# ⚠️ L'index décrit TOUS les films présents, pas seulement celui qu'on vient de tourner. Écrit pour
# le seul dernier tournage, il effaçait le parcours précédent à chaque passage : la page annonçait
# un dépôt qui n'aurait filmé qu'un parcours, alors que les deux films étaient là, côte à côte.
ecrire_index() { # <dossier> <index>
    local dossier="$1" index="$2" nom
    {
        echo "# Parcours filmés de la documentation"
        echo
        echo "Cette page est **dérivée** : elle se réécrit à chaque tournage, depuis les repères posés"
        echo "par le scénario et la mesure des fichiers. Rien n'y est saisi à la main."
        echo
        for nom in declarer-un-carre importer-une-nuit melange-de-capteurs sans-journal journal-illisible; do
            section_du_parcours "$dossier" "$nom"
        done
        echo "## ⚠️ Ce que ces films ne prouvent pas"
        echo
        echo "Qu'ils soient **clairs**. Le banc sait dire que l'enchaînement a eu lieu - chaque geste a"
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
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; VIGIECHIRO_JAR=/tmp/x.jar; [ "$(resoudre_le_jar)" = /tmp/x.jar ]' "${BASH_SOURCE[0]}"

    # --- le bac jetable ---
    essai "un bac hors de l'espace utilisateur est accepté" vert  verifier_bac_jetable "$bac"
    essai "un bac DANS l'espace utilisateur est refusé"     rouge \
        verifier_bac_jetable "$HOME/Documents/VigieChiro-Companion/essai"
    essai "un bac vide est refusé"                          rouge verifier_bac_jetable ""

    # --- l'environnement jetable du produit ---
    essai "l'environnement jetable pose HOME dans le bac"   vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; poser_environnement_jetable "$1"; [ "$HOME" = "$1/home" ]' "${BASH_SOURCE[0]}" "$bac"
    # ⚠️ LE cas qui porte ce dispositif : si l'export disparaît, HOME reste celui de l'utilisateur et
    # le sélecteur filme son arborescence. Le vert ne le dirait pas ; ce rouge-là, si.
    essai "le HOME réel ne survit pas à l'environnement jetable" rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; reel="$HOME"; source "$0"; poser_environnement_jetable "$1"; [ "$HOME" = "$reel" ]' "${BASH_SOURCE[0]}" "$bac"
    essai "les dossiers XDG que GTK lira sont créés"        vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; poser_environnement_jetable "$1"; [ -d "$XDG_CONFIG_HOME" ] && [ -d "$XDG_DATA_HOME" ]' "${BASH_SOURCE[0]}" "$bac"
    essai "sans bac, l'environnement jetable refuse"        rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; poser_environnement_jetable ""' "${BASH_SOURCE[0]}"

    # --- les parcours que le banc sait tourner ---
    essai "le parcours de déclaration est connu"           vert  parcours_connu declarer-un-carre
    essai "le parcours d'importation est connu"            vert  parcours_connu importer-une-nuit
    # ⚠️ Sans ce cas, une faute de frappe tournerait le parcours par défaut sous le nom demandé : un
    # film juste, portant le nom d'un autre.
    essai "un parcours inconnu est refusé"                 rouge parcours_connu importer-une-nuits
    essai "le parcours du mélange est connu"               vert  parcours_connu melange-de-capteurs
    essai "le parcours du journal illisible est connu"     vert  parcours_connu journal-illisible
    # ⚠️ Ce parcours ne se termine PAS par un import : son exigence de fin est le motif du refus.
    # L'y attendre « Import terminé » le ferait échouer à tous les coups ; ne rien exiger publierait
    # un film montrant un écran quelconque.
    essai "le refus a sa propre exigence de fin"           vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1
            [ "$(grep -c "^    exiger_a_l_ecran .* \"inexploitable\"" "$0")" = 1 ]' "${BASH_SOURCE[0]}"
    essai "le parcours sans journal est connu"             vert  parcours_connu sans-journal
    # ⚠️ Le montage doit sortir un MP4 qui commence à jouer AVANT d'être entièrement chargé. Sans
    # `+faststart`, l'atome `moov` reste en fin de fichier et le navigateur doit aller le chercher
    # là-bas : la page rend un lecteur qui ne démarre pas tout de suite, sans une erreur.
    essai "le montage pose l index en TÊTE du fichier"     vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            printf "1000.0\tdebut\n1004.0\tfin\n1010.0\tarret\n" > "$1/fs.tsv"
            ffmpeg -v error -y -f lavfi -i "testsrc=s=160x120:d=10:r=10" "$1/fs.mkv" </dev/null 2>/dev/null
            monter "$1/fs.mkv" "$1/fs.tsv" "$1/fs.mp4" >/dev/null 2>&1 || exit 1
            python3 -c "
import sys
d = open(sys.argv[1], \"rb\").read(8192)
i, j = d.find(b\"moov\"), d.find(b\"mdat\")
sys.exit(0 if i != -1 and (j == -1 or i < j) else 1)
" "$1/fs.mp4"' "${BASH_SOURCE[0]}" "$bac"
    essai "le parcours d'importation nomme SA carte"       vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(parcours_connu importer-une-nuit | cut -f2)" = "recette/fixtures/spec/sd-nominale.yaml" ]' "${BASH_SOURCE[0]}"
    essai "celui de déclaration n'en réclame pas"          vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(parcours_connu declarer-un-carre | cut -f2)" = non ]' "${BASH_SOURCE[0]}"
    # ⚠️ LE cas qui porte le passage d'un global à une propriété : deux parcours qui monteraient la
    # MÊME carte donneraient un film juste sous un nom faux, et rien ne le dirait.
    essai "deux parcours ne partagent pas leur carte"      vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            [ "$(parcours_connu importer-une-nuit | cut -f2)" != "$(parcours_connu melange-de-capteurs | cut -f2)" ]' "${BASH_SOURCE[0]}"
    essai "chaque spec citée existe vraiment"              vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            for nom in importer-une-nuit melange-de-capteurs; do
                [ -f "$RACINE/$(parcours_connu "$nom" | cut -f2)" ] || exit 1
            done' "${BASH_SOURCE[0]}"
    essai "sans spec, la carte ne se prépare pas"          rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; preparer_la_carte "$1" ""' "${BASH_SOURCE[0]}" "$bac"

    # --- la relecture du dossier retenu par le sélecteur ---
    # ⚠️ La fixture rend le chemin tel que l'écran le porte, et les cas éprouvent le DÉFAUT CONNU :
    # un cran trop bas doit être refusé, sans quoi le film montrerait l'import du mauvais dossier.
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=420x40" \
        -vf "drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='/run/media/x/VIGIECHIRO':fontcolor=black:fontsize=18:x=10:y=10" \
        -frames:v 1 "$bac/chemin.png" </dev/null 2>/dev/null
    essai "le dossier retenu se relit"                     vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "$(lire_zone "$1" 0 0 420 40)" "VIGIECHIRO"' "${BASH_SOURCE[0]}" "$bac/chemin.png"
    essai "un cran TROP BAS ne passe pas pour le bon"      rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "$(lire_zone "$1" 0 0 420 40)" "bruts"' "${BASH_SOURCE[0]}" "$bac/chemin.png"

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
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(lire_zone "$1" 0 0 400 60)" = "Mes sites" ]' "${BASH_SOURCE[0]}" "$bac/libelle.png"
    essai "une zone vide ne rend aucun libellé"         vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ -z "$(lire_zone "$1" 300 0 90 60)" ]' "${BASH_SOURCE[0]}" "$bac/libelle.png"
    # ⚠️ Le cas qui porte le contrôle : lire le MAUVAIS libellé doit échouer, sinon `viser` laisserait
    # partir n'importe quel clic.
    essai "un libellé ABSENT ne se lit pas quand même"  rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(lire_zone "$1" 0 0 400 60)" = "Importer une nuit" ]' "${BASH_SOURCE[0]}" "$bac/libelle.png"

    # --- l'appariement des libellés ---
    essai "un libellé identique correspond"              vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "Mes sites" "Mes sites"' "${BASH_SOURCE[0]}"
    # ⚠️ Le cas mesuré sur le produit : l'OCR rend « Messites » sans son espace.
    essai "un espace perdu par l'OCR correspond quand même" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "Accueil » Messites" "Mes sites"' "${BASH_SOURCE[0]}"
    essai "un libellé DIFFÉRENT ne correspond pas"        rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "Mes sites" "Importer une nuit"' "${BASH_SOURCE[0]}"
    # ⚠️ Sans ce cas, un attendu vide correspondrait à tout, et « viser » laisserait partir n'importe
    # quel clic sur un scénario mal écrit.
    essai "un attendu VIDE ne correspond à rien"          rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "Mes sites" ""' "${BASH_SOURCE[0]}"

    # ⚠️ Le cas qui a coûté un aller-retour : un libellé plus large que la zone est tronqué, et le
    # refus doit le DIRE au lieu d'accuser le scénario.
    # --- le balayage vertical, et ce qu'il ne doit PAS absorber ---
    # ⚠️ La fixture rend le libellé DÉCALÉ de 8 px, comme la feuille de style du socle l'a fait
    # (#4023). Le geste doit le retrouver ; sans balayage, le tournage s'arrêtait sur un scénario
    # pourtant juste.
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=400x80" \
        -vf "drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='Mes sites':fontcolor=black:fontsize=22:x=20:y=46" \
        -frames:v 1 "$bac/decale.png" </dev/null 2>/dev/null
    # ⚠️ Ce que ce cas garde : le banc ne doit pas rendre ✅ sur un film où rien n'arrive. Le premier
    # tournage « sans journal » l'a fait - tous les `viser` passaient, le seul clic aveugle tombait à
    # côté, et le film montrait trente-quatre secondes d'un formulaire jamais rempli.
    essai "une exigence non satisfaite refuse"           rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; exiger_a_l_ecran ":91" 100 100 "Import termin"' "${BASH_SOURCE[0]}"
    # --- le mode de calage (#4055) ---
    # ⚠️ Le cas qui compte n'est pas « il sait saisir un écran » - il n'y a pas d'écran ici - mais
    # « il ne fait RIEN sans sa variable ». Un outil de mise au point qui saisirait l'écran à chaque
    # repère d'un tournage ordinaire coûterait douze saisies par film, pour personne.
    essai "sans CALAGE_BANC, aucune saisie n est TENTÉE"  vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            saisir_ecran() { printf appel >> "$d/temoin"; }
            unset CALAGE_BANC
            calage un_repere
            [ ! -s "$d/temoin" ]' "${BASH_SOURCE[0]}"
    # ⚠️ Et son jumeau positif : AVEC la variable, la saisie doit être tentée. Sans ce cas-ci, un
    # calage qui ne ferait jamais rien passerait le cas précédent sans faillir.
    essai "avec CALAGE_BANC, la saisie est tentée"        vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            saisir_ecran() { printf appel >> "$d/temoin"; }
            CALAGE_BANC="$d/images" calage un_repere
            [ -s "$d/temoin" ] && [ -d "$d/images" ]' "${BASH_SOURCE[0]}"
    # ⚠️ Et le repère lui-même s écrit dans les deux cas : le calage est un supplément, pas un
    # détour. S il remplaçait la ligne du repère, le montage perdrait ses plages.
    essai "le repère s écrit, calage ou non"              vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            marque "$d/m.tsv" premier
            CALAGE_BANC="$d/images" marque "$d/m.tsv" second
            [ "$(cut -f2 "$d/m.tsv" | tr "\n" " ")" = "premier second " ]' "${BASH_SOURCE[0]}"

    essai "tout parcours d import exige son résultat"     vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            [ -z "$(parcours_sans_exigence "$0")" ]' "${BASH_SOURCE[0]}"
    # ⚠️ Et le garde du garde, sur une fixture FAUTIVE : un parcours qui désigne une carte sans rien
    # exiger doit être NOMMÉ. Sans ce cas, une fonction d'inventaire qui ne rend jamais rien passerait
    # pour un banc sain - c'est exactement le vert que le compteur précédent rendait.
    printf '%s\n' 'parcours_fautif() {' '    verifier_dossier_retenu "$e" 1 1 "$c"' '}' > "$bac/fautif.sh"
    essai "un parcours d import sans exigence est nommé"  vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            [ "$(parcours_sans_exigence "$1")" = parcours_fautif ]' "${BASH_SOURCE[0]}" "$bac/fautif.sh"

    essai "un libellé décalé de 8 px se retrouve"        vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            for ecart in $DECALAGES_ESSAYES; do
                libelle_correspond "$(lire_zone "$1" 0 $((40 + ecart - ZONE_H / 2)) 400)" "Mes sites" && exit 0
            done
            exit 1' "${BASH_SOURCE[0]}" "$bac/decale.png"
    # ⚠️ Et le cas qui empêche le balayage de tout accepter : un libellé ABSENT reste absent, si
    # loin qu'on le cherche. Sans lui, `viser` finirait par trouver n'importe quoi ailleurs.
    essai "un libellé ABSENT ne se trouve à aucun écart"  rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            for ecart in $DECALAGES_ESSAYES; do
                libelle_correspond "$(lire_zone "$1" 0 $((40 + ecart - ZONE_H / 2)) 400)" "Importer une nuit" && exit 0
            done
            exit 1' "${BASH_SOURCE[0]}" "$bac/decale.png"

    essai "un libellé tronqué par la zone ne correspond pas" rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; libelle_correspond "uter mon premier site de:" "Ajouter mon premier site de suivi"' "${BASH_SOURCE[0]}"

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
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(origine_du_film "$1" 10)" = "1000.000" ]' "${BASH_SOURCE[0]}" "$bac/m.tsv"
    essai "un repere se convertit en position de film"   vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(instant_du_repere "$1" 1000 fin)" = "2.000" ]' "${BASH_SOURCE[0]}" "$bac/m.tsv"
    # ⚠️ Sans repere « arret », t0 est indéterminable : le montage doit refuser, pas deviner.
    printf '1000.0\tdebut\n1002.0\tfin\n' > "$bac/sans-arret.tsv"
    essai "sans repere « arret », t0 est refuse"         rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ -n "$(origine_du_film "$1" 10)" ]' "${BASH_SOURCE[0]}" "$bac/sans-arret.tsv"
    # Un film clair rend une part utile de 1 ; un film noir, de 0. Les deux bornes, pas un reglage.
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=160x120:d=2:r=10" "$bac/clair.mkv" </dev/null 2>/dev/null
    ffmpeg -v error -y -f lavfi -i "color=c=black:s=160x120:d=2:r=10" "$bac/noir.mkv" </dev/null 2>/dev/null
    essai "un film clair rend une part utile de 1"       vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(part_utile "$1")" = "1.00" ]' "${BASH_SOURCE[0]}" "$bac/clair.mkv"
    essai "un film noir rend une part utile de 0"        vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(part_utile "$1")" = "0.00" ]' "${BASH_SOURCE[0]}" "$bac/noir.mkv"

    # --- l'index ---
    # ⚠️ Le cas qui garde l'honnêteté de la page. « Ce que ce film ne prouve pas » est la première
    # section qu'on retire quand on veut faire propre, et c'est la seule qui empêche de lire l'index
    # comme un certificat. Un index sans elle annoncerait « 100 % d'images utiles » à un lecteur qui
    # comprendrait « ce film est bon ».

    # --- l'accélération des plages de machine ---
    printf '1000.0\tdebut\n1004.0\tinspection_debut\n1008.0\tinspection_fin\n1020.0\tfin\n1030.0\tarret\n' > "$bac/acc.tsv"
    essai "une plage jumelée est trouvée"                  vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(plages_a_accelerer "$1" 1000 0 | wc -l)" = 1 ]' "${BASH_SOURCE[0]}" "$bac/acc.tsv"
    # ⚠️ Un `_debut` orphelin doit être IGNORÉ, pas accéléré jusqu'à la fin : un film au rythme réel
    # vaut mieux qu'un montage qui emballe tout sur un repère manquant.
    printf '1000.0\tdebut\n1004.0\timport_debut\n1020.0\tfin\n1030.0\tarret\n' > "$bac/orphelin.tsv"
    essai "un repere de debut ORPHELIN est ignoré"         vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ -z "$(plages_a_accelerer "$1" 1000 0)" ]' "${BASH_SOURCE[0]}" "$bac/orphelin.tsv"
    essai "sans plage, le filtre reste vide"               vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ -z "$(filtre_de_montage 0 20 "")" ]' "${BASH_SOURCE[0]}"
    essai "le filtre alterne normal et accéléré"           vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(filtre_de_montage 0 20 "$(printf "4.000\t8.000\n")" | grep -o "concat=n=3")" = "concat=n=3" ]' "${BASH_SOURCE[0]}"

    # ⚠️ LE cas qui porte l'accélération, et il se mesure sur un VRAI fichier : le même parcours,
    # monté avec et sans sa plage de machine, doit rendre un film plus court. Un filtre qui ne
    # s'appliquerait pas laisserait les deux durées égales - et la ligne « montage » resterait
    # verte, puisqu'elle ne compare rien.
    ffmpeg -v error -y -f lavfi -i "testsrc=s=160x120:d=30:r=10" "$bac/long.mkv" </dev/null 2>/dev/null
    essai "une plage de machine RACCOURCIT le film"        vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0";
            monter "$1" "$2" "$3" >/dev/null 2>&1 || exit 1
            monter "$1" "$4" "$5" >/dev/null 2>&1 || exit 1
            avec=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$3")
            sans=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$5")
            LC_NUMERIC=C awk -v a="$avec" -v b="$sans" "BEGIN{exit !(a < b - 1)}"' \
        "${BASH_SOURCE[0]}" "$bac/long.mkv" "$bac/acc.tsv" "$bac/avec.mkv" "$bac/orphelin.tsv" "$bac/sans.mkv"

    # --- les positions de l'index, une fois les plages accélérées ---
    essai "une position AVANT toute plage ne bouge pas"    vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(position_dans_le_montage 3.0 "$(printf "10.0\t14.0\n")" 4)" = "3.0" ]' "${BASH_SOURCE[0]}"
    essai "une position APRÈS une plage recule"            vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(position_dans_le_montage 20.0 "$(printf "10.0\t14.0\n")" 4)" = "17.0" ]' "${BASH_SOURCE[0]}"
    # ⚠️ LE cas, et c'est celui que le dépôt a vécu : l'index annonçait « fin à 39,8 s » sur un
    # fichier de 31,2 s. Une page dont tout l'objet est de dire où regarder envoyait dans le vide,
    # en ayant l'air parfaitement calculée. Le garde compare la dernière étape à la durée MESURÉE.
    mkdir -p "$bac/verif"
    ffmpeg -v error -y -f lavfi -i "testsrc=s=160x120:d=40:r=10" "$bac/verif/importer-une-nuit.mkv" </dev/null 2>/dev/null
    printf '1000.0\tdebut\n1006.0\timport_debut\n1020.0\timport_fin\n1030.0\tfin\n1040.0\tarret\n' \
        > "$bac/verif/importer-une-nuit.marques.tsv"
    essai "l index ne pointe pas APRÈS la fin du film"     vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"
            monter "$1/importer-une-nuit.mkv" "$1/importer-une-nuit.marques.tsv" "$1/importer-une-nuit-monte.mp4" >/dev/null 2>&1 || exit 1
            ecrire_index "$1" "$1/i.md" >/dev/null || exit 1
            d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$1/importer-une-nuit-monte.mp4")
            max=$(grep -oE "\| [0-9]+\.[0-9]+ s \|" "$1/i.md" | grep -oE "[0-9]+\.[0-9]+" | sort -g | tail -1)
            [ -n "$max" ] && [ -n "$d" ] || exit 1
            LC_NUMERIC=C awk -v m="$max" -v d="$d" "BEGIN{exit !(m <= d + 0.5)}"' \
        "${BASH_SOURCE[0]}" "$bac/verif"
    mkdir -p "$bac/films"
    printf '1000.0\tdebut\n1002.0\tfin\n1010.0\tarret\n' > "$bac/films/importer-une-nuit.marques.tsv"
    ffmpeg -v error -y -f lavfi -i "color=c=white:s=160x120:d=2:r=10" "$bac/films/importer-une-nuit.mkv" </dev/null 2>/dev/null
    cp "$bac/films/importer-une-nuit.mkv" "$bac/films/importer-une-nuit-monte.mp4"
    essai "l index dit ce que le film ne prouve PAS"     vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; ecrire_index "$1" "$2" >/dev/null; grep -q "ADR 3764" "$2"' \
        "${BASH_SOURCE[0]}" "$bac/films" "$bac/i.md"
    essai "l index nomme la fiche d ecran illustree"     vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; grep -q "docs/ecrans/importation.md" "$2"' \
        "${BASH_SOURCE[0]}" "$bac/films" "$bac/i.md"
    # ⚠️ LE cas qui a manqué : l'index titrait « Déclarer un carré » au-dessus du film
    # d'importation. Une page dérivée qui se trompe de sujet a l'air d'avoir été vérifiée.
    essai "l index titre le parcours qu il décrit"       vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; grep -q "^## Importer une nuit" "$2"' \
        "${BASH_SOURCE[0]}" "$bac/films" "$bac/i.md"
    essai "il ne titre PAS un parcours absent"           rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; grep -q "^## Déclarer un carré" "$2"' \
        "${BASH_SOURCE[0]}" "$bac/films" "$bac/i.md"
    # ⚠️ Et le cas inverse : les DEUX films présents doivent tenir sur la même page. Écrit pour le
    # seul dernier tournage, l'index effaçait le parcours précédent à chaque passage.
    cp "$bac/films/importer-une-nuit.mkv" "$bac/films/declarer-un-carre.mkv"
    cp "$bac/films/importer-une-nuit.mkv" "$bac/films/declarer-un-carre-monte.mp4"
    cp "$bac/films/importer-une-nuit.marques.tsv" "$bac/films/declarer-un-carre.marques.tsv"
    essai "deux films tiennent sur la meme page"         vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; ecrire_index "$1" "$2" >/dev/null; grep -q "^## Déclarer un carré" "$2" && grep -q "^## Importer une nuit" "$2"' \
        "${BASH_SOURCE[0]}" "$bac/films" "$bac/i.md"

    # --- la carte SD ---
    # ⚠️ On n'appelle pas Maven ici : l'auto-test doit rester en secondes. Ce qu'on éprouve, c'est le
    # CONTRÔLE - qu'une carte absente, vide ou sans bruts soit refusée, puisque c'est le seul rempart
    # entre un tournage et un film où l'importation ne trouve rien.
    mkdir -p "$bac/carte-vide"
    essai "un dossier sans carte est refusé"             rouge carte_utilisable "$bac/carte-vide"
    mkdir -p "$bac/carte-sans-bruts/sd-nominale"
    essai "une carte sans dossier bruts est refusée"     rouge carte_utilisable "$bac/carte-sans-bruts"
    mkdir -p "$bac/carte-bruts-vides/sd-nominale/bruts"
    essai "une carte sans aucun brut est refusée"        rouge carte_utilisable "$bac/carte-bruts-vides"
    # Et le cas VERT, sans quoi les trois refus ci-dessus passeraient sur une fonction qui refuse tout.
    mkdir -p "$bac/carte-bonne/sd-nominale/bruts" && : > "$bac/carte-bonne/sd-nominale/bruts/a.wav"
    essai "une carte avec ses bruts est acceptée"        vert  carte_utilisable "$bac/carte-bonne"

    # --- la carte montée ---
    # ⚠️ Le garde qui compte : cette machine porte de vraies cartes montées. Un banc qui démonterait
    # au jugé pourrait s'en prendre à elles.
    essai "notre étiquette est reconnue"                 vert  notre_montage "/run/media/moi/VIGIECHIRO"
    essai "la carte d un tiers est refusée"              rouge notre_montage "/run/media/moi/72CA-9E54"
    essai "un point de montage vide est refusé"          rouge notre_montage ""
    essai "on refuse de démonter ce qui n est pas à nous" rouge demonter_la_carte "/run/media/moi/72CA-9E54" /dev/loop9
    essai "une carte source absente est refusée"         rouge image_de_la_carte "$bac/nexiste-pas" "$bac/x.img"
    essai "une carte source sans brut est refusée"       rouge image_de_la_carte "$bac/carte-bruts-vides/sd-nominale" "$bac/x.img"

    # --- les outils ---
    essai "les outils du banc sont là"                   vert  verifier_outils
    # ⚠️ Le cas qui porte la séparation des deux listes : un outil de la CARTE manquant ne doit pas
    # refuser un parcours qui n'en monte pas. C'est ce qui permet à la CI de lancer ces 60 cas sans
    # installer un service de disques.
    essai "un outil de carte absent ne bloque pas le banc"  vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; OUTILS_DE_LA_CARTE="outil-qui-nexiste-pas"; verifier_outils' "${BASH_SOURCE[0]}"
    essai "mais il bloque un tournage AVEC carte"        rouge \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; OUTILS_DE_LA_CARTE="outil-qui-nexiste-pas"; verifier_outils --avec-carte' "${BASH_SOURCE[0]}"
    # ⚠️ Et le remède doit nommer ce qui manque : le message ne citait que tesseract quand `mcopy`
    # manquait.
    #
    # ⚠️ La sortie se CAPTURE, elle ne se traverse pas par un tube. Ce banc pose `pipefail` : dans
    # `verifier_outils … | grep -q`, le code du pipeline est celui de `verifier_outils` - un échec,
    # puisqu'un outil manque - et non celui du `grep`. Le cas rougissait pour une raison étrangère à
    # ce qu'il éprouve, et il aurait aussi bien pu verdir pour une autre.
    essai "le remède nomme les paquets de la carte"      vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; OUTILS_DE_LA_CARTE="outil-qui-nexiste-pas"
            sortie=$(verifier_outils --avec-carte 2>&1)
            case "$sortie" in *dosfstools*) exit 0 ;; *) exit 1 ;; esac' "${BASH_SOURCE[0]}"

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
# ⚠️ Le drapeau, et non « $0 vaut BASH_SOURCE ». Cette comparaison ne dit PAS ce qu'on croit :
# sous `bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"' /chemin/du/banc`, `$0` EST le chemin du banc, donc les deux sont
# égaux et le garde conclut « lancé directement ». L'auto-test déclenchait alors un tournage à
# chaque cas qui se source - il s'est mis à figer dès que les prérequis ont grandi.
#
# Un drapeau explicite ne peut pas se tromper : qui source le dit.
if [ -z "${BANC_SOURCE_SEULEMENT:-}" ] && [ "${BASH_SOURCE[0]}" = "$0" ]; then
    if [ "${1:-}" = "--auto-test" ]; then
        auto_test
        exit $?
    fi
    tourner "${1:-}" "${2:-}"
fi
