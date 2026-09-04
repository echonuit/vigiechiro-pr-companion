#!/usr/bin/env python3
"""Une condition de job appuyee sur un `needs` nomme l etat qu elle attend (#5219, porte du bash).

GitHub saute un job des qu un ancetre a ete saute, **SAUF** si son `if:` porte une fonction d etat.
Sans elle, la condition est enveloppee en `success() && (...)` sur tout le graphe amont : la porte
qu on croit tenir n est jamais evaluee, et le run reste VERT.

La forme juste ressemble a :

    if: ${{ !cancelled() && needs.X.result == 'success' && <la condition voulue> }}

## Ce qu il ne juge PAS

Ce que la fonction d etat y fait. Sa seule presence suffit a reprendre la main sur la propagation du
« saute » ; juger la logique demanderait d evaluer une expression GitHub, ce qu aucun garde local ne
peut faire honnetement.

## Les deux controles negatifs, et pourquoi ils comptent

**Un job sans ancetre sautable** garde le droit a une condition nue : il n y a rien a propager.
**Un job sans `if:`** est saute avec son ancetre, et c est le comportement attendu. Sans ces deux
cas, le garde exigerait une fonction d etat partout et se ferait contourner plutot que corriger.
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
FLUX = "FLUX"

# Les quatre fonctions d etat de GitHub Actions.
ETAT = re.compile(r"\b(always|cancelled|success|failure)\s*\(\s*\)")


def ateliers(flux: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les fichiers d atelier que ce garde LIT."""
    base = flux or pathlib.Path(os.environ.get(FLUX, RACINE / ".github" / "workflows"))
    if not base.is_dir():
        return []
    return sorted(list(base.glob("*.yml")) + list(base.glob("*.yaml")))


def _besoins(jobs: dict, nom: str) -> list[str]:
    """Les ancetres DIRECTS d un job, que `needs:` soit une chaine ou une liste."""
    spec = jobs.get(nom) or {}
    n = (spec.get("needs") or []) if isinstance(spec, dict) else []
    return [n] if isinstance(n, str) else list(n)


def _ancetres(jobs: dict, nom: str, vus: set[str] | None = None) -> set[str]:
    """Tous les ancetres, transitivement : la propagation du « saute » traverse le graphe entier."""
    vus = vus if vus is not None else set()
    for parent in _besoins(jobs, nom):
        if parent not in vus:
            vus.add(parent)
            _ancetres(jobs, parent, vus)
    return vus


def exposes(flux: pathlib.Path | None = None) -> tuple[list[str], int]:
    """Les conditions exposees, et le nombre de conditions APPUYEES SUR UN NEEDS qui ont ete lues."""
    import yaml

    trouves, total = [], 0
    for chemin in ateliers(flux):
        doc = yaml.safe_load(chemin.read_text(encoding="utf-8"))
        if not isinstance(doc, dict):
            continue
        jobs = doc.get("jobs") or {}
        if not isinstance(jobs, dict):
            continue
        for nom, spec in jobs.items():
            if not isinstance(spec, dict):
                continue
            condition = spec.get("if")
            # Un job sans `if:` est hors sujet : etre saute avec son ancetre est ce qu on attend.
            if condition is None or not _besoins(jobs, nom):
                continue
            total += 1
            if ETAT.search(str(condition)):
                continue
            # Un ancetre porteur d un `if:` est un ancetre qui PEUT etre saute.
            sautables = sorted(
                a
                for a in _ancetres(jobs, nom)
                if isinstance(jobs.get(a), dict) and jobs[a].get("if") is not None
            )
            if sautables:
                cites = ", ".join("« " + a + " »" for a in sautables)
                trouves.append(
                    f"{chemin.name} · job « {nom} » : sa condition ne porte aucune fonction d'état, "
                    f"et il descend de {cites}"
                )
    return trouves, total


