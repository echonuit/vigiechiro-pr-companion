#!/usr/bin/env bash
# Garde-fou : tout parcours filmé référencé par une page de doc utilisateur
# (docs/**/*.md, motif parcours-*.mp4) doit exister dans .github/assets/, et tout
# film présent doit correspondre à un parcours que le banc sait tourner.
#
# Pendant vidéo de check-doc-images.sh, avec une source de vérité différente : les
# captures ont un manifeste, les films n'en ont pas besoin - la liste des parcours
# EST dans le banc (`parcours_connu`), et c'est elle qu'on interroge. Un film qui
# survivrait au renommage de son parcours serait sinon publié indéfiniment.
#
# Ce qu'il ne vérifie PAS, et il faut le savoir : qu'un parcours connu du banc ait
# son film. Tourner demande un serveur d'affichage, openbox et une carte montée ;
# l'exiger de toute PR qui ajoute un scénario coûterait plus qu'il ne rapporte. Ce
# garde attrape la référence morte et le film orphelin, pas la publication en retard.
#
# Exit 0 si tout est cohérent, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Mode --site : vérifie que les chemins RÉSOLVENT dans le site construit.
#
# Pourquoi ce mode existe. La première version de ce garde vérifiait que le film existait dans
# .github/assets/ - et elle est restée VERTE pendant que le site était cassé. MkDocs ne réécrit pas
# les chemins du HTML brut, seulement ceux du Markdown : un `<source src="../assets/...">` reste
# littéral et pointe un cran trop haut. La page rendait un lecteur vidéo vide, sans une erreur.
#
# Vérifier l'existence du fichier source ne prouve donc rien de ce qui compte. Ce mode ouvre les
# pages construites et suit chaque référence comme un navigateur le ferait.
if [ "${1:-}" = "--site" ]; then
    site="${2:-site}"
    if [ ! -d "$site" ]; then
        echo "✗ $site : site construit introuvable ; rien ne peut être vérifié."
        exit 1
    fi
    python3 - "$site" <<'PY'
import os, re, sys

site = sys.argv[1]
motif = re.compile(r'<(?:source|a)[^>]*(?:src|href)="([^"]*parcours-[a-z0-9-]+\.mp4)"')
erreurs = 0
vues = 0
for dossier, _, fichiers in os.walk(site):
    for fichier in fichiers:
        if not fichier.endswith(".html"):
            continue
        page = os.path.join(dossier, fichier)
        with open(page, encoding="utf-8") as f:
            html = f.read()
        for lien in motif.findall(html):
            vues += 1
            cible = os.path.normpath(os.path.join(dossier, lien))
            if not os.path.isfile(cible):
                rel = os.path.relpath(page, site)
                print(f"✗ {rel} : « {lien} » ne résout sur aucun fichier du site")
                erreurs += 1
if erreurs:
    print()
    print(f"✗ {erreurs} référence(s) de parcours cassée(s) dans le site construit.")
    print("  MkDocs ne réécrit PAS les chemins du HTML brut : depuis docs/ecrans/*.md,")
    print("  un parcours se référence en ../../assets/parcours/, comme les captures.")
    sys.exit(1)
if vues == 0:
    print("Aucune référence de parcours dans le site : rien à vérifier.")
else:
    print(f"✓ Les {vues} référence(s) de parcours résolvent dans le site construit.")
PY
    exit $?
fi

