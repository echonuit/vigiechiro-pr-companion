#!/usr/bin/env python3
"""Un script que la CI peut lancer sous Windows ecrit ses sorties en UTF-8 (#5197).

## Pourquoi ce garde existe

La console de Windows encode en cp1252, et Python ecrit dans l encodage de la console. Un « ✓ » ou
un « ⚠️ » y leve `UnicodeEncodeError`, le script meurt en plein compte rendu, et le job rougit sur
l ecriture en accusant ce que le script mesurait. Le tournage l a vecu (#5195), puis le tri des
echecs de plateforme, plusieurs fois : treize tests verts et un rouge le 13 octobre 2026 (#6200).

Aucun auto-test ne le voyait. Ils tournent sous Linux, et la plupart lisent la sortie dans un
tampon qui accepte tout caractere. Le defaut ne se decouvrait qu en lancant le job Windows.

## Ce qu il exige

Chaque script de `.github/scripts` qu une etape lance, dans un job qui PEUT tourner sous Windows,
appelle `ecrire_en_utf8()` de `_console` a son chargement : un appel de premier niveau, et non dans
une fonction qu un autre chemin d entree sauterait.

Un job peut tourner sous Windows quand son `runs-on` nomme Windows, ou quand c est une expression :
une matrice, ou un choix fait a l execution (`needs.choisir.outputs.runner`). Le garde ne suit pas
l expression jusqu a sa valeur, et ne lit pas non plus le `if` des etapes. Une condition change sans
que le script le sache, et l appel ne coute rien sous Linux ou macOS, dont la console est en UTF-8.

## Ce qu il ne dit PAS

Que le script s ecrive bien sous Windows : il verifie que l aide est appelee, pas ce qu elle rend.
Le cas qui le joue sous une console cp1252 est dans l auto-test de `trie_les_echecs_de_plateforme.py`.
Il ne voit pas non plus un script lance hors de `.github/scripts`, ni du Python ecrit dans l etape :
le 14 octobre 2026, aucun job capable de Windows n en portait.
"""

from __future__ import annotations

import ast
import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test, sort_si_contrat_demande

# Injectable pour l auto-test, comme `verifie_butoirs.py` : ses cas jouent sur un arbre a eux.
DOSSIER = "SORTIES_WINDOWS_RACINE"
SCRIPT_LANCE = re.compile(r"\.github/scripts/([\w-]+\.py)")
AIDE = "ecrire_en_utf8"


def _racine(racine: pathlib.Path | None) -> pathlib.Path:
    return racine or pathlib.Path(os.environ.get(DOSSIER, RACINE))


def peut_tourner_sous_windows(job: dict) -> bool:
    """Le libelle nomme Windows, ou il se decide a l execution."""
    libelle = str(job.get("runs-on", ""))
    return "windows" in libelle.lower() or "${{" in libelle


def lances_sous_windows(racine: pathlib.Path | None = None) -> tuple[dict[str, set[str]], int]:
    """Les scripts lances par un job capable de Windows, avec les jobs qui les lancent.

    Rendre aussi le nombre de jobs lus distingue « aucun script exposé » de « aucun job lu ».
    """
    import yaml

    scripts: dict[str, set[str]] = {}
    jobs = 0
    for atelier in sorted((_racine(racine) / ".github" / "workflows").glob("*.yml")):
        contenu = yaml.safe_load(atelier.read_text(encoding="utf-8")) or {}
        for nom, job in (contenu.get("jobs") or {}).items():
            if not isinstance(job, dict) or "uses" in job:
                continue
            jobs += 1
            if not peut_tourner_sous_windows(job):
                continue
            for etape in job.get("steps") or []:
                for script in SCRIPT_LANCE.findall(str(etape.get("run") or "")):
                    scripts.setdefault(script, set()).add(f"{atelier.name} / {nom}")
    return scripts, jobs


def appelle_l_aide(source: str) -> bool:
    """L aide est importee de `_console` ET appelee au premier niveau du module."""
    arbre = ast.parse(source)
    importee = appelee = False
    for noeud in arbre.body:
        if isinstance(noeud, ast.ImportFrom) and noeud.module == "_console":
            importee |= any(alias.name == AIDE for alias in noeud.names)
        if (
            isinstance(noeud, ast.Expr)
            and isinstance(noeud.value, ast.Call)
            and isinstance(noeud.value.func, ast.Name)
            and noeud.value.func.id == AIDE
        ):
            appelee = True
    return importee and appelee


