#!/usr/bin/env bash
# Construit une AppImage à partir de l'app-image jpackage (#2107).
#
# Une AppImage est un fichier unique et exécutable : l'utilisateur le rend exécutable et le lance,
# sans décompresser ni installer. C'est le complément de l'archive portable, pour qui préfère un
# fichier à un dossier, et cela apporte l'intégration au menu des applications via le .desktop.
#
# Prérequis : `./mvnw -Pinstaller -Djpackage.type=app-image … verify` a produit target/dist/VigieChiro.
# Usage : construit-appimage.sh <version> <arch>      (ex. construit-appimage.sh 2.20.0 x86_64)
set -euo pipefail

VERSION="${1:?version attendue (ex. 2.20.0)}"
ARCH="${2:-x86_64}"

RACINE="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE="${RACINE}/target/dist/VigieChiro"
APPDIR="${RACINE}/target/AppDir"
SORTIE="${RACINE}/target/dist/VigieChiro-${VERSION}-linux-${ARCH}.AppImage"

if [ ! -d "${SOURCE}" ]; then
    echo "::error::${SOURCE} est absent : construire d'abord l'app-image jpackage." >&2
    exit 1
fi

# 1. L'AppDir. La convention AppImage veut l'application sous usr/, et à la RACINE de l'AppDir :
#    AppRun (le point d'entrée), le .desktop et l'icône nommée d'après la clé Icon= du .desktop.
rm -rf "${APPDIR}"
mkdir -p "${APPDIR}/usr"
cp -a "${SOURCE}/." "${APPDIR}/usr/"

install -m 755 "${RACINE}/.github/appimage/AppRun" "${APPDIR}/AppRun"
install -m 644 "${RACINE}/.github/appimage/vigiechiro.desktop" "${APPDIR}/vigiechiro.desktop"

# jpackage dépose l'icône de l'application dans lib/. On la reprend plutôt que d'en versionner une
# seconde copie, qui divergerait le jour où l'icône change.
ICONE="${APPDIR}/usr/lib/VigieChiro.png"
if [ ! -f "${ICONE}" ]; then
    echo "::error::icône introuvable (${ICONE}) : jpackage a changé sa disposition ?" >&2
    exit 1
fi
cp "${ICONE}" "${APPDIR}/vigiechiro.png"
# Les environnements de bureau lisent aussi le thème d'icônes : sans cette copie, l'entrée de menu
# s'affiche sans icône une fois l'AppImage intégrée.
mkdir -p "${APPDIR}/usr/share/icons/hicolor/256x256/apps"
cp "${ICONE}" "${APPDIR}/usr/share/icons/hicolor/256x256/apps/vigiechiro.png"
mkdir -p "${APPDIR}/usr/share/applications"
cp "${APPDIR}/vigiechiro.desktop" "${APPDIR}/usr/share/applications/"

# 2. appimagetool. Récupéré à une version FIGÉE : un outil qui change sous les pieds du build
#    changerait la forme de l'artefact publié sans qu'aucun commit ne le dise.
OUTIL="${RACINE}/target/appimagetool"
if [ ! -x "${OUTIL}" ]; then
    curl -sfL -o "${OUTIL}" \
        "https://github.com/AppImage/appimagetool/releases/download/1.9.0/appimagetool-${ARCH}.AppImage"
    chmod +x "${OUTIL}"
fi

# `--appimage-extract-and-run` : appimagetool est lui-même une AppImage, donc il lui faut FUSE pour
# se monter. Les conteneurs CI n'en ont pas toujours, et l'échec y est obscur ; cette option le fait
# s'extraire au lieu de se monter. Coûte quelques secondes, marche partout.
export APPIMAGE_EXTRACT_AND_RUN=1

# NE PAS définir SOURCE_DATE_EPOCH ici, même si l'idée d'un artefact reproductible est tentante :
# appimagetool passe déjà ses propres options de date à mksquashfs, qui refuse alors les deux
# ensemble et s'arrête sur « SOURCE_DATE_EPOCH and command line options can't be used at the same
# time to set timestamp(s) ». On le neutralise donc s'il vient de l'environnement.
unset SOURCE_DATE_EPOCH

rm -f "${SORTIE}"
"${OUTIL}" --no-appstream "${APPDIR}" "${SORTIE}"

rm -rf "${APPDIR}"
echo "AppImage produite : ${SORTIE}"
ls -lh "${SORTIE}"
