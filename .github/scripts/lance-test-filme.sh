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
#   lance-test-filme.sh --planche             # TOUTES les classes qui citent un cas (#3835)
#   lance-test-filme.sh --verifier            # les préconditions, sans rien lancer
#   lance-test-filme.sh --auto-test           # éprouve les vérifications elles-mêmes

set -uo pipefail

# Se relancer SANS WAYLAND_DISPLAY plutôt que d'exiger que l'appelant y pense : l'objet de ce
# script est de tenir en une commande. La vérification qui suit garde tout son sens, puisqu'elle
# porte sur l'environnement réellement remis à Maven, et non sur une intention.
# Le drapeau évite une boucle si l'environnement le repose (ce qu'aucun cas connu ne fait, mais
# une relance infinie serait un défaut bien plus coûteux que cette ligne).
# ⚠️ `BANC_SOURCE_SEULEMENT` court-circuite aussi CE point : qui source ne veut rien lancer, et la
# relance est un lancement. Sans cela, `source ce-script` sur une session Wayland `exec`ute une
# copie qui repart au début - le garde du bas n'est alors jamais atteint.
if [ -z "${BANC_SOURCE_SEULEMENT:-}" ] && [ -n "${WAYLAND_DISPLAY:-}" ] && [ -z "${RECETTE_RELANCE:-}" ]; then
    exec env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE RECETTE_RELANCE=1 \
        bash "${BASH_SOURCE[0]}" "$@"
fi

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POM="${POM_A_VERIFIER:-$RACINE/pom.xml}"
ECRAN="${ECRAN_RECETTE:-:97}"
TAILLE="1280x900x24"
# La police du carton. DejaVu est présente sur les runners GitHub comme sur les postes de
# développement ; `carton_de_titre` échoue proprement si elle manque, et le clip se produit sans
# carton plutôt que pas du tout.
# ⚠️ La police du carton se RÉSOUT. Le chemin DejaVu ci-dessous est celui des postes de dev et des
# images GitHub, mais aucun paquet du banc ne le garantit : `fc-match` sert de recours plutôt que de
# rendre un carton vide sur une machine autrement équipée.
POLICE_CARTON="${POLICE_CARTON:-/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf}"
if [ ! -r "$POLICE_CARTON" ] && command -v fc-match >/dev/null 2>&1; then
    POLICE_CARTON=$(fc-match -f '%{file}' sans 2>/dev/null)
