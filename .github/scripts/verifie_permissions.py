#!/usr/bin/env python3
"""Un plancher de permissions en ecriture n est pas accorde a tous les jobs (#5219, porte du bash).

Un bloc `permissions:` pose au niveau du workflow s applique a **tous** ses jobs, y compris ceux qui
compilent et empaquettent. Dans un workflow d un seul job, cela ne dit rien de plus que de le poser
sur le job ; dans un workflow qui en porte plusieurs, cela elargit des droits a des etapes qui n en
ont pas besoin.

## Ce qu il ne dit PAS

Que le droit soit legitime pour le job qui en a besoin. Il dit seulement qu il ne doit pas etre
accorde au plancher quand plusieurs jobs le partagent. La regle reste **etroite**, et trois de ses
cinq cas d auto-test sont des controles NEGATIFS qui la tiennent etroite : un mono-job garde son
plancher, un workflow sans plancher ne declenche pas, et plusieurs droits en LECTURE non plus.

## PyYAML est requis, et son absence REFUSE

Un YAML de workflow ne se lit pas a la ligne sans se tromper : blocs, ancres, et un `on:` que YAML
interprete en booleen. Si PyYAML manque, ce garde echoue bruyamment - un garde qui se saute quand son
outillage manque est un faux vert de plus.
"""

from __future__ import annotations

import os
import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]

# Injectable pour l auto-test : sans cela ses cas exigeraient le depot reel, et les cinq fixtures
# n auraient nulle part ou vivre.
RACINE_INJECTEE = "PERMISSIONS_RACINE"


def workflows(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les ateliers que ce garde LIT."""
    base = racine or pathlib.Path(os.environ.get(RACINE_INJECTEE, RACINE))
    dossier = base / ".github" / "workflows"
    return sorted(dossier.glob("*.yml")) if dossier.is_dir() else []


def problemes(racine: pathlib.Path | None = None) -> tuple[list[str], int]:
    """Les planchers trop larges, et le nombre d ateliers PORTANT DES JOBS qui ont ete lus.

    Le second compte n est pas le nombre de fichiers : un atelier sans job ne se verifie pas, et le
    confondre ferait dire au verdict qu il a juge ce qu il a seulement ouvert.
    """
    import yaml

    trouves, verifies = [], 0
    for chemin in workflows(racine):
        document = yaml.safe_load(chemin.read_text(encoding="utf-8")) or {}
        jobs = document.get("jobs") or {}
        if not jobs:
            continue
        verifies += 1
        plancher = document.get("permissions") or {}
        if isinstance(plancher, str):
            plancher = {"*": plancher}
        ecritures = sorted(cle for cle, valeur in plancher.items() if valeur == "write")
        if ecritures and len(jobs) > 1:
            trouves.append(
                f"{chemin.name} : plancher en écriture ({', '.join(ecritures)}) dans un workflow "
                f"de {len(jobs)} jobs : ces droits sont accordés à TOUS, y compris à "
                f"{', '.join(sorted(jobs))}"
            )
    return trouves, verifies


def juge(racine: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("❌ PyYAML est absent : la garde des permissions ne peut pas lire les workflows.")
        print("   Installer avec « pip install --group gardes », qui lit pyproject.toml.")
        return 1
    trouves, verifies = problemes(racine)
    if trouves:
        print("❌ Plancher de permissions trop large :")
        for p in trouves:
            print(f"   {p}")
        print()
        print(
            "Retirer l'écriture du bloc « permissions: » du workflow, et la déclarer dans le seul"
        )
        print("job qui en a besoin. Un droit accordé au plancher l'est à tous les jobs, y compris")
        print("ceux qui compilent et empaquettent.")
        return 1
    print(
        f"Garde permissions : OK ({verifies} workflow(s), aucun plancher en écriture hors mono-job)."
    )
    return 0


CAS = (
    (
        0,
        "un plancher en lecture, l écriture déclarée au job, passe",
        "propre.yml",
        (
            "permissions:\n  contents: read\njobs:\n  a:\n    steps: []\n  b:\n"
            "    permissions:\n      contents: write\n    steps: []\n"
        ),
    ),
    (
        1,
        "un plancher en écriture dans un workflow de deux jobs est refusé",
        "fautif.yml",
        (
            "permissions:\n  contents: write\n  issues: write\njobs:\n  a:\n"
            "    steps: []\n  b:\n    steps: []\n"
        ),
    ),
    # Controles NEGATIFS : la regle doit rester etroite.
    (
        0,
        "un workflow MONO-JOB garde le droit d écrire son plancher",
        "mono.yml",
        "permissions:\n  contents: write\n  pull-requests: write\njobs:\n  seul:\n    steps: []\n",
    ),
    (
        0,
        "un workflow sans plancher déclaré ne déclenche pas",
        "sans.yml",
        "jobs:\n  a:\n    steps: []\n  b:\n    steps: []\n",
    ),
    (
        0,
        "plusieurs droits en LECTURE ne déclenchent pas",
        "lecture.yml",
        (
            "permissions:\n  contents: read\n  actions: read\njobs:\n  a:\n"
            "    steps: []\n  b:\n    steps: []\n"
        ),
    ),
)


def _auto_test() -> int:
    """Les cinq cas de la version bash, dont TROIS controles negatifs."""
    import tempfile

    echecs = cas = rouges = 0
    for attendu, libelle, nom, contenu in CAS:
        cas += 1
        if attendu != 0:
            rouges += 1
        with tempfile.TemporaryDirectory(prefix="vc-perm-") as tmp:
            # Un bac NEUF par cas : la version bash remontait le sien avec `monter`, et deux
            # fixtures qui se superposeraient feraient juger un corpus que le cas ne decrit pas.
            bac = pathlib.Path(tmp)
            wf = bac / ".github" / "workflows"
            wf.mkdir(parents=True)
            (wf / nom).write_text(contenu, encoding="utf-8")
            code = juge(bac)
        if code == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
            echecs = 1

    print()
    verbe = "DOIT" if rouges == 1 else "DOIVENT"
    print(f"{cas} cas, dont {rouges} qui {verbe} rougir.")
    if echecs == 0:
        print("Auto-test de la garde permissions : OK")
    else:
        print(
            "Auto-test de la garde permissions : ÉCHEC - les règles ne font plus ce qu'elles "
            "promettent."
        )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge())
