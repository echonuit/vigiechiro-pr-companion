#!/usr/bin/env python3
"""Construit une AppImage a partir de l app-image jpackage (#2107, porte du bash en #5236).

Une AppImage est un fichier unique et executable : l utilisateur le rend executable et le lance, sans
decompresser ni installer. C est le complement de l archive portable, pour qui prefere un fichier a un
dossier, et cela apporte l integration au menu des applications via le `.desktop`.

Prerequis : `./mvnw -Pinstaller -Djpackage.type=app-image … verify` a produit
`target/dist/VigieChiroCompanion`.

Usage : python3 .github/scripts/construit_appimage.py <version> [arch]
        (ex. construit_appimage.py 2.20.0 x86_64)
"""

from __future__ import annotations

import os
import pathlib
import shutil
import subprocess
import sys
import urllib.request

RACINE = pathlib.Path(__file__).resolve().parents[2]

# Une version FIGEE : un outil qui change sous les pieds du build changerait la forme de l artefact
# publie sans qu aucun commit ne le dise.
APPIMAGETOOL = (
    "https://github.com/AppImage/appimagetool/releases/download/1.9.0/appimagetool-{arch}.AppImage"
)


def construire(version: str, arch: str = "x86_64") -> int:
    """Les deux etapes - l AppDir, puis appimagetool - et le code de sortie qui va avec."""
    source = RACINE / "target" / "dist" / "VigieChiroCompanion"
    appdir = RACINE / "target" / "AppDir"
    sortie = RACINE / "target" / "dist" / f"VigieChiroCompanion-{version}-linux-{arch}.AppImage"

    if not source.is_dir():
        print(
            f"::error::{source} est absent : construire d'abord l'app-image jpackage.",
            file=sys.stderr,
        )
        return 1

    # appimagetool valide le `.desktop` avec `desktop-file-validate` et s arrete net s il ne le
    # trouve pas, sur un message qui ne dit pas quel paquet installer. Le controle est fait ICI pour
    # que l echec nomme sa solution - et parce que la dependance est invisible autrement : l outil est
    # present sur la plupart des postes de developpement et absent des runners GitHub. C est
    # exactement l ecart qui a fait echouer la release v2.21.0 alors que la construction passait en
    # local.
    if shutil.which("desktop-file-validate") is None:
        print(
            "::error::desktop-file-validate est requis (paquet desktop-file-utils)", file=sys.stderr
        )
        return 1

    # 1. L AppDir. La convention AppImage veut l application sous `usr/`, et a la RACINE de l AppDir :
    #    AppRun, le `.desktop` et l icone nommee d apres la cle `Icon=` du `.desktop`.
    shutil.rmtree(appdir, ignore_errors=True)
    (appdir / "usr").mkdir(parents=True)
    shutil.copytree(source, appdir / "usr", dirs_exist_ok=True, symlinks=True)

    apprun = appdir / "AppRun"
    shutil.copy(RACINE / ".github/appimage/AppRun", apprun)
    apprun.chmod(0o755)
    bureau = appdir / "vigiechiro.desktop"
    shutil.copy(RACINE / ".github/appimage/vigiechiro.desktop", bureau)
    bureau.chmod(0o644)

    # jpackage depose l icone de l application dans `lib/`. On la reprend plutot que d en versionner
    # une seconde copie, qui divergerait le jour ou l icone change.
    icone = appdir / "usr" / "lib" / "VigieChiroCompanion.png"
    if not icone.is_file():
        print(
            f"::error::icône introuvable ({icone}) : jpackage a changé sa disposition ?",
            file=sys.stderr,
        )
        return 1
    shutil.copy(icone, appdir / "vigiechiro.png")
    # Les environnements de bureau lisent aussi le theme d icones : sans cette copie, l entree de
    # menu s affiche sans icone une fois l AppImage integree.
    theme = appdir / "usr/share/icons/hicolor/256x256/apps"
    theme.mkdir(parents=True, exist_ok=True)
    shutil.copy(icone, theme / "vigiechiro.png")
    applications = appdir / "usr/share/applications"
    applications.mkdir(parents=True, exist_ok=True)
    shutil.copy(bureau, applications / "vigiechiro.desktop")

    # 2. appimagetool, recupere a une version figee.
    outil = RACINE / "target" / "appimagetool"
    if not (outil.is_file() and os.access(outil, os.X_OK)):
        urllib.request.urlretrieve(APPIMAGETOOL.format(arch=arch), outil)
        outil.chmod(0o755)

    environnement = dict(os.environ)
    # `--appimage-extract-and-run` : appimagetool est lui-meme une AppImage, donc il lui faut FUSE
    # pour se monter. Les conteneurs CI n en ont pas toujours, et l echec y est obscur ; cette option
    # le fait s extraire au lieu de se monter.
    environnement["APPIMAGE_EXTRACT_AND_RUN"] = "1"
    # NE PAS definir SOURCE_DATE_EPOCH, meme si l idee d un artefact reproductible est tentante :
    # appimagetool passe deja ses propres options de date a mksquashfs, qui refuse alors les deux
    # ensemble. On le neutralise donc s il vient de l environnement.
    environnement.pop("SOURCE_DATE_EPOCH", None)

    sortie.unlink(missing_ok=True)
    rendu = subprocess.run(
        [str(outil), "--no-appstream", str(appdir), str(sortie)], env=environnement, check=False
    )
    if rendu.returncode != 0:
        return rendu.returncode

    shutil.rmtree(appdir, ignore_errors=True)
    # `flush` : sans lui, cette ligne sort APRES le `ls` qu elle introduit, et apres la sortie
    # d appimagetool. La sortie standard de Python est mise en tampon par blocs des qu elle n est
    # pas un terminal, et un journal de CI n en est jamais un. Mesure faite avec un appimagetool de
    # comptoir : l annonce arrivait sous le listing de l artefact qu elle annonce (#5245).
    print(f"AppImage produite : {sortie}", flush=True)
    subprocess.run(["ls", "-lh", str(sortie)], check=False)
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2 or not sys.argv[1]:
        print(f"{sys.argv[0]}: version attendue (ex. 2.20.0)", file=sys.stderr)
        sys.exit(1)
    sys.exit(construire(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "x86_64"))