def juge(flux: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("✗ PyYAML est absent : la garde ne peut rien analyser.")
        return 1
    trouves, total = exposes(flux)
    if trouves:
        print(f"✗ {len(trouves)} condition(s) de job exposée(s) à la propagation du « sauté » :")
        for e in trouves:
            print(f"   · {e}")
        print()
        print("  GitHub saute un job dès qu'un ancêtre a été sauté, SAUF si son `if:` porte une")
        print("  fonction d'état. Sans elle, la condition est enveloppée en `success() && (...)`")
        print("  sur tout le graphe amont : la porte qu'on croit tenir n'est jamais évaluée, et le")
        print("  run reste VERT. Écrire par exemple :")
        print(
            "     if: ${{ !cancelled() && needs.X.result == 'success' && <la condition voulue> }}"
        )
        return 1
    print(
        f"✓ Les {total} condition(s) de job appuyées sur un `needs` nomment l'état qu'elles attendent."
    )
    return 0


NUE_SOUS_SAUTABLE = """on: [push]
jobs:
  garde:
    if: github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  milieu:
    needs: [garde]
    if: ${{ !cancelled() }}
    runs-on: ubuntu-latest
    outputs:
      tag: ${{ steps.t.outputs.v }}
    steps:
      - id: t
        run: echo "v=x" >> "$GITHUB_OUTPUT"
  aval:
    needs: [milieu]
    if: needs.milieu.outputs.tag != ''
    runs-on: ubuntu-latest
    steps:
      - run: echo
"""
TRANSITIVE = """on: [push]
jobs:
  garde:
    if: github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  a:
    needs: [garde]
    if: ${{ always() }}
    runs-on: ubuntu-latest
    steps:
      - run: echo
  b:
    needs: [a]
    if: ${{ always() }}
    runs-on: ubuntu-latest
    steps:
      - run: echo
  c:
    needs: [b]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - run: echo
"""
FORME_JUSTE = """on: [push]
jobs:
  garde:
    if: github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  aval:
    needs: [garde]
    if: ${{ !cancelled() && needs.garde.result == 'success' }}
    runs-on: ubuntu-latest
    steps:
      - run: echo
"""
SANS_ANCETRE_SAUTABLE = """on: [push]
jobs:
  amont:
    runs-on: ubuntu-latest
    steps:
      - run: echo
  aval:
    needs: [amont]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - run: echo
"""
SANS_CONDITION = """on: [push]
jobs:
  garde:
    if: github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  aval:
    needs: [garde]
    runs-on: ubuntu-latest
    steps:
      - run: echo
"""

CAS = (
    ("rouge", "une condition nue sous un ancetre sautable est refusee", NUE_SOUS_SAUTABLE),
    ("rouge", "la propagation transitive est vue a deux crans", TRANSITIVE),
    ("vert", "la forme juste passe", FORME_JUSTE),
    # Les DEUX controles negatifs : sans eux, la garde exigerait une fonction d etat partout.
    ("vert", "sans ancetre sautable, une condition nue est permise", SANS_ANCETRE_SAUTABLE),
    ("vert", "un job sans condition n est pas concerne", SANS_CONDITION),
)


def _auto_test() -> int:
    """Les cinq cas de la version bash, dont DEUX qui doivent rougir."""
    import tempfile

    echecs = total = 0
    print("AUTO-TEST")
    for attendu, libelle, contenu in CAS:
        total += 1
        with tempfile.TemporaryDirectory(prefix="vc-cond-") as tmp:
            flux = pathlib.Path(tmp) / ".github" / "workflows"
            flux.mkdir(parents=True)
            (flux / "essai.yml").write_text(contenu, encoding="utf-8")
            code = juge(flux)
        obtenu = "vert" if code == 0 else "rouge"
        if obtenu == attendu:
            print(f"  [OK   ] {libelle:<60} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {libelle:<60} -> {obtenu} (attendu {attendu})")
            echecs += 1

    print()
    print(f"{total} cas, dont 2 qui DOIVENT rougir.")
    if echecs:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce garde.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge())