fi

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
    # ⚠️ UN SEUL fork, et c'est la condition même du filmage. Le réglage ordinaire du dépôt est `1C`,
    # un fork par cœur : plusieurs classes tournent alors en parallèle, ce qui est bon pour la durée
    # du build et fatal ici. Un tournage n'a qu'UN écran et qu'UN pointeur ; deux forks les pilotent
    # en même temps, et les clics d'une classe tombent dans les fenêtres d'une autre.
    #
    # Mesuré : `ConnexionModaleViewTest` passe seul et rougit dès qu'une seconde classe est filmée
    # avec lui. Le premier tournage complet en CI a donné 14 rouges sur 128 pour cette seule raison.
    printf '%s' "$bloc" | grep -q '<surefire.forkCount>1<' \
        || manques+=("surefire.forkCount devrait valoir 1 : un écran, un pointeur, un fork")

    [ ${#manques[@]} -eq 0 ] && return 0
    printf '   - %s\n' "${manques[@]}"
    return 1
}

# La configuration du gestionnaire de fenêtres du banc est-elle là ?
#
# ⚠️ Sans elle, openbox lit `/etc/xdg/openbox/rc.xml` et ne dit rien : il se rabat sur les défauts de
# la distribution, qui ne sont pas les mêmes partout. Le poste de développement centre les fenêtres,
# l'image des runners ne les centrait pas - et la modale corrigée par #4074 paraissait centrée en
# local et collée en haut à gauche sur le clip PUBLIÉ. Même code, même commit, deux placements.
#
# Un fichier manquant ne doit donc pas se rattraper en silence : un film qui dépend des défauts de la
# machine qui l'enregistre ne montre pas le produit, il montre la machine.
verifier_config_openbox() {
    if [ ! -r "$CONFIG_OPENBOX" ]; then
        echo "   - configuration du gestionnaire de fenêtres absente : $CONFIG_OPENBOX"
        echo "     Sans elle, openbox reprend les défauts de la distribution, qui placent les"
        echo "     fenêtres autrement d'une machine à l'autre. Les clips ne seraient plus"
        echo "     comparables entre un poste et la CI."
        return 1
    fi
    return 0
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
    for o in Xvfb xdotool ffmpeg openbox; do
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
    verifier_config_openbox || defauts=$((defauts + 1))
    verifier_pointeur "$ecran" || defauts=$((defauts + 1))
    if [ "$defauts" -eq 0 ]; then
        echo "✅ Les six conditions sont réunies."
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
# Aucune valeur entre les deux. ⚠️ Une image noire vaut 16 et NON zéro : « différent de zéro »
# ne marcherait pas.
#
# ## ⚠️ Le seuil a dû BAISSER quand le banc a cessé de maximiser (#3788)
#
# Les 216-226 ci-dessus sont ceux d'une fenêtre PLEIN ÉCRAN : c'est ce que faisait matchbox de
# tout ce qu'il affichait. Avec openbox, une fenêtre occupe sa taille réelle, et la moyenne de
# l'image chute d'autant. Mesuré sur la même modale :
#
#     16,01  le fond seul
#     27,7   la fenêtre est là, encore vide
#     69,1   la fenêtre avec son contenu
#
# 69 passait encore un seuil à 50, mais de justesse, et le calcul dit que ça ne tient pas : une
# boîte de dialogue de 400 × 250 sur un écran de 1280 × 900 couvre 8,7 % de la surface, donc
# rend 0,087 × 243 + 0,913 × 16 ≈ 36. Elle aurait été déclarée « rien à l'écran », la coupe
# aurait refusé, et la couverture se serait effondrée - sur un banc pourtant juste.
#
# Le seuil se pose donc juste au-dessus du fond, à 20 : il sépare « aucune fenêtre » de
# « une fenêtre, si petite soit-elle », qui est la question réellement posée. Il reste valable
# pour l'ancien banc, où l'application donnait 223.
#
# ## Et le contrôle mesure CE QUE LA COUPE A DÉCIDÉ
#
# C'est la leçon de #3696 : un contrôle qui mesure autre chose que la coupe reproduit son
# angle mort. Ici la coupe retient les images au-dessus du seuil, et le contrôle recompte,
# sur le MONTAGE, la proportion d'images au-dessus du même seuil.
# La configuration du gestionnaire de fenêtres, surchargeable pour que le cas d'auto-test puisse
# éprouver son absence.
CONFIG_OPENBOX="${CONFIG_OPENBOX:-$RACINE/.github/scripts/openbox-banc.xml}"

LUMINANCE_SEUIL=20
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

# --------------------------------------------------------------------------------------------
# Le montage par cas : un clip par test cité, et un index qui se lit par cas (#3774).
#
# ## Le problème dur, et pourquoi `t0` se MESURE
#
# Le journal consigne des instants d'horloge ; la vidéo se compte depuis son début. Passer de
# l'un à l'autre demande de connaître l'instant où l'image 0 a été capturée, et cet instant
# n'est PAS celui où l'on a lancé ffmpeg : il s'initialise, et cette latence varie.
#
# On la contourne en la rendant sans objet. On connaît l'instant où l'on a demandé l'arrêt, et
# la durée du fichier obtenu : l'image 0 est donc à `arrêt - durée`, quelle qu'ait été la
# latence de démarrage. Rien n'est supposé.
#
# ⚠️ Se tromper ici ne produit pas de panne. Les clips s'ouvrent, montrent une interface, et ne
# montrent pas le bon geste. C'est le motif exact que cet EPIC combat, d'où le contrôle qui suit.
#
# ## Le contrôle : la COUVERTURE, et non la clarté de chaque clip
#
# Le réflexe serait d'exiger que chaque clip soit clair. Ce serait un garde qui crie sur du bon
# travail : un test de ViewModel cite des cas et n'ouvre **aucune** fenêtre, son clip est noir à
# juste titre.
#
# La grandeur qui sépare vraiment est ailleurs : les images où quelque chose est à l'écran
# doivent tomber DANS les plages calculées. Si `t0` est faux, elles tombent toutes à côté, et la
# couverture s'effondre - alors qu'une séance sans aucune fenêtre n'a rien à couvrir et ne
# déclenche rien.
#
# ⚠️ LES PLAGES SONT CELLES DE TOUS LES TESTS, pas seulement des tests cités. La première séance
# filmée réelle a refusé un alignement correct pour cette raison : `ConnexionModaleViewTest`
# compte dix tests dont trois annotés, et les sept autres ouvrent aussi des fenêtres. Le contrôle
# jugeait donc hors sujet les cinq sixièmes de ce qu'il voyait, et annonçait 16 %.
#
# C'était le même travers que celui qu'il évitait par ailleurs : un garde qui crie sur du bon
# travail. Le journal décrit désormais la séance entière ; seul l'index ne retient que les cas.
#
# Mesuré sur le film fabriqué de l'auto-test (6 s, geste visible de 2 s à 4 s, 21 images utiles) :
#
#     repères justes          -> couverture 1,00
#     repères décalés de 3 s  -> couverture 0,00
#
# Les deux modes sont aux extrémités, et le seuil est loin des deux : il ne se règle pas au
# millimètre. ⚠️ Le nombre d'images utiles est rendu avec la couverture, et non déduit : une
# couverture parfaite sur ZÉRO image utile serait un vert creux, indiscernable d'un contrôle qui
# ne s'est pas exécuté.
CLIP_MARGE=0.5           # un peu avant et après : ne pas couper au ras du geste
ONGLET=$'\t'             # la tabulation du journal, nommée : illisible en littéral dans un printf
CLIP_DUREE_MIN=0.20      # sous cette durée, un extrait ne montre rien et ffmpeg n'écrit rien de lisible
COUVERTURE_MIN=0.6       # sous ce seuil, les plages ne désignent pas ce que le film montre

# Rend « couverture utiles » : la part des images utiles du brut qui tombent dans une plage, et
# leur nombre. Une couverture de -1 signifie qu'il n'y avait aucune image utile à couvrir.
couverture_des_plages() {
    profil_luminance "$1" | awk -v s="$LUMINANCE_SEUIL" -v p="$2" '
        BEGIN {
            n = split(p, lignes, "\n")
            for (i = 1; i <= n; i++) {
                if (lignes[i] == "") continue
                split(lignes[i], ch, "\t"); deb[i] = ch[2]; fin[i] = ch[3]
            }
        }
        $2 > s {
            utiles++
            for (i = 1; i <= n; i++) {
                if (deb[i] != "" && $1 >= deb[i] && $1 <= fin[i]) { couvertes++; break }
            }
        }
        END { printf "%s %d\n", (utiles ? couvertes / utiles : -1), utiles }'
}

# L'instant courant en millisecondes depuis l'époque, sur treize chiffres.
#
# ⚠️ `date +%s%3N` n'est PAS portable, et son échec ne se voit pas. Le modificateur de largeur `3`
# est ignoré par certaines versions de `date` : `%N` sort alors ses neuf chiffres entiers, et
# l'instant vaut un MILLION de fois trop. Mesuré sur un poste de développement, où `date +%s%3N`
# rend `1787247026506300185` au lieu de `1787247026506`.
#
# Ce que cela produisait : `t0` partait à 1,8e15, toutes les plages du journal devenaient massivement
# négatives, aucune image ne tombait dedans, et le banc annonçait « le montage vise à côté : 0 % » -
# un message qui accuse les repères alors que c'est l'horloge qui a menti. Les runners GitHub
# honorent `%3N`, si bien que le défaut ne paraissait qu'en local, et qu'il y bloquait tout.
#
# Une seule invocation de `date` : deux appels séparés pourraient tomber de part et d'autre d'une
# seconde et rendre un instant faux d'une seconde entière.
instant_en_millisecondes() {
    local secondes nanosecondes
    read -r secondes nanosecondes < <(date +'%s %N')
    printf '%s' "$(( secondes * 1000 + 10#$nanosecondes / 1000000 ))"
}

# Les plages « test début fin cas », en secondes depuis le début du brut.
plages_du_journal() {
    # ⚠️ La marge ne s'ajoute PAS à l'aveugle : elle est bornée par le cas voisin (#4113).
    #
    # Ajoutée sans regarder, elle fait finir un clip sur l'écran du cas SUIVANT. Constaté à l'image :
    # S6-28 se terminait sur la modale de connexion d'une autre classe, et un clip perceptif existe
    # justement pour qu'un humain juge ce qu'il voit. Sa dernière image est celle qui reste.
    #
    # Là où le voisin est loin, la marge reste entière : c'est elle qui empêche de couper au ras du
    # geste, et la raboter partout coûterait la respiration qu'elle donne.
    #
    # ⚠️ Et le repère ne suffit PAS. Borner sur le `debut` du cas suivant laisse encore passer sa
    # fenêtre : elle paraît pendant son montage (`@Start`), donc AVANT que son repère soit écrit. La
    # première version de ce bornage a raccourci le clip de S6-28 de 0,2 s et la modale de connexion y
    # est restée - vérifié à l'image sur le tournage qui a suivi.
    #
    # La borne juste n'est pas un repère mais une IMAGE : la queue s'arrête à la première image noire
    # qui suit la fin du cas, c'est-à-dire au moment où sa fenêtre disparaît. Le profil de luminance
    # est passé en troisième argument ; sans lui, on s'en tient aux repères.
    #
    # Deux passes, parce que la seconde a besoin de connaître le voisin, donc de les avoir tous. Le
    # tri intermédiaire ne suppose rien de l'ordre du journal.
    LC_NUMERIC=C awk -F'\t' -v t0="$1" '
        /^#/ { next }
        $2 == "debut" { d[$3] = $1; c[$3] = $4; next }
        $2 == "fin" && ($3 in d) {
            printf "%.3f\t%.3f\t%s\t%s\n", d[$3] / 1000 - t0, $1 / 1000 - t0, $3, c[$3]
            delete d[$3]
        }' "$2" \
        | LC_ALL=C sort -t"$ONGLET" -k1,1n \
        | LC_NUMERIC=C awk -F'\t' -v m="$CLIP_MARGE" -v duree_min="$CLIP_DUREE_MIN" \
            -v profil="${3:-}" -v seuil="$LUMINANCE_SEUIL" '
            BEGIN {
                # Le profil, une fois : « instant<TAB>luminance » par image, tel que rend
                # profil_luminance. Absent, le bornage reste celui des reperes.
                if (profil != "") {
                    while ((getline ligne < profil) > 0) {
                        split(ligne, champ, "\t")
                        n_prof++
                        t_prof[n_prof] = champ[1] + 0
                        y_prof[n_prof] = champ[2] + 0
                    }
                    close(profil)
                }
            }
            { deb[NR] = $1; fin[NR] = $2; nom[NR] = $3; cas[NR] = $4 }
            # La premiere image NOIRE au-dela de « depuis », ou -1 : cest la ou la fenetre du cas a
            # disparu, donc la derniere image qui lui appartienne encore.
            function premiere_image_noire(depuis,    k) {
                for (k = 1; k <= n_prof; k++) {
                    if (t_prof[k] >= depuis && y_prof[k] <= seuil) return t_prof[k]
                }
                return -1
            }
            END {
                for (i = 1; i <= NR; i++) {
                    a = deb[i] - m
                    if (i > 1 && a < fin[i - 1]) a = fin[i - 1]
                    if (a < 0) a = 0
                    b = fin[i] + m
                    if (i < NR && b > deb[i + 1]) b = deb[i + 1]
                    # ⚠️ Le bornage a l image AFFINE, il ne doit jamais APPAUVRIR. Un test tres rapide
                    # nouvre aucune fenetre : son ecran est noir des le depart, la premiere image noire
                    # tombe avant sa propre fin, et la plage seffondre. Onze clips ont ainsi disparu de
                    # la pre-version, laissant onze lecteurs vides sur les pages de la doc.
                    #
                    # La coupe a limage ne sapplique donc que si elle laisse un extrait regardable.
                    noir = premiere_image_noire(fin[i])
                    if (noir >= 0 && noir < b && noir - a >= duree_min) b = noir
                    # ATTENTION : une plage EFFONDREE ne se coupe pas. ffmpeg ecrit un fichier vide,
                    # et le remuxage suivant echoue dessus : « invalid as first byte of an EBML
                    # number ». Vecu : le premier tournage apres le bornage n a publie AUCUN clip.
                    # (Sans apostrophe : ce commentaire vit DANS un programme awk entre guillemets
                    # simples, ou une apostrophe termine la chaine - shellcheck l a vu.)
                    if (b - a < duree_min) {
                        printf "   index : %s ecarte, plage de %.2f s apres bornage (< %.2f)\n", \
                            nom[i], b - a, duree_min > "/dev/stderr"
                        continue
                    }
                    printf "%s\t%.2f\t%.2f\t%s\n", nom[i], a, b, cas[i]
                }
            }'
}

montage_par_cas() {
    local brut="$1" journal="$2" dossier="$3" arret_ms="$4"
    [ -s "$brut" ] || { echo "   index : rien n'a été filmé"; return 0; }
    [ -s "$journal" ] || { echo "   index : aucun repère, aucun test filmé ne cite de cas"; return 0; }

    local duree t0 plages mesure couverture utiles
    duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$brut" 2>/dev/null)
    [ -n "$duree" ] || { echo "⚠️ durée du brut illisible : pas de montage"; return 1; }
    # ⚠️ Un instant hors de portée se REFUSE ici, au lieu de traverser le calcul. Sans ce contrôle,
    # une horloge d'un million de fois trop grande rendait des plages négatives, et le banc concluait
    # « les repères ne décrivent pas CE film » : il accusait les repères d'un défaut de l'horloge.
    # Treize chiffres, c'est l'époque en millisecondes de 2001 à 2286.
    case "$arret_ms" in
        [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;;
        *)
            echo "⚠️ instant d'arrêt implausible : « $arret_ms » n'est pas un nombre de treize chiffres."
            echo "   L'horloge du banc ne rend pas des millisecondes. Sans elle, aucune plage n'est"
            echo "   calculable et les clips seraient taillés n'importe où : pas de montage."
            return 1
            ;;
    esac
    t0=$(LC_NUMERIC=C awk -v a="$arret_ms" -v d="$duree" 'BEGIN{printf "%.3f", a / 1000 - d}')

    # Le profil sert de borne d'image au montage : calcule une fois, il dit ou chaque fenetre
    # disparait. Sans lui, on retomberait sur les seuls reperes - et sur le defaut de S6-28.
    local profil
    profil=$(mktemp)
    profil_luminance "$brut" > "$profil"
    plages=$(plages_du_journal "$t0" "$journal" "$profil")
    rm -f "$profil"
    [ -n "$plages" ] || { echo "   index : le journal ne contient aucun passage complet"; return 0; }

    mesure=$(couverture_des_plages "$brut" "$plages")
    couverture=${mesure% *}; utiles=${mesure##* }
    if [ "$utiles" -eq 0 ]; then
        echo "   index : aucune image utile de toute la séance, il n'y a rien à couvrir"
    elif awk -v c="$couverture" -v m="$COUVERTURE_MIN" 'BEGIN{exit !(c < m)}'; then
        LC_NUMERIC=C printf '⚠️ le montage vise à côté : %.0f %% seulement des images utiles tombent\n' \
            "$(awk -v c="$couverture" 'BEGIN{print c * 100}')"
        echo "   dans une plage. Les repères ne décrivent pas CE film : aucun clip produit."
        return 1
    fi

    mkdir -p "$dossier"
    local index="$dossier/index.md" lignes="$dossier/.lignes"
    : > "$lignes"

    local test deb fin cas clip part clips=0
    while IFS=$'\t' read -r test deb fin cas; do
        # Le journal décrit la séance ENTIÈRE, parce que le contrôle de couverture a besoin de
        # toutes les fenêtres. L'index, lui, ne retient que les cas : un test qui n'en cite aucun
        # n'a pas d'extrait, personne n'irait le chercher.
        [ -n "$cas" ] || continue
        clips=$((clips + 1))
        clip="$dossier/$test.mkv"
        ffmpeg -nostdin -loglevel error -i "$brut" -ss "$deb" -to "$fin" -an \
            -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -y "$clip" >/dev/null 2>&1

        # ⚠️ Le carton d'ouverture (#4053). Sans lui, un clip démarre sur l'application en mouvement
        # et le lien avec le cas vit dans `index.md`, à côté. Retour de la première revue humaine.
        #
        # Si le collage échoue - police absente sur ce poste - le clip reste tel quel : un extrait
        # sans titre vaut mieux qu'un extrait manquant.
        #
        # ⚠️ La part d'images utiles se mesure AVANT le collage, et l'ordre n'est pas indifférent.
        # Le carton porte du texte clair sur fond sombre : ses images comptent pour utiles. Mesurée
        # après, un test qui ne montre RIEN - un ViewModel, par exemple - passerait de 0 % à un tiers,
        # et l'index le proposerait « en regardant » alors qu'il n'y a rien à regarder. La part décrit
        # le TEST, pas son titre.
        part=$(part_utile "$clip")
        coller_le_carton "$clip" "$cas" "$test" || true
        # La part d'images utiles est REPORTÉE, pas exigée : un clip noir est le résultat juste
        # pour un test qui n'ouvre pas de fenêtre. Ce qui est exigé vaut pour la séance entière,
        # et c'est le contrôle de couverture ci-dessus.
        #
        # ⚠️ Elle décide en revanche de la MANIÈRE d'auditer (#3835). La frontière est « aucune
        # image utile », et non un seuil réglé : soit quelque chose a paru à l'écran, soit rien.
        # Un nombre choisi à la main aurait rangé un cas du mauvais côté sans qu'on le sache.
        printf '%s\t%s\t%s\t%s\t%s\n' "$cas" "$test" "$(basename "$clip")" \
            "$(awk -v p="$part" 'BEGIN{printf "%.0f", p * 100}')" \
            "$(awk -v p="$part" 'BEGIN{print (p > 0 ? "en regardant" : "en lisant le test")}')" >> "$lignes"
    done <<< "$plages"

    {
        cat <<'ENTETE'
# Cas filmés

Un clip par **test**, parce que c'est ce que la JVM sait borner ; cet index se lit par **cas**,
parce que c'est ce qu'on cherche. Un cas couvert par plusieurs tests a donc plusieurs lignes.

⚠️ Aucune position dans le film livré n'est donnée, et c'est volontaire : ce film est écourté par
luminance, si bien qu'une position calculée sur le brut y serait fausse. Le clip est le point
d'entrée.

## ⚠️ Comment auditer : en regardant, ou en lisant

Cet index sert à relire ce que les annotations affirment - le garde vérifie qu'un identifiant cité
**existe**, jamais que le test **fait ce que le cas décrit**.

Mais tous les tests qui citent un cas n'ouvrent pas de fenêtre. Un ViewModel en cite et ne montre
rien : son clip est noir, et c'est le résultat **juste**. Cocher « vu » dessus serait un mensonge, et
un mensonge que cette page aurait encouragé si elle avait proposé la même case à tout le monde.

La dernière colonne dit donc, pour chaque ligne, **par quel moyen** ce cas s'audite :

- **en regardant** - quelque chose a paru à l'écran pendant ce test, le clip le montre ;
- **en lisant le test** - rien n'a paru, il n'y a rien à regarder. L'audit est une lecture de code,
  et le clip n'y ajoute rien.

« Images utiles » donne la part du clip où quelque chose est à l'écran, pour que la ligne se juge
plutôt que de se croire.

| Cas | Clip | Ce qu'il joue | Images utiles | Comment l'auditer |
|---|---|---|---|---|
ENTETE
        sort "$lignes" | awk -F'\t' '{
            n = split($1, cas, ",")
            for (i = 1; i <= n; i++) printf "| %s | `%s` | %s | %s %% | %s |\n", cas[i], $3, $2, $4, $5
        }' | sort
    } > "$index"

    # Compté AVANT de retirer le fichier de travail : `grep '^|'` sur l'index compterait aussi
    # l'en-tête et son trait de séparation, et annoncerait deux lignes de trop.
    local nb_cas
    nb_cas=$(awk -F'\t' '{n = split($1, c, ","); total += n} END {print total + 0}' "$lignes")

    local a_regarder
    a_regarder=$(awk -F'\t' '$5 == "en regardant" {n = split($1, c, ","); total += n} END {print total + 0}' "$lignes")
    printf '   index : %d clip(s) sur %d test(s), %d ligne(s) de cas dont %d à regarder -> %s\n' \
        "$clips" "$(printf '%s\n' "$plages" | wc -l)" "$nb_cas" "$a_regarder" "$index"
    rm -f "$lignes"
    return 0
}

# --------------------------------------------------------------------------------------------

lancer() {
    local classe="$1"
    # Deux `local` séparés, et non `local a=… b=…$a…` : bash développe TOUS les mots avant
    # d'affecter, si bien que la seconde référence lirait une variable encore vide.
    local sortie="${2:-$RACINE/target/recette/${classe}.mkv}"
    mkdir -p "$(dirname "$sortie")"

    Xvfb "$ECRAN" -screen 0 "$TAILLE" -nolisten tcp >/dev/null 2>&1 &
    local xvfb=$!
    sleep 2
    DISPLAY="$ECRAN" openbox --sm-disable --config-file "$CONFIG_OPENBOX" >/dev/null 2>&1 &
    local wm=$!
    sleep 2
    # Guillemets DOUBLES : les numéros de processus sont gravés dans le trap à sa pose. En
    # simples, il lirait `$wm` et `$xvfb` au déclenchement, or ce sont des `local` : à la sortie
    # du script ils n'existent plus, `set -u` avorte le trap, et Xvfb comme le gestionnaire de
    # fenêtres survivent en orphelins. Constaté.
    # shellcheck disable=SC2064  # L'expansion IMMÉDIATE est le remède, pas le défaut : cf. ci-dessus.
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
        -i "$ECRAN" -t 1800 -c:v libx264 -preset ultrafast -crf 26 -g 20 -flush_packets 1 \
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
    # Relevé ICI, et non après `wait` : ffmpeg cesse de capturer quand il lit `q`, puis prend une
    # seconde à finaliser. Prendre l'heure après l'attente placerait l'image 0 une seconde trop
    # tôt, et décalerait TOUS les clips d'autant - sans rien casser d'apparent.
    local arret_ms
    arret_ms=$(instant_en_millisecondes)
    exec 3>&-
    wait "$film" 2>/dev/null
    rm -f "$tube"

    # AVANT la coupe : elle supprime le brut, dans lequel les clips se taillent.
    local montage=0
    montage_par_cas "$brut" "$RACINE/target/recette-filmee/reperes.tsv" \
        "$(dirname "$sortie")/clips" "$arret_ms" || montage=1

    couper_par_luminance "$brut" "$sortie"

    local duree=0
    [ -s "$sortie" ] && duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$sortie" 2>/dev/null)
    # LC_NUMERIC=C : ffprobe rend « 7.900000 », que la locale française refuse (elle attend
    # une virgule). Sans cela, printf échoue et affiche un nombre FAUX, ici 7,0 pour 7,9.
    LC_NUMERIC=C printf 'Verdict du test : %s · vidéo : %s (%.1f s)\n' "$code" "$sortie" "${duree:-0}"

    # Un montage qui vise à côté est un DÉFAUT, pas une remarque : il livrerait des extraits
    # plausibles pris au mauvais endroit. Il fait donc échouer le lancement même quand les tests
    # sont verts - c'est justement le cas où personne ne regarderait.
    if [ "$montage" -ne 0 ] && [ "$code" -eq 0 ]; then
        return 1
    fi
    return "$code"
}

# --------------------------------------------------------------------------------------------
# La planche de contact : filmer TOUT ce qui cite un cas, en une séance (#3835).
#
# ## Pourquoi une seule séance, et non seize
#
# Le montage taille déjà un clip par TEST et indexe par CAS. Passer les seize classes à un même
# `-Dtest=A,B,C` rend donc, d'un coup, un index qui les couvre toutes : il n'y a rien à fusionner.
# Seize séances auraient donné seize artefacts et une comptabilité à tenir de tête.
#
# ## ⚠️ La liste se DÉRIVE, elle ne se tient pas à la main
#
# Un `grep` sur `@CasDeRecette` ramène deux faux positifs sur dix-huit : l'annotation elle-même,
# dont la documentation contient un exemple, et les fixtures qui imitent un test sans rien couvrir.
# C'est `CorrespondanceRecetteTest` qui dépose la liste, parce qu'il balaie les annotations
# COMPILÉES et honore `@FixtureDeRecette`.
#
# Une liste tenue à la main dériverait exactement comme la prose dérivait avant #3728.
LISTE_CLASSES_DEFAUT="target/recette/classes-citantes.txt"

# Rend les classes à filmer, séparées par des virgules. REFUSE plutôt que de filmer le vide : une
# séance sans classe produirait un film noir et un index sans ligne, ce qui ressemble trait pour
# trait à une recette qui ne couvre rien.
classes_de_la_planche() {
    local fichier="$1"
    if [ ! -s "$fichier" ]; then
        echo "⚠️ liste des classes à filmer absente ou vide : $fichier" >&2
        echo "   Elle est DÉRIVÉE par CorrespondanceRecetteTest, qui doit tourner d'abord." >&2
        return 1
    fi
    paste -sd, "$fichier"
}

planche() {
    local liste="$RACINE/$LISTE_CLASSES_DEFAUT"
    echo "Dérivation de la liste des classes qui citent un cas..."
    if ! ( cd "$RACINE" && ./mvnw -B -q test -Dtest=CorrespondanceRecetteTest -DfailIfNoTests=false ); then
        echo "⚠️ la liste n'a pas pu être dérivée : CorrespondanceRecetteTest a échoué." >&2
        return 1
    fi

    local classes
    classes=$(classes_de_la_planche "$liste") || return 1
    printf 'Planche de contact : %d classe(s) à filmer.\n' "$(wc -l < "$liste")"
    lancer "$classes" "$RACINE/target/recette/planche.mkv"
}

# Les dimensions d'un film, pour que le carton soit taillé comme lui.
#
# ⚠️ `concat -c copy` exige des flux identiques : un carton d'une autre taille ferait échouer le
# recollage, et le clip repartirait sans titre sans qu'on sache pourquoi.
dimensions_du_film() { # <film>
    ffprobe -v error -select_streams v:0 -show_entries stream=width,height \
        -of csv=s=x:p=0 "$1" 2>/dev/null
}

# Le libellé d'un cas, tel que sa session le formule.
#
# ⚠️ Une puce de session se REPLIE sur plusieurs lignes physiques, avec deux espaces d'indentation
# pour la suite. Un `grep` n'en ramène que le premier morceau : la première revue du carton affichait
# « ... rien ne se », qui s'arrête juste avant le verbe et annonce le contraire du cas. On recolle
# donc la puce entière avant de la lire.
libelle_du_cas() { # <identifiant, ex. S1-26>
    local cas="$1"
    awk -v cas="$cas" '
        $0 ~ "^- \\*\\*" cas "\\*\\* " { dans = 1; texte = $0; next }
        dans && /^  [^ ]/ { sub(/^  /, " "); texte = texte $0; next }
        dans { print texte; dans = 0; exit }
        END  { if (dans) print texte }
    ' "$RACINE"/dev-docs/recette/sessions/*.md 2>/dev/null \
        | sed -e "s/^- \*\*${cas}\*\* · //" -e 's/^\*perceptif\* · //' \
              -e 's/\*\*//g' -e 's/\[\([^]]*\)\]([^)]*)/\1/g' -e 's/ *#[0-9]\+ *$//' \
        | abreger_sur_un_mot 170
}

# Abrège un texte sans couper un mot, et le DIT quand il abrège.
#
# ⚠️ Une coupe brutale s'est vue sur le premier clip réel : le carton de S6-27 finissait sur
# « habituelle. L'aper ». Un titre tronqué en plein mot se lit comme un défaut de rendu, et le
# lecteur ne sait pas s'il manque trois lettres ou trois phrases. Les points de suspension le disent.
abreger_sur_un_mot() { # <longueur maximale>  (texte sur l'entrée standard)
    awk -v n="$1" '{
        if (length($0) <= n) { print; next }
        court = substr($0, 1, n - 1)
        # ⚠️ On ne recule d un mot QUE si la coupe en tranche un. Quand elle tombe pile sur une fin
        # de mot - le caractère suivant est un espace - reculer perdrait un mot entier pour rien.
        if (substr($0, n, 1) != " ") {
            espace = match(court, /[ ][^ ]*$/)
            if (espace > 1) court = substr(court, 1, espace - 1)
        }
        sub(/[ ,;:.]+$/, "", court)
        print court "…"
    }'
}

# Le carton d'ouverture d'un clip : son identifiant, ce qu'il montre, et la classe qui le filme.
# Colle le carton d'ouverture devant un clip, EN PLACE.
#
# ⚠️ Cette fonction existe parce que le collage était écrit dans la boucle de montage, et que le cas
# d'auto-test qui le gardait le réécrivait à côté - avec un nom de sortie différent. Le cas passait,
# le code échouait, et l'écart tenait à une extension : la boucle écrivait dans « <clip>.titre »,
# que ffmpeg refuse (« Unable to choose an output format ») faute d'extension connue. Le carton n'a
# donc jamais été collé sur un vrai clip, et rien ne l'a dit - le collage est délibérément silencieux
# en cas d'échec, pour qu'un poste sans police rende quand même ses extraits.
#
# Un garde qui rejoue le geste au lieu de l'appeler ne garde pas ce geste-là.
coller_le_carton() { # <clip> <cas> <test>
    local clip="$1" cas="$2" test="$3"
    local carton="${clip%.mkv}.carton.mkv"
    local liste="${clip%.mkv}.liste"
    local monte="${clip%.mkv}.avec-titre.mkv"
    local code=0

    if carton_de_titre "$cas" "$(libelle_du_cas "$cas")" "$test" \
        "$(dimensions_du_film "$clip")" "$carton"; then
        printf "file '%s'\nfile '%s'\n" "$carton" "$clip" > "$liste"
        # ⚠️ Le carton est produit aux réglages du clip : `concat -c copy` recolle sans ré-encoder,
        # donc sans coûter une génération de qualité à l'extrait.
        if ffmpeg -nostdin -loglevel error -f concat -safe 0 -i "$liste" -c copy \
            -y "$monte" >/dev/null 2>&1; then
            mv "$monte" "$clip"
        else
            code=1
        fi
    else
        code=1
    fi
    rm -f "$carton" "$liste" "$monte"
    return "$code"
}

carton_de_titre() { # <cas> <libellé> <classe> <largeur>x<hauteur> <sortie>
    local cas="$1" libelle="$2" classe="$3" taille="$4" sortie="$5"
    local hauteur="${taille#*x}" atelier filtre="" total=0 y i=0 corps couleur ligne
    local -a lignes=() corps_de=() couleur_de=()

    # ⚠️ UNE ligne par fichier, et un `drawtext` par ligne. Un seul `drawtext` nourri d'un texte à
    # sauts de ligne paraissait plus simple - c'est ce que faisait la première version - mais il
    # DESSINE le saut de ligne : la première revue montrait un tofu en bout de chaque ligne. Un
    # filtre par ligne n'a aucun saut à dessiner, et centre chaque ligne pour elle-même au lieu de
    # caler un bloc ferré à gauche sur la largeur de sa ligne la plus longue.
    atelier=$(mktemp -d)

    lignes+=("$cas");            corps_de+=(46); couleur_de+=("0xffffff")
    # ⚠️ Le libellé se REPLIE, il ne se tronque pas. Coupé à la largeur, il s'arrêtait en plein mot :
    # un titre tronqué annonce autre chose que ce que le clip montre.
    # ⚠️ `|| [ -n "$ligne" ]` : `fold` ne termine pas sa DERNIÈRE ligne par un saut, et `read` rend
    # alors 1 tout en ayant rempli la variable. Sans ce garde, le carton perdait la fin du libellé -
    # il affichait « ... la saisie » et s'arrêtait là, sans rien signaler.
    while IFS= read -r ligne || [ -n "$ligne" ]; do
        [ -n "$ligne" ] || continue
        lignes+=("$ligne");      corps_de+=(30); couleur_de+=("0xd6d6e6")
    done < <(printf '%s' "$libelle" | fold -s -w 54)
    lignes+=("$classe");         corps_de+=(22); couleur_de+=("0x9494ac")

    for corps in "${corps_de[@]}"; do total=$(( total + corps * 3 / 2 )); done
    y=$(( (hauteur - total) / 2 ))

    for ligne in "${lignes[@]}"; do
        corps="${corps_de[$i]}"; couleur="${couleur_de[$i]}"
        printf '%s' "$ligne" > "$atelier/$i"
        [ -n "$filtre" ] && filtre="$filtre,"
        filtre="${filtre}drawtext=fontfile=${POLICE_CARTON}:textfile=$atelier/$i"
        filtre="${filtre}:fontcolor=${couleur}:fontsize=${corps}:x=(w-text_w)/2:y=${y}"
        y=$(( y + corps * 3 / 2 ))
        i=$(( i + 1 ))
    done

    ffmpeg -nostdin -loglevel error -f lavfi -i "color=c=0x1a1a2e:s=${taille}:d=2:r=25" \
        -vf "$filtre" -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p \
        -y "$sortie" >/dev/null 2>&1
    local code=$?
    rm -rf "$atelier"
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

    export -f part_utile profil_luminance couper_par_luminance classes_de_la_planche
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

    # --- le carton d'ouverture d'un clip (#4053) ---
    # ⚠️ Retour de la première revue humaine des clips : « ça montre bien ce qui est attendu, mais il
    # faudrait une diapo de titre pour comprendre ce qu'on regarde ».
    #
    # ⚠️ DEUX pièges à la fois, et les cas voisins les évitaient sans que la raison soit écrite.
    #
    # `RECETTE_RELANCE=1` d'abord : sans lui, sur une session Wayland, la copie sourcée atteint la
    # relance de la ligne 44 et s'EXEC elle-même. Le shell appelant est remplacé, et rien après
    # `source` ne s'exécute - le cas ne rougit pas, il disparaît. C'est #3883 pris par l'autre bout.
    #
    # Un SEUL argument ensuite : ce script n'a pas de garde de sourçage, son aiguillage tourne à la
    # fin, et `${1:---aide}` prend le second argument pour une classe de test. Sourcé avec deux, il
    # tombe dans `*) lancer "$@"` et tente de FILMER une classe portant le nom du chemin qu'on lui a
    # passé.
    essai "le libellé d un cas se lit dans sa session" vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            case "$(libelle_du_cas S1-26)" in *"sans saut"*) exit 0 ;; *) exit 1 ;; esac' "${BASH_SOURCE[0]}"
    # ⚠️ Un cas peut être annoté avant d'être rédigé : le libellé manque alors, et le montage doit
    # continuer. Un carton sans phrase vaut mieux qu'un clip qui n'existe pas.
    essai "un cas sans libellé ne fait pas échouer" vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1; libelle_du_cas S9-99 >/dev/null' "${BASH_SOURCE[0]}"
    essai "et il ne renvoie RIEN, pas un faux libellé" vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1; [ -z "$(libelle_du_cas S9-99)" ]' "${BASH_SOURCE[0]}"
    essai "le carton se fabrique et dure deux secondes" vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            carton_de_titre "S1-26" "La modale s ouvre sans saut" "ScenarioPerceptifConnexionTest" 320x180 "$d/c.mkv" || exit 1
            duree=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$d/c.mkv")
            awk -v x="$duree" "BEGIN{exit !(x > 1.5 && x < 2.5)}"' "${BASH_SOURCE[0]}"
    # ⚠️ LE cas qui porte le carton : il doit montrer QUELQUE CHOSE. Un carton noir - police absente,
    # texte non rendu - passerait un contrôle d'existence et ne dirait rien à qui regarde.
    essai "et il n est pas vide : du texte y paraît" vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            carton_de_titre "S1-26" "La modale s ouvre sans saut" "Scenario" 320x180 "$d/c.mkv" || exit 1
            awk -v p="$(part_utile "$d/c.mkv")" "BEGIN{exit !(p > 0)}"' "${BASH_SOURCE[0]}"

    # ⚠️ Les deux cas ci-dessus ont laissé passer TROIS cartons faux : un avec un tofu en bout de
    # chaque ligne, un dont le libellé s'arrêtait en plein mot, un qui perdait sa dernière ligne de
    # repli. Tous les trois duraient deux secondes et portaient des pixels clairs. Compter des pixels
    # dit qu'il y a de l'encre, pas ce qui est écrit : ce cas RELIT le carton.
    #
    # L'assertion porte sur la FIN du libellé - c'est elle que les trois défauts mangeaient.
    essai "et on y relit le libellé JUSQU AU BOUT" vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            command -v tesseract >/dev/null || exit 0
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            carton_de_titre "S1-26" "$(libelle_du_cas S1-26)" "Scenario" 1280x720 "$d/c.mkv" || exit 1
            ffmpeg -nostdin -loglevel error -i "$d/c.mkv" -vframes 1 -y "$d/c.png" >/dev/null 2>&1
            tesseract "$d/c.png" - --psm 6 -l fra 2>/dev/null | grep -q "apr.s coup"' "${BASH_SOURCE[0]}"

    # ⚠️ L abrègement ne coupe pas un mot, et il le DIT. Vu sur le premier clip réel : le carton de
    # S6-27 finissait sur « habituelle. L aper ». Un titre tronqué en plein mot se lit comme un
    # défaut de rendu, et rien ne dit s il manque trois lettres ou trois phrases.
    essai "un libellé long s abrège sur un mot entier"    vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            court=$(printf "un deux trois quatre cinq six" | abreger_sur_un_mot 14)
            [ "$court" = "un deux trois…" ]' "${BASH_SOURCE[0]}"
    essai "et un libellé court reste intact"             vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            [ "$(printf "court" | abreger_sur_un_mot 40)" = "court" ]' "${BASH_SOURCE[0]}"

    # --- le fork unique (#4056) ---
    # ⚠️ Le cas qui manquait au profil, et qui a coûté quatorze rouges. Un pom qui laisse `1C` fait
    # tourner les classes en parallèle sur un écran unique : les clips existent, les tests rougissent,
    # et le banc n'a aucune raison de soupçonner le pom.
    sed 's|<surefire.forkCount>1<|<surefire.forkCount>1C<|' "$POM" > "$tmp/forks-multiples.xml"
    # --- la configuration du gestionnaire de fenêtres (#4075) ---
    # ⚠️ Sans elle, openbox reprend les défauts de la distribution et place les fenêtres autrement
    # d'une machine à l'autre : la modale de S1-26 paraissait centrée en local et collée en haut à
    # gauche sur le clip publié, à partir du MÊME commit.
    essai "la configuration du banc est présente"        vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1; verifier_config_openbox' "${BASH_SOURCE[0]}"
    essai "une configuration absente est REFUSÉE"        rouge \
        env RECETTE_RELANCE=1 CONFIG_OPENBOX=/inexistant.xml \
        bash -c 'source "$0" >/dev/null 2>&1; verifier_config_openbox' "${BASH_SOURCE[0]}"

    essai "un profil à plusieurs forks est REFUSÉ" rouge \
        env RECETTE_RELANCE=1 POM_A_VERIFIER="$tmp/forks-multiples.xml" \
        bash -c 'source "$0"; verifier_profil' "${BASH_SOURCE[0]}"

    # --- l'horloge du banc (#4056) ---
    # ⚠️ LE cas qui manquait, et qui a coûté un tournage. `date +%s%3N` n'est pas portable : certaines
    # versions ignorent le modificateur de largeur et rendent les neuf chiffres de `%N`, soit un
    # instant un MILLION de fois trop grand. Le banc n'échouait pas pour autant - il calculait des
    # plages négatives et concluait « les repères ne décrivent pas CE film », accusant les repères
    # d'un défaut de l'horloge.
    essai "l instant du banc tient sur treize chiffres"   vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            i=$(instant_en_millisecondes)
            [ "${#i}" = 13 ]' "${BASH_SOURCE[0]}"
    # ⚠️ Et qu il soit plausible, pas seulement long : treize chiffres au hasard passeraient le cas
    # ci-dessus. On le compare aux secondes de l horloge, la seule référence disponible.
    essai "et il vaut bien les secondes fois mille"       vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            i=$(instant_en_millisecondes); s=$(date +%s)
            awk -v i="$i" -v s="$s" "BEGIN{ d = i / 1000 - s; exit !(d >= -2 && d <= 2) }"' "${BASH_SOURCE[0]}"

    # ⚠️ Et celui-ci éprouve le collage TEL QU'IL SE FAIT, en appelant `coller_le_carton` et non en
    # rejouant ses gestes à côté. Sa première version les rejouait, avec un nom de sortie différent :
    # elle passait au vert pendant que le code échouait, parce que la boucle écrivait dans
    # « <clip>.titre », sans extension connue de ffmpeg. Le carton n'a jamais été collé sur un vrai
    # clip, et le collage étant silencieux en cas d'échec, rien ne l'a dit.
    #
    # Le cas nomme donc son clip comme la production le nomme, et laisse la fonction faire le reste.
    essai "le carton se colle DEVANT le clip, en place"   vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            clip="$d/UneClasseTest.un_test.mkv"
            ffmpeg -nostdin -loglevel error -f lavfi -i "color=c=green:s=320x180:d=4:r=25" \
                -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -y "$clip" >/dev/null 2>&1
            avant=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$clip")
            coller_le_carton "$clip" "S1-26" "UneClasseTest.un_test" || exit 1
            apres=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$clip")
            awk -v a="$avant" -v b="$apres" "BEGIN{exit !(b > a + 1.5 && b < a + 2.5)}" || exit 1
            set -- $(ffmpeg -nostdin -loglevel error -i "$clip" -vframes 1 -vf scale=1:1 \
                -f rawvideo -pix_fmt rgb24 - 2>/dev/null | od -An -tu1)
            [ "$1" -lt 60 ] && [ "$2" -lt 60 ] && [ "$3" -gt "$2" ]' "${BASH_SOURCE[0]}"
    # ⚠️ Et il ne laisse RIEN derrière lui : un carton ou une liste oubliés dans le dossier des clips
    # partiraient dans l'artefact et dans la publication, sous un nom que personne n'attend.
    essai "et il ne laisse aucun fichier de travail"      vert \
        env RECETTE_RELANCE=1 bash -c 'source "$0" >/dev/null 2>&1
            d=$(mktemp -d); trap "rm -rf $d" EXIT
            clip="$d/UneClasseTest.un_test.mkv"
            ffmpeg -nostdin -loglevel error -f lavfi -i "color=c=green:s=320x180:d=4:r=25" \
                -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -y "$clip" >/dev/null 2>&1
            coller_le_carton "$clip" "S1-26" "UneClasseTest.un_test" || exit 1
            [ "$(ls -A "$d" | wc -l)" = 1 ]' "${BASH_SOURCE[0]}"

    # --- la RELANCE elle-même (#4047) ---
    # ⚠️ Le cas ci-dessous éprouve le contrôle ; celui-ci éprouve la RELANCE, qui n'était gardée par
    # rien. C'est pourtant elle qui a rendu « WAYLAND_DISPLAY posé » faussement vert : la copie
    # sourcée se relançait, et le contrôle ne voyait plus rien à signaler.
    #
    # Le levier est `--verifier`, qui sort sans rien lancer. Avec WAYLAND_DISPLAY posé et AUCUN
    # drapeau, la ligne 44 doit s'exécuter et retirer la variable : la sortie ne porte alors aucune
    # plainte Wayland. Si la relance cessait d'opérer, la plainte reviendrait - et c'est exactement
    # ce que ce cas verrait.
    #
    # ⚠️ On lit la SORTIE, pas le code de retour : `--verifier` rougit aussi pour un serveur X
    # absent, ce qui est le cas sur un poste de développement. Un cas qui lirait le code confondrait
    # les deux causes.
    # ⚠️ `env -u RECETTE_RELANCE` : l'environnement de l'auto-test PORTE déjà le drapeau, si bien
    # qu'un cas qui ne le retire pas hérite de la condition qu'il prétend poser - et observe donc
    # l'absence de relance en croyant observer sa présence. C'est le piège de #3883 exactement, une
    # génération plus loin : un cas doit poser son point de départ, jamais en hériter.
    essai "la relance retire WAYLAND_DISPLAY" vert \
        bash -c 'sortie=$(env -u RECETTE_RELANCE WAYLAND_DISPLAY=wayland-cas bash "$0" --verifier 2>&1)
            case "$sortie" in *"WAYLAND_DISPLAY est posé"*) exit 1 ;; *) exit 0 ;; esac' "${BASH_SOURCE[0]}"
    # ⚠️ Et le témoin : avec le drapeau DÉJÀ posé, la relance ne doit pas avoir lieu, donc la plainte
    # doit paraître. Sans ce second cas, le premier passerait aussi si la relance retirait la
    # variable pour une raison étrangère - ou si plus rien ne la posait jamais.
    essai "sans relance, la plainte Wayland paraît" rouge \
        bash -c 'sortie=$(WAYLAND_DISPLAY=wayland-cas RECETTE_RELANCE=1 bash "$0" --verifier 2>&1)
            case "$sortie" in *"WAYLAND_DISPLAY est posé"*) exit 1 ;; *) exit 0 ;; esac' "${BASH_SOURCE[0]}"

    # --- WAYLAND_DISPLAY ---
    essai "WAYLAND_DISPLAY retiré" vert  env -u WAYLAND_DISPLAY bash -c 'source "$0"; verifier_wayland' "${BASH_SOURCE[0]}"
    # ⚠️ `RECETTE_RELANCE=1` est INDISPENSABLE, et son absence a rendu ce cas vert pendant tout ce
    # temps sans qu'il éprouve quoi que ce soit. `source` rejoue le script depuis le début, donc la
    # relance de la ligne 44 : avec WAYLAND_DISPLAY posé et le drapeau absent, la copie sourcée
    # s'exec elle-même SANS WAYLAND_DISPLAY, si bien que `verifier_wayland` ne voyait plus rien à
    # signaler et rendait 0 - vert, là où l'on attend rouge.
    #
    # Le cas ne passait donc que sur une machine dont la session est en Wayland : le lancement
    # extérieur y ayant déjà posé le drapeau, la copie sourcée en héritait. Sur le runner, qui n'a
    # pas de session Wayland, il a rougi au premier passage en CI (#3883).
    essai "WAYLAND_DISPLAY posé"   rouge env WAYLAND_DISPLAY=wayland-0 RECETTE_RELANCE=1 bash -c 'source "$0"; verifier_wayland' "${BASH_SOURCE[0]}"

    # --- le pointeur, sur de VRAIS serveurs X ---
    Xvfb :91 -screen 0 "$TAILLE" -nolisten tcp >/dev/null 2>&1 &
    local nu=$!
    Xvfb :92 -screen 0 "$TAILLE" -nolisten tcp >/dev/null 2>&1 &
    local avec=$!
    sleep 2
    DISPLAY=:92 openbox --sm-disable --config-file "$CONFIG_OPENBOX" >/dev/null 2>&1 &
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

    # --- le montage par cas, sur un film dont on SAIT où est le geste ---
    #
    # Un sandwich de 6 s : noir, puis blanc de 2 s à 4 s, puis noir. Le journal est écrit pour
    # que la plage tombe sur le blanc. C'est la seule façon d'éprouver l'arithmétique de `t0`
    # sans dépendre d'une vraie séance - et sans elle, un décalage d'une seconde passerait,
    # puisqu'il ne casse rien : il déplace.
    ffmpeg -nostdin -loglevel error -f lavfi -i color=black:s=320x240:r=10:d=6 \
        -f lavfi -i color=white:s=320x240:r=10:d=6 \
        -filter_complex "[0:v][1:v]overlay=enable='between(t,2,4)'" \
        -c:v libx264 -preset ultrafast -pix_fmt yuv420p -y "$tmp/sandwich.mkv" >/dev/null 2>&1

    # Arrêt à 1 000 000 000 000 ms, brut de 6 s : l'image 0 est donc à 999 999 994 s.
    printf '# entête\n999999996000\tdebut\tExemple.geste\tS1-01\n999999998000\tfin\tExemple.geste\tS1-01\n' \
        > "$tmp/reperes-justes.tsv"
    # Les mêmes repères, décalés de 3 s : la plage tombe alors sur le noir de fin.
    printf '# entête\n999999999000\tdebut\tExemple.geste\tS1-01\n1000000001000\tfin\tExemple.geste\tS1-01\n' \
        > "$tmp/reperes-decales.tsv"
    printf '# entête seule\n' > "$tmp/reperes-vides.tsv"

    essai "un montage aligné sur le geste est accepté" vert \
        montage_par_cas "$tmp/sandwich.mkv" "$tmp/reperes-justes.tsv" "$tmp/clips-ok" 1000000000000
    essai "des repères décalés de 3 s sont REFUSÉS" rouge \
        montage_par_cas "$tmp/sandwich.mkv" "$tmp/reperes-decales.tsv" "$tmp/clips-ko" 1000000000000
    essai "un journal sans passage ne casse rien" vert \
        montage_par_cas "$tmp/sandwich.mkv" "$tmp/reperes-vides.tsv" "$tmp/clips-vide" 1000000000000
    essai "l'index nomme le cas, pas seulement le test" vert \
        bash -c 'grep -q "| S1-01 |" "$1/index.md"' _ "$tmp/clips-ok"
    essai "un montage refusé ne laisse AUCUN clip" vert \
        bash -c '[ ! -d "$1" ]' _ "$tmp/clips-ko"

    # Le cas que la première séance réelle a fait échouer : le geste appartient à un test qui ne
    # cite AUCUN cas. Il doit couvrir - sinon le contrôle refuse un alignement juste - et ne
    # produire aucun extrait, puisque personne n'irait le chercher.
    printf '# entête\n999999996000\tdebut\tExemple.sans_cas\t\n999999998000\tfin\tExemple.sans_cas\t\n' \
        > "$tmp/reperes-sans-cas.tsv"

    # --- la manière d'auditer : en regardant, ou en lisant (#3835) ---
    #
    # Deux tests cités dans la MÊME séance : l'un tombe sur le geste, l'autre sur le noir. Un test
    # qui n'ouvre aucune fenêtre est le cas normal - un ViewModel en cite et ne montre rien - et
    # l'index doit le dire, plutôt que de proposer la même case à cocher aux deux.
    printf '# entête\n' > "$tmp/reperes-mixtes.tsv"
    printf '999999996500\tdebut\tExemple.visible\tS1-01\n' >> "$tmp/reperes-mixtes.tsv"
    printf '999999997500\tfin\tExemple.visible\tS1-01\n' >> "$tmp/reperes-mixtes.tsv"
    printf '999999999000\tdebut\tExemple.invisible\tS1-02\n' >> "$tmp/reperes-mixtes.tsv"
    printf '999999999500\tfin\tExemple.invisible\tS1-02\n' >> "$tmp/reperes-mixtes.tsv"

    essai "un montage mêlant visible et invisible est accepté" vert \
        montage_par_cas "$tmp/sandwich.mkv" "$tmp/reperes-mixtes.tsv" "$tmp/clips-mixtes" 1000000000000
    essai "le cas qui a paru à l'écran s'audite EN REGARDANT" vert \
        bash -c 'grep -q "| S1-01 |.*| en regardant |" "$1/index.md"' _ "$tmp/clips-mixtes"
    essai "le cas qui n'a rien montré s'audite EN LISANT" vert \
        bash -c 'grep -q "| S1-02 |.*| en lisant le test |" "$1/index.md"' _ "$tmp/clips-mixtes"
    essai "et il ne se voit PAS proposer le même verdict" vert \
        bash -c '! grep -q "| S1-02 |.*| en regardant |" "$1/index.md"' _ "$tmp/clips-mixtes"

    # --- la liste de la planche : refuser le vide plutôt que filmer un décor ---
    printf 'MainViewTest\nMesSitesViewTest\n' > "$tmp/liste-pleine.txt"
    : > "$tmp/liste-vide.txt"

    essai "une liste de classes absente est REFUSÉE" rouge \
        classes_de_la_planche "$tmp/liste-inexistante.txt"
    essai "une liste de classes vide est REFUSÉE" rouge \
        classes_de_la_planche "$tmp/liste-vide.txt"
    essai "une liste pleine rend les classes séparées par des virgules" vert \
        bash -c '[ "$(classes_de_la_planche "$1")" = "MainViewTest,MesSitesViewTest" ]' _ "$tmp/liste-pleine.txt"

    essai "un test NON cité couvre quand même le geste" vert \
        montage_par_cas "$tmp/sandwich.mkv" "$tmp/reperes-sans-cas.tsv" "$tmp/clips-sc" 1000000000000
    essai "et il ne produit aucun extrait" vert \
        bash -c '! ls "$1"/*.mkv >/dev/null 2>&1' _ "$tmp/clips-sc"

    # --- les bornes d'un extrait, contre son voisin (#4113) ---
    #
    # ⚠️ La marge existe pour ne pas couper au ras du geste. Ajoutée à l'aveugle, elle fait finir un
    # clip sur l'écran du cas SUIVANT : constaté à l'image sur S6-28, qui se terminait sur la modale
    # de connexion d'une autre classe. Les trois cas ci-dessous tiennent les deux moitiés de la
    # règle - borner quand le voisin est proche, et garder la marge entière quand il est loin.
    printf '%s\n' \
        "1000${ONGLET}debut${ONGLET}TestA${ONGLET}S9-01" \
        "2000${ONGLET}fin${ONGLET}TestA${ONGLET}S9-01" \
        "2200${ONGLET}debut${ONGLET}TestB${ONGLET}S9-02" \
        "3000${ONGLET}fin${ONGLET}TestB${ONGLET}S9-02" > "$tmp/journal-serre.tsv"
    printf '%s\n' \
        "1000${ONGLET}debut${ONGLET}TestA${ONGLET}S9-01" \
        "2000${ONGLET}fin${ONGLET}TestA${ONGLET}S9-01" \
        "9000${ONGLET}debut${ONGLET}TestB${ONGLET}S9-02" \
        "9500${ONGLET}fin${ONGLET}TestB${ONGLET}S9-02" > "$tmp/journal-espace.tsv"

    essai "un extrait ne déborde pas sur le début du cas suivant" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(plages_du_journal 0 "$1" | head -1 | cut -f3)" = 2.20 ]' \
        "${BASH_SOURCE[0]}" "$tmp/journal-serre.tsv"
    essai "ni en arriere sur la fin du cas precedent" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(plages_du_journal 0 "$1" | tail -1 | cut -f2)" = 2.00 ]' \
        "${BASH_SOURCE[0]}" "$tmp/journal-serre.tsv"
    # ⚠️ Le cas qui MANQUAIT, et dont l'absence a coûté un tournage entier. Deux cas collés - la fin
    # de l'un est le début de l'autre - donnaient une plage de durée nulle ; ffmpeg écrivait un fichier
    # vide, et le remuxage s'arrêtait dessus. Le banc n'a publié aucun clip.
    # ⚠️ Un cas COURT coince entre deux autres : c'est LUI qui s'effondre. Mon premier jeu d'essai
    # posait deux cas simplement adjacents, et il ne s'effondrait pas - le cas restait vert sans la
    # garde, donc il ne gardait rien. Mesure avant conclusion, ici comme ailleurs.
    printf '%s\n' \
        "1000${ONGLET}debut${ONGLET}TestA${ONGLET}S9-01" \
        "2000${ONGLET}fin${ONGLET}TestA${ONGLET}S9-01" \
        "1900${ONGLET}debut${ONGLET}TestB${ONGLET}S9-02" \
        "1950${ONGLET}fin${ONGLET}TestB${ONGLET}S9-02" \
        "2000${ONGLET}debut${ONGLET}TestC${ONGLET}S9-03" \
        "2600${ONGLET}fin${ONGLET}TestC${ONGLET}S9-03" > "$tmp/journal-colle.tsv"
    essai "un cas court coince ne rend pas de plage effondree" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; plages_du_journal 0 "$1" 2>/dev/null \
            | awk -F"\t" "{ if (\$3 - \$2 < 0.20) exit 1 }"' \
        "${BASH_SOURCE[0]}" "$tmp/journal-colle.tsv"

    # --- la queue s'arrete a l'IMAGE, pas au repere (#4122) ---
    #
    # ⚠️ Le defaut que ce cas garde : la fenetre du cas SUIVANT parait pendant son montage, donc avant
    # que son repere soit ecrit. Borner sur le repere laissait la modale de connexion a la fin de
    # S6-28. Le profil dit ou l ecran devient noir, cest-a-dire ou la fenetre du cas a disparu.
    #
    # Il se joue sans video : le profil est un fichier « instant<TAB>luminance ».
    : > "$tmp/profil.tsv"
    for centieme in $(seq 0 5 30); do
        printf '%s.%02d%s100\n' "$((centieme / 100))" "$((centieme % 100))" "$ONGLET" >> "$tmp/profil.tsv"
    done
    printf '2.20%s5\n2.40%s5\n' "$ONGLET" "$ONGLET" >> "$tmp/profil.tsv"
    printf '%s\n' \
        "1000${ONGLET}debut${ONGLET}TestA${ONGLET}S9-01" \
        "2000${ONGLET}fin${ONGLET}TestA${ONGLET}S9-01" > "$tmp/journal-seul.tsv"
    essai "sans profil, la marge entiere est gardee" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(plages_du_journal 0 "$1" | cut -f3)" = 2.50 ]' \
        "${BASH_SOURCE[0]}" "$tmp/journal-seul.tsv"
    # ⚠️ Le cas qui MANQUAIT, et dont l absence a coute onze clips. Un test tres rapide nouvre aucune
    # fenetre : son ecran est noir des le depart. Si la coupe a limage sappliquait, sa plage tomberait
    # a 0,01 s, il serait ecarte, et son lecteur pointerait dans le vide sur la page de la doc.
    #
    # Leffondrement demande DEUX choses : un cas precedent qui remonte la borne de tete, et un ecran
    # deja noir. Cest exactement ce que le tournage a montre - les onze ecartes avaient des plages de
    # 0,01 a 0,19 s. Un cas isole ne seffondre jamais, la marge le protegeant : mon premier jeu
    # dessai restait donc vert sous le mutant, donc il ne gardait rien.
    printf '%s%s5\n%s%s5\n' "2.02" "$ONGLET" "2.60" "$ONGLET" > "$tmp/profil-noir.tsv"
    printf '%s\n' \
        "1000${ONGLET}debut${ONGLET}TestA${ONGLET}S9-01" \
        "2000${ONGLET}fin${ONGLET}TestA${ONGLET}S9-01" \
        "2005${ONGLET}debut${ONGLET}TestB${ONGLET}S9-02" \
        "2010${ONGLET}fin${ONGLET}TestB${ONGLET}S9-02" > "$tmp/journal-eclair.tsv"
    essai "un cas eclair sans fenetre garde son extrait" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; plages_du_journal 0 "$1" "$2" 2>/dev/null \
            | grep -q TestB' \
        "${BASH_SOURCE[0]}" "$tmp/journal-eclair.tsv" "$tmp/profil-noir.tsv"

    essai "la queue s arrete a la premiere image noire" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(plages_du_journal 0 "$1" "$2" | cut -f3)" = 2.20 ]' \
        "${BASH_SOURCE[0]}" "$tmp/journal-seul.tsv" "$tmp/profil.tsv"

    # ⚠️ Sans ce cas, un correctif qui raboterait TOUTES les queues passerait au vert, et la
    # respiration que la marge existe pour donner disparaitrait sans qu'aucun test ne le dise.
    essai "un voisin eloigne laisse la marge entiere" vert \
        bash -c 'BANC_SOURCE_SEULEMENT=1; source "$0"; [ "$(plages_du_journal 0 "$1" | head -1 | cut -f3)" = 2.50 ]' \
        "${BASH_SOURCE[0]}" "$tmp/journal-espace.tsv"

    kill "$nu" "$avec" "$wm" 2>/dev/null

    echo
    echo "$total cas, dont $rouges qui DOIVENT rougir."
    if [ "$echecs" -eq 0 ]; then echo "Auto-test concluant."; return 0; fi
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de ce script."
    return 1
}

# --------------------------------------------------------------------------------------------

# ⚠️ Un fichier qui se source doit dire ce qu'il fait à ce moment-là : ici, RIEN. Sans ce garde,
# `source ce-script` lançait un tournage complet - vécu, y compris depuis la copie principale du
# dépôt, et les cas d'auto-test qui se sourcent en déclenchaient un chacun.
#
# Le drapeau, et non « $0 vaut BASH_SOURCE » : sous `bash -c 'source "$0"' /chemin/du/banc`, les deux
# sont égaux et la comparaison conclut « lancé directement ». Le banc de documentation porte la même
# leçon depuis plus longtemps.
if [ -z "${BANC_SOURCE_SEULEMENT:-}" ]; then
    case "${1:---aide}" in
        --auto-test) auto_test ;;
        --planche)   planche ;;
        --verifier)  verifier_tout ;;
        --aide|-h)   sed -n '/^# Usage/,/^$/p' "${BASH_SOURCE[0]}" | sed 's/^# \?//' ;;
        *)           lancer "$@" ;;
    esac
fi
