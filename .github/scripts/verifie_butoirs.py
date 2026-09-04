#!/usr/bin/env python3
"""Tout job de workflow porte un `timeout-minutes` (#5219, porte du bash).

Pourquoi ce garde existe. Un job sans butoir que GitHub laisse courir SIX HEURES a bloque une PR
pendant 4 h 15 sur un `apt-get install` de police, en `-qq` - donc sans une ligne de log pour dire ce
qui trainait. Le butoir de 12 minutes pose sur `banc-filme` (#3883) avait ete apprecie pour ce seul
job ; vingt-six autres n en avaient aucun, dont ceux qui publient.

Un job qui echoue apprend quelque chose. Un job qui pend n apprend rien ET retient tout le monde.

## Ce qu il ne dit PAS

Que le butoir soit bien choisi. Un butoir trop large ne protege de rien, un butoir trop serre rend le
rouge illisible. Les valeurs viennent d une mesure - environ quatre fois le maximum observe sur les
quarante derniers runs reussis - et se revisent en mesurant de nouveau, pas en discutant.

## Ce que le portage a rendu

La version bash imprimait son contrat depuis un **heredoc recopie**, et son commentaire l assumait :
« la forme est celle de `_commun.imprime_contrat`, qu un script shell ne peut pas importer. C est la
duplication que l ADR 3661 assume. » Elle n a plus lieu d etre : ce fichier appelle l aide commune,
et la forme du contrat vient d un seul endroit.

Le bash portait aussi un garde-fou que le Python rend gratuit : le delimiteur du heredoc ne pouvait
pas s appeler `CONTRAT`, sans quoi shellcheck s y perdait (SC1121), bloquant en CI.
"""

from __future__ import annotations

import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

# Injectable pour l auto-test : sans cela ses cas exigeraient le depot reel, et un garde dont les
# cas ne tournent pas sur un arbre a eux ne peut pas eprouver le cas « aucun workflow ».
DOSSIER = "BUTOIRS_RACINE"


def workflows(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les ateliers que ce garde LIT, extraits pour que le compte des jobs soit une valeur."""
    import os

    base = racine or pathlib.Path(os.environ.get(DOSSIER, RACINE))
    dossier = base / ".github" / "workflows"
    return sorted(dossier.glob("*.yml")) if dossier.is_dir() else []


def nus(racine: pathlib.Path | None = None) -> tuple[list[str], int]:
    """Les jobs sans butoir, et le nombre de jobs lus.

    Rendre les DEUX est ce qui distingue « aucun job n est nu » de « aucun job n a ete lu » : le
    second est une panne, et un garde qui les confond rend un vert qui n a rien juge (ADR 5015).
    """
    import yaml

    trouves, total = [], 0
    for chemin in workflows(racine):
        contenu = yaml.safe_load(chemin.read_text(encoding="utf-8"))
        for nom, job in ((contenu or {}).get("jobs") or {}).items():
            if not isinstance(job, dict):
                continue
            # Un job qui DELEGUE a un workflow reutilisable n a pas de butoir a lui : c est le
            # workflow appele qui le porte.
            if "uses" in job:
                continue
            total += 1
            if "timeout-minutes" not in job:
                trouves.append(f"{chemin.name} / {nom}")
    return trouves, total


def juge(racine: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("✗ PyYAML absent : la garde ne peut pas lire les workflows.")
        return 1
    if not workflows(racine):
        print("✗ aucun workflow trouvé : la garde ne peut rien affirmer.")
        return 1
    trouves, total = nus(racine)
    if trouves:
        print(f"✗ {len(trouves)} job(s) sans `timeout-minutes` :")
        for n in trouves:
            print(f"   · {n}")
        print()
        print("  Sans butoir, GitHub laisse courir SIX HEURES. Un job qui échoue apprend quelque")
        print("  chose ; un job qui pend n'apprend rien et retient tout le monde.")
        return 1
    print(f"✓ Les {total} job(s) portent un butoir.")
    return 0


BON = """name: bon
on: [push]
jobs:
  un-job:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - run: echo ok
"""
NU = """name: nu
on: [push]
jobs:
  sans-butoir:
    runs-on: ubuntu-latest
    steps:
      - run: echo ok
"""
DELEGUE = """name: delegue
on: [push]
jobs:
  appelant:
    uses: ./.github/workflows/bon.yml
"""


def _auto_test() -> int:
    """Les cinq cas de la version bash, et le compte qui distingue le vide de la panne."""
    import tempfile

    echecs = cas = rouges = 0

    def verifie(attendu, libelle, racine):
        nonlocal echecs, cas, rouges
        cas += 1
        if attendu != 0:
            rouges += 1
        code = juge(racine)
        if code == attendu:
            print(f"  [OK   ] {libelle:<52} -> {code}")
        else:
            print(f"  [ÉCHEC] {libelle:<52} -> {code} (attendu {attendu})")
            echecs += 1

    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-5219-") as tmp:
        bac = pathlib.Path(tmp)
        wf = bac / ".github" / "workflows"
        wf.mkdir(parents=True)

        (wf / "bon.yml").write_text(BON, encoding="utf-8")
        verifie(0, "un job avec butoir est accepté", bac)

        (wf / "nu.yml").write_text(NU, encoding="utf-8")
        verifie(1, "un job SANS butoir est refusé", bac)

        (wf / "nu.yml").unlink()
        verifie(0, "le dépôt redevient conforme quand on le retire", bac)

        (wf / "delegue.yml").write_text(DELEGUE, encoding="utf-8")
        verifie(0, "un job qui DÉLÈGUE n est pas exigé d en porter un", bac)

        for f in wf.glob("*.yml"):
            f.unlink()
        verifie(1, "sans aucun workflow, la garde REFUSE au lieu de passer", bac)

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de cette garde.")
    return 1


# Ce que ce garde DECLARE etre (issue #5009). La forme vient de `_commun.imprime_contrat`, et non
# plus d un heredoc recopie : c est ce que le portage a rendu.
CONTRAT = {
    "geste": "job de workflow sans butoir de durée",
    "population": "WORKFLOWS",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": ".github/scripts/verifie_butoirs.py --auto-test",
    "decision": "hygiène, sans décision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge())
