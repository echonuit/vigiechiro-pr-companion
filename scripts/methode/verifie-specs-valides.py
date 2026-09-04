#!/usr/bin/env python3
"""Garde : les specs principales d OpenSpec valident, et le corpus n est pas vide (#4962).

Trois gardes tenaient OpenSpec avant celui-ci, et **tous portaient sur la coherence de l outillage
avec lui-meme** : les invocations citees existent, la version epinglee concorde, l adaptation
francaise n a pas ete regeneree. Aucun n executait `openspec validate`, mesure de l audit #4920.

Consequence : qu une spec principale reste bien formee reposait sur le fait que la competence
d archivage pense a valider avant de deplacer. C etait ecrit dans la competence, et rien ne le
verifiait.

## Le corpus vide, qui est le vrai piege

L outil sort en **0** sur un corpus vide, en ecrivant « No items found to validate. » Mesure le
2026-08-31 en vidant `openspec/specs/` dans un bac :

    $ openspec validate --specs
    - Validating...
    No items found to validate.
    $ echo $?
    0

Un garde qui appellerait l outil nu deviendrait donc **muet en ayant l air sain** le jour ou la
racine des specs bouge, ou ou la derniere capacite est retiree. C est l article A3 et l [ADR 2748],
« un dispositif qui peut ne rien verifier le dit ». Ce garde REFUSE sur un corpus vide, et le dit
autrement qu un echec de validation, parce que les deux se reparent differemment.

## Ce qu il ne fait pas

Il ne valide **que les specs principales**, par `--specs`. Un changement actif est legitimement
incomplet pendant qu on le redige, et le faire rougir transformerait un garde en gene. Les specs
principales, elles, sont fusionnees donc finies : c est le seul corpus dont on peut exiger qu il
valide toujours.

    --verifie   : ne rien ecrire, sortir 1 sur un ecart (garde de CI). C est aussi le defaut.
    --auto-test : eprouver le garde sur une copie jetable, et sortir 1 s il reste vert la ou il
                  devrait rougir, ou s il rougit sur un arbre sain.
"""

import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

BINAIRE_EPINGLE = pathlib.Path(".github") / "openspec" / "node_modules" / ".bin" / "openspec"

# Ce que l outil ecrit quand il n a rien trouve. Cherche sur les DEUX flux : il l ecrit sur la
# sortie standard, mais un changement de version pourrait le deplacer sur l erreur.
RIEN_A_VALIDER = re.compile(r"No items found to validate", re.IGNORECASE)

# La ligne de total, dont on tire le nombre reellement valide.
TOTAUX = re.compile(r"Totals:\s*(\d+)\s+passed,\s*(\d+)\s+failed")


def racine() -> pathlib.Path:
    rendu = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=False
    )
    return pathlib.Path(rendu.stdout.strip() or ".")


def valide(base: pathlib.Path) -> tuple[int, str]:
    """Lance l outil epingle sur `base`, et rend (code, sortie fusionnee)."""
    epingle = base / BINAIRE_EPINGLE
    if not epingle.exists():
        return 2, (
            f"REFUS : {BINAIRE_EPINGLE} est absent. Lancez « npm ci --prefix .github/openspec » : "
            "ce garde ne conclut pas sur un outil qu il n a pas lu."
        )
    rendu = subprocess.run(
        [str(epingle), "validate", "--specs"],
        capture_output=True,
        text=True,
        cwd=base,
        check=False,
    )
    return rendu.returncode, rendu.stdout + rendu.stderr


def juge(base: pathlib.Path) -> tuple[int, str]:
    """Rend (code, message). 0 = valide, 1 = ecart, 2 = refus de conclure."""
    code, sortie = valide(base)
    if code == 2 and sortie.startswith("REFUS"):
        return 2, sortie

    if RIEN_A_VALIDER.search(sortie):
        return 2, (
            "REFUS : l outil n a trouve AUCUNE spec principale a valider, et il sort en 0 pour le "
            "dire. Un corpus vide n est pas un corpus valide. Verifiez que « openspec/specs/ » "
            "existe et porte au moins une capacite."
        )

    totaux = TOTAUX.search(sortie)
    if totaux is None:
        return 2, (
            "REFUS : la sortie de l outil ne porte aucune ligne « Totals: N passed, M failed ». "
            "Ce garde ne conclut pas sur une sortie qu il n a pas su lire.\n" + sortie.strip()
        )

    passes, echoues = int(totaux.group(1)), int(totaux.group(2))
    if passes == 0 and echoues == 0:
        return 2, "REFUS : « 0 passed, 0 failed ». Rien n a ete valide, ce qui n est pas un succes."
    if echoues or code != 0:
        return 1, f"REFUS : {echoues} spec(s) principale(s) ne valident pas.\n" + sortie.strip()
    return 0, f"Les {passes} spec(s) principale(s) valident."


def auto_test() -> int:
    echecs = 0
    base = racine()

    def joue(libelle: str, attendu: int, prepare) -> None:
        nonlocal echecs
        with tempfile.TemporaryDirectory() as bac:
            r = pathlib.Path(bac) / "arbre"
            shutil.copytree(
                base,
                r,
                symlinks=True,
                ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
            )
            prepare(r)
            obtenu = juge(r)[0]
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    joue("un arbre sain passe", 0, lambda r: None)

    # Le cas qui compte : sans lui, tous les verts de ce garde seraient creux, parce que l outil
    # rend 0 sur un corpus vide.
    def vider(r: pathlib.Path) -> None:
        shutil.rmtree(r / "openspec" / "specs", ignore_errors=True)
        (r / "openspec" / "specs").mkdir(parents=True, exist_ok=True)

    joue("un corpus VIDE fait REFUSER, pas conclure", 2, vider)

    def casser(r: pathlib.Path) -> None:
        spec = next((r / "openspec" / "specs").rglob("spec.md"))
        spec.write_text("# Cassee\n\n## Requirements\n", encoding="utf-8")

    joue("une spec sans Purpose ni exigence fait rougir", 1, casser)

    def desinstaller(r: pathlib.Path) -> None:
        shutil.rmtree(r / ".github" / "openspec" / "node_modules", ignore_errors=True)

    joue("l outil epingle absent fait REFUSER", 2, desinstaller)

    print()
    print(
        "Auto-test concluant : le garde voit un corpus vide et une spec cassee."
        if not echecs
        else "Auto-test EN ÉCHEC."
    )
    return echecs


CONTRAT = {
    "geste": "specification principale d OpenSpec qui ne valide pas",
    "population": "les specs de .github/openspec, par la ligne de commande epinglee",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-specs-valides.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())
    code, message = juge(racine())
    print(message)
    sys.exit(0 if code == 0 else 1)