def juge(racine: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("✗ PyYAML absent : le garde ne peut pas lire les workflows.")
        return 1
    scripts, jobs = lances_sous_windows(racine)
    if jobs == 0:
        print("✗ aucun job lu : le garde ne peut rien affirmer.")
        return 1
    dossier = _racine(racine) / ".github" / "scripts"
    fautes = []
    for script, lanceurs in sorted(scripts.items()):
        chemin = dossier / script
        if not chemin.is_file():
            fautes.append(f"{script} : introuvable, lancé par {', '.join(sorted(lanceurs))}")
        elif not appelle_l_aide(chemin.read_text(encoding="utf-8")):
            fautes.append(
                f"{script} : n'appelle pas `{AIDE}()`, lancé par {', '.join(sorted(lanceurs))}"
            )
    if fautes:
        print(f"✗ {len(fautes)} script(s) peuvent mourir sur la console de Windows :")
        for faute in fautes:
            print(f"   · {faute}")
        print()
        print("  Sous Windows, la sortie de Python est en cp1252, et un « ✓ » y lève")
        print("  UnicodeEncodeError : le job rougit sur l'écriture, pas sur ce qu'il mesure.")
        print(f"  Ajouter, après les imports : `from _console import {AIDE}` puis `{AIDE}()`.")
        return 1
    print(f"✓ Les {len(scripts)} script(s) qu'un job peut lancer sous Windows écrivent en UTF-8.")
    return 0


AVEC_L_AIDE = "import sys\n\nfrom _console import ecrire_en_utf8\n\necrire_en_utf8()\nprint('✓')\n"
SANS_L_AIDE = "import sys\n\nprint('✓')\n"
IMPORTEE_SEULEMENT = "from _console import ecrire_en_utf8\n\nprint('✓')\n"
DANS_UNE_FONCTION = (
    "from _console import ecrire_en_utf8\n\n\ndef main():\n    ecrire_en_utf8()\n\n\nmain()\n"
)


def _atelier(runs_on: str) -> str:
    return (
        "name: essai\non: [push]\njobs:\n  essai:\n"
        f"    runs-on: {runs_on}\n    timeout-minutes: 5\n    steps:\n"
        "      - run: python3 .github/scripts/essai.py\n"
    )


def _auto_test() -> int:
    """Les cas, dont ceux qui doivent rougir, et le compte qui distingue le vide de la panne."""
    import tempfile

    asserte, echecs = cas_d_auto_test()
    cas = rouges = 0

    def verifie(attendu, libelle, racine):
        nonlocal cas, rouges
        cas += 1
        if attendu != 0:
            rouges += 1
        asserte(f"{libelle} -> attendu {attendu}", lambda: juge(racine), attendu)

    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-5197-") as tmp:
        bac = pathlib.Path(tmp)
        ateliers = bac / ".github" / "workflows"
        scripts = bac / ".github" / "scripts"
        ateliers.mkdir(parents=True)
        scripts.mkdir(parents=True)
        atelier = ateliers / "essai.yml"
        script = scripts / "essai.py"

        verifie(1, "sans aucun job, le garde REFUSE au lieu de passer", bac)

        atelier.write_text(_atelier("windows-latest"), encoding="utf-8")
        script.write_text(AVEC_L_AIDE, encoding="utf-8")
        verifie(0, "un script lancé sous Windows qui appelle l'aide est accepté", bac)

        script.write_text(SANS_L_AIDE, encoding="utf-8")
        verifie(1, "le même script SANS l'aide est refusé", bac)

        script.write_text(IMPORTEE_SEULEMENT, encoding="utf-8")
        verifie(1, "l'aide importée sans être appelée est refusée", bac)

        script.write_text(DANS_UNE_FONCTION, encoding="utf-8")
        verifie(1, "l'aide appelée dans une fonction est refusée", bac)

        script.write_text(SANS_L_AIDE, encoding="utf-8")
        atelier.write_text(_atelier("ubuntu-latest"), encoding="utf-8")
        verifie(0, "un script que seul Linux lance n'est pas exigé", bac)

        atelier.write_text(_atelier("${{ matrix.os }}"), encoding="utf-8")
        verifie(1, "un runs-on choisi à l'exécution compte comme Windows", bac)

        script.unlink()
        atelier.write_text(_atelier("windows-latest"), encoding="utf-8")
        verifie(1, "un script lancé mais introuvable est refusé", bac)

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs() == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs()}) : ne pas se fier au verdict de ce garde.")
    return 1


# Ce que ce garde DECLARE etre (issue #5009), dans la forme de `_commun.imprime_contrat`.
CONTRAT = {
    "geste": "script lancé sous Windows sans sortie en UTF-8",
    "population": "WORKFLOWS",
    # Il lit les ateliers, puis les scripts qu ils lancent : un script modifie peut perdre l appel.
    "chemins": """
.github/workflows/**
.github/scripts/*.py
""",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": ".github/scripts/verifie_sorties_sous_windows.py --auto-test",
    "decision": "hygiène, sans décision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge())
