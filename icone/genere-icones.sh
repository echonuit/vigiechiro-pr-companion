#!/usr/bin/env bash
# Dérive toutes les icônes distribuées depuis la SEULE source vigiechiro.svg (#2144).
#
# Les fichiers produits sont versionnés : la CI n'a donc besoin ni d'Inkscape ni de cairosvg, et une
# release ne dépend pas de l'outillage graphique du poste. En contrepartie, ce script doit être
# relancé - et son résultat commité - à chaque retouche du SVG.
#
# Deux sorties, deux usages :
#   icone/derive/       -> ce que jpackage empaquette (un format par système)
#   src/main/resources/icones/ -> ce que l'application charge à l'exécution (Stage.getIcons())
#
# Prérequis : python3 avec cairosvg, et icotool (paquet icoutils).
# Usage : ./icone/genere-icones.sh
set -euo pipefail

RACINE="$(cd "$(dirname "$0")/.." && pwd)"
SVG="${RACINE}/icone/vigiechiro.svg"
DERIVE="${RACINE}/icone/derive"
RUNTIME="${RACINE}/src/main/resources/icones"

for outil in python3 icotool; do
    command -v "${outil}" >/dev/null || { echo "::error::${outil} est requis" >&2; exit 1; }
done
python3 -c "import cairosvg" 2>/dev/null || { echo "::error::le module python cairosvg est requis" >&2; exit 1; }

rm -rf "${DERIVE}" "${RUNTIME}"
mkdir -p "${DERIVE}" "${RUNTIME}"

# 1. Les rendus PNG. 1024 sert au type Retina de macOS, 16 à la barre de titre : c'est cette
#    dernière taille qui a dicté le dessin, une version plus fine s'y réduisant à une tache.
TAILLES=(16 32 48 64 128 256 512 1024)
python3 - "$SVG" "$DERIVE" "${TAILLES[@]}" <<'PY'
import sys
import cairosvg

svg, sortie, *tailles = sys.argv[1:]
for t in (int(x) for x in tailles):
    cairosvg.svg2png(url=svg, write_to=f"{sortie}/vigiechiro-{t}.png",
                     output_width=t, output_height=t)
print("PNG rendus :", ", ".join(tailles))
PY

# 2. Linux : jpackage attend un PNG unique.
cp "${DERIVE}/vigiechiro-512.png" "${DERIVE}/vigiechiro.png"

# 3. Windows : un ICO multi-tailles. Windows choisit la plus proche du contexte d'affichage, d'où
#    l'intérêt d'y mettre les petites - c'est ce qui évite un 256 écrasé en 16 par l'explorateur.
icotool -c -o "${DERIVE}/vigiechiro.ico" \
    "${DERIVE}/vigiechiro-16.png" \
    "${DERIVE}/vigiechiro-32.png" \
    "${DERIVE}/vigiechiro-48.png" \
    "${DERIVE}/vigiechiro-256.png"

# 4. macOS : un ICNS assemblé par nos soins, `magick …icns` écrivant en réalité un PNG déguisé que
#    jpackage refuse (cf. l'en-tête de ecrit-icns.py).
python3 "${RACINE}/icone/ecrit-icns.py" "${DERIVE}/vigiechiro.icns" \
    16:"${DERIVE}/vigiechiro-16.png" \
    32:"${DERIVE}/vigiechiro-32.png" \
    128:"${DERIVE}/vigiechiro-128.png" \
    256:"${DERIVE}/vigiechiro-256.png" \
    512:"${DERIVE}/vigiechiro-512.png" \
    1024:"${DERIVE}/vigiechiro-1024.png"

# 5. L'exécution. JavaFX choisit parmi les tailles fournies celle qui convient au contexte ; on
#    s'arrête à 256, au-delà c'est du poids embarqué pour rien dans le jar.
for t in 16 32 48 128 256; do
    cp "${DERIVE}/vigiechiro-${t}.png" "${RUNTIME}/vigiechiro-${t}.png"
done

# 6. Contrôle : un format annoncé mais mal écrit ne se voit qu'à l'usage, souvent trop tard.
echo
echo "=== contrôle des formats produits ==="
file "${DERIVE}/vigiechiro.ico" | grep -q "MS Windows icon" \
    || { echo "::error::vigiechiro.ico n'est pas un ICO" >&2; exit 1; }
file "${DERIVE}/vigiechiro.icns" | grep -q "Mac OS X icon" \
    || { echo "::error::vigiechiro.icns n'est pas un ICNS (magick écrit un PNG déguisé)" >&2; exit 1; }
file "${DERIVE}/vigiechiro.png" | grep -q "PNG image" \
    || { echo "::error::vigiechiro.png n'est pas un PNG" >&2; exit 1; }
file "${DERIVE}/vigiechiro.ico" "${DERIVE}/vigiechiro.icns" "${DERIVE}/vigiechiro.png"
echo
echo "Icônes dérivées. Penser à committer icone/derive/ et src/main/resources/icones/."