# Auto-test, sur le modèle de check-doc-images.sh : le script se réinvoque sur une
# arborescence jetable, donc le cas de test et le chemin réel sont le même code.
if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    cas=0
    rouges=0
    verifie() { # <attendu> <libellé>
        cas=$((cas + 1))
        [ "$1" != 0 ] && rouges=$((rouges + 1))
        DOC_VIDEOS_RACINE="$bac" bash "$ICI/$(basename "${BASH_SOURCE[0]}")" >/dev/null 2>&1
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
    mkdir -p "$bac/.github/assets" "$bac/docs/ecrans" "$bac/scripts/doc-video"
    # Un banc minimal : ce garde n'a besoin que de la liste des parcours.
    cat > "$bac/scripts/doc-video/filme-un-parcours.sh" <<'BANC'
parcours_connu() {
    case "$1" in
        declarer-un-carre) printf '45\tnon\n' ;;
        importer-une-nuit) printf '120\toui\n' ;;
        *) return 1 ;;
    esac
}
BANC

    echo "AUTO-TEST"
    : > "$bac/docs/ecrans/vide.md"
    verifie 0 "aucun film référencé : rien à vérifier"

    printf 'voir parcours-declarer-un-carre.mp4\n' > "$bac/docs/ecrans/vide.md"
    verifie 1 "un film RÉFÉRENCÉ mais absent est refusé"

    : > "$bac/.github/assets/parcours-declarer-un-carre.mp4"
    verifie 0 "un film référencé et présent est accepté"

    # Le cas qui porte ce garde : un film dont le parcours n'existe plus.
    : > "$bac/.github/assets/parcours-ancien-nom.mp4"
    verifie 1 "un film ORPHELIN de son parcours est refusé"
    rm "$bac/.github/assets/parcours-ancien-nom.mp4"

    # Sans banc lisible, on ne peut rien affirmer : il faut refuser, pas passer.
    mv "$bac/scripts/doc-video/filme-un-parcours.sh" "$bac/scripts/doc-video/absent"
    verifie 1 "sans le banc, le garde REFUSE au lieu de passer"
    mv "$bac/scripts/doc-video/absent" "$bac/scripts/doc-video/filme-un-parcours.sh"

    # --- le mode --site, celui qui suit les chemins comme un navigateur ---
    verifie_site() { # <attendu> <libellé>
        cas=$((cas + 1))
        [ "$1" != 0 ] && rouges=$((rouges + 1))
        bash "$ICI/$(basename "${BASH_SOURCE[0]}")" --site "$bac/site" >/dev/null 2>&1
        code=$?
        if [ "$code" = "$1" ]; then
            printf '  [OK   ] %-52s -> %s\n' "$2" "$code"
        else
            printf '  [ÉCHEC] %-52s -> %s (attendu %s)\n' "$2" "$code" "$1"
            echecs=$((echecs + 1))
        fi
    }
    mkdir -p "$bac/site/ecrans/sites" "$bac/site/assets/parcours"
    : > "$bac/site/assets/parcours/parcours-declarer-un-carre.mp4"
    printf '<video><source src="../../assets/parcours/parcours-declarer-un-carre.mp4"></video>\n' \
        > "$bac/site/ecrans/sites/index.html"
    verifie_site 0 "un chemin qui RÉSOUT est accepté"
    # LE cas : c'est ce chemin-là qui a été livré, et le garde d'alors le laissait passer.
    printf '<video><source src="../assets/parcours/parcours-declarer-un-carre.mp4"></video>\n' \
        > "$bac/site/ecrans/sites/index.html"
    verifie_site 1 "un chemin d un cran trop haut est refusé"
    rm -rf "$bac/site"
    verifie_site 1 "sans site construit, le garde REFUSE"

    echo ""
    echo "$cas cas, dont $rouges qui DOIVENT rougir."
    [ "$echecs" -eq 0 ] && { echo "Auto-test concluant."; exit 0; }
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de ce garde."
    exit 1
fi

RACINE="${DOC_VIDEOS_RACINE:-$(cd "$ICI/../.." && pwd)}"
cd "$RACINE" || exit 1

ASSETS=".github/assets"
BANC="scripts/doc-video/filme-un-parcours.sh"
ERREURS=0

if [ ! -f "$BANC" ]; then
    echo "✗ $BANC introuvable : la liste des parcours ne peut pas être lue."
    exit 1
fi

# La liste des parcours vient du BANC, pas d'une copie : c'est lui qui tourne les films.
connus=$(BANC_SOURCE_SEULEMENT=1 bash -c 'source "$0"
    for nom in $(grep -oE "^        [a-z-]+\) printf" "$0" | tr -d " )" | sed "s/printf//"); do
        parcours_connu "$nom" >/dev/null 2>&1 && echo "$nom"
    done' "$BANC" | sort -u)

if [ -z "$connus" ]; then
    echo "✗ aucun parcours lisible dans $BANC : le garde ne peut rien affirmer."
    exit 1
fi

referencees=$(grep -rhoE 'parcours-[a-z0-9-]+\.mp4' docs --include='*.md' 2>/dev/null | sort -u)
# `find` plutôt que `ls` : un nom de fichier inattendu (espace, accent) ne doit pas décaler
# l'inventaire en silence, puisque c'est justement une ABSENCE que cette garde constate.
presentes=$(find "$ASSETS" -maxdepth 1 -type f -name 'parcours-*.mp4' -printf '%f\n' 2>/dev/null \
    | grep -E '^parcours-[a-z0-9-]+\.mp4$' | sort -u)

while IFS= read -r film; do
    [ -z "$film" ] && continue
    if [ ! -f "$ASSETS/$film" ]; then
        echo "✗ $film : référencé par la doc mais ABSENT de $ASSETS/"
        ERREURS=$((ERREURS + 1))
    fi
done <<< "$referencees"

while IFS= read -r film; do
    [ -z "$film" ] && continue
    nom=${film#parcours-}
    nom=${nom%.mp4}
    if ! printf '%s\n' "$connus" | grep -qx "$nom"; then
        echo "✗ $film : présent, mais « $nom » n'est plus un parcours que le banc sait tourner"
        ERREURS=$((ERREURS + 1))
    fi
done <<< "$presentes"

if [ "$ERREURS" -eq 0 ]; then
    if [ -z "$referencees" ] && [ -z "$presentes" ]; then
        echo "Aucun parcours filmé : rien à vérifier."
    else
        echo "✓ Les parcours filmés référencés existent, et chacun a son scénario au banc."
    fi
    exit 0
fi
echo ""
echo "✗ $ERREURS parcours filmé(s) incohérent(s)."
echo "  Pour en ajouter un : un scénario dans $BANC, puis"
echo "  bash $BANC <nom> et le montage copié en $ASSETS/parcours-<nom>.mp4."
exit 1
