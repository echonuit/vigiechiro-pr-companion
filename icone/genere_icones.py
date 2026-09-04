#!/usr/bin/env python3
"""Derive toutes les icones distribuees depuis la SEULE source vigiechiro.svg (#2144, porte du bash).

Les fichiers produits sont versionnes : la CI n a donc besoin ni d Inkscape ni de cairosvg, et une
release ne depend pas de l outillage graphique du poste. En contrepartie, ce script doit etre relance
- et son resultat commite - a chaque retouche du SVG.

Deux sorties, deux usages :

    icone/derive/                 ce que jpackage empaquette (un format par systeme)
    src/main/resources/icones/    ce que l application charge a l execution (Stage.getIcons())

## Le controle vacant que le portage RETIRE

La version bash verifiait que `python3` etait installe. Ce script EST python3 : le controle ne peut
plus rougir, et un garde qui ne peut pas rougir se retire. Les deux qui restent - `icotool` et le
module `cairosvg` - peuvent manquer, et disent lequel.

Prerequis : le module `cairosvg`, et `icotool` (paquet icoutils).
Usage : python3 icone/genere_icones.py
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[1]
SVG = RACINE / "icone" / "vigiechiro.svg"
DERIVE = RACINE / "icone" / "derive"
RUNTIME = RACINE / "src" / "main" / "resources" / "icones"

# 1024 sert au type Retina de macOS, 16 a la barre de titre : c est cette derniere taille qui a dicte
# le dessin, une version plus fine s y reduisant a une tache.
TAILLES = (16, 32, 48, 64, 128, 256, 512, 1024)

# Ce que l execution embarque. JavaFX choisit parmi les tailles fournies celle qui convient au
# contexte ; on s arrete a 256, au-dela c est du poids embarque pour rien dans le jar.
EMBARQUEES = (16, 32, 48, 128, 256)

# Le format annonce, et ce que `file` doit y reconnaitre. Un format mal ecrit ne se voit qu a
# l usage, souvent trop tard.
FORMATS = (
    ("vigiechiro.ico", "MS Windows icon", "vigiechiro.ico n'est pas un ICO"),
    (
        "vigiechiro.icns",
        "Mac OS X icon",
        "vigiechiro.icns n'est pas un ICNS (magick écrit un PNG déguisé)",
    ),
    ("vigiechiro.png", "PNG image", "vigiechiro.png n'est pas un PNG"),
)


def prerequis() -> int:
    """Ce qui peut manquer, et qui le dit. 0 si tout est la."""
    if shutil.which("icotool") is None:
        print("::error::icotool est requis", file=sys.stderr)
        return 1
    try:
        import cairosvg  # noqa: F401
    except ImportError:
        print("::error::le module python cairosvg est requis", file=sys.stderr)
        return 1
    return 0


def engendrer() -> int:
    """Les six etapes, dans l ordre, et le code de sortie qui va avec."""
    manque = prerequis()
    if manque:
        return manque
    import cairosvg

    shutil.rmtree(DERIVE, ignore_errors=True)
    shutil.rmtree(RUNTIME, ignore_errors=True)
    DERIVE.mkdir(parents=True)
    RUNTIME.mkdir(parents=True)

    # 1. Les rendus PNG.
    for t in TAILLES:
        cairosvg.svg2png(
            url=str(SVG),
            write_to=str(DERIVE / f"vigiechiro-{t}.png"),
            output_width=t,
            output_height=t,
        )
    print("PNG rendus :", ", ".join(str(t) for t in TAILLES))

    # 2. Linux : jpackage attend un PNG unique.
    shutil.copy(DERIVE / "vigiechiro-512.png", DERIVE / "vigiechiro.png")

    # 3. Windows : un ICO multi-tailles. Windows choisit la plus proche du contexte d affichage,
    #    d ou l interet d y mettre les petites - c est ce qui evite un 256 ecrase en 16.
    subprocess.run(
        ["icotool", "-c", "-o", str(DERIVE / "vigiechiro.ico")]
        + [str(DERIVE / f"vigiechiro-{t}.png") for t in (16, 32, 48, 256)],
        check=True,
    )

    # 4. macOS : un ICNS assemble par nos soins, `magick …icns` ecrivant en realite un PNG deguise
    #    que jpackage refuse (cf. l en-tete de ecrit-icns.py).
    subprocess.run(
        ["python3", str(RACINE / "icone" / "ecrit-icns.py"), str(DERIVE / "vigiechiro.icns")]
        + [f"{t}:{DERIVE / f'vigiechiro-{t}.png'}" for t in (16, 32, 128, 256, 512, 1024)],
        check=True,
    )

    # 5. L execution.
    for t in EMBARQUEES:
        shutil.copy(DERIVE / f"vigiechiro-{t}.png", RUNTIME / f"vigiechiro-{t}.png")

    # 6. Controle des formats produits.
    print()
    print("=== contrôle des formats produits ===")
    for nom, attendu, refus in FORMATS:
        rendu = subprocess.run(
            ["file", str(DERIVE / nom)], capture_output=True, text=True, check=False
        )
        if attendu not in rendu.stdout:
            print(f"::error::{refus}", file=sys.stderr)
            return 1
    subprocess.run(["file"] + [str(DERIVE / nom) for nom, _, _ in FORMATS], check=False)
    print()
    print("Icônes dérivées. Penser à committer icone/derive/ et src/main/resources/icones/.")
    return 0


if __name__ == "__main__":
    sys.exit(engendrer())
