#!/usr/bin/env python3
"""Un renvoi `workflow_run` vise un libelle qui existe (#5219, porte du bash).

GitHub apparie par egalite **STRICTE**. Un `workflows: ["Apercus des vues"]` qui ne correspond a
aucun `name:` n arme rien - et ne rougit pas : le workflow consommateur ne se declenche simplement
jamais. C est le vecu de #3279, ou un libelle renomme chez le producteur a rendu muet son
consommateur sans qu aucune couleur ne change.

## Ce que la comparaison ne pardonne pas

Ni la casse, ni l accent, ni un espace en trop. Trois des huit cas d auto-test tiennent chacune de
ces trois formes, parce qu elles se ressemblent a l oeil et pas a l appariement.

## La non-vacuite, et pourquoi elle refuse

Si plus aucun renvoi n est trouve, ce n est pas « tout va bien » : c est que le garde ne regarde plus
rien. Le depot en porte au moins un. Un balayage qui ne trouve rien a examiner et se declare conforme
est un faux vert, et ce garde refuse plutot que de le rendre.

## Le message compte autant que le code

Ses cas exigent le MESSAGE, pas seulement le code de sortie. Un `1` peut venir d un PyYAML absent,
d une erreur de syntaxe ou d un bac mal monte : ces rouges-la ne prouvent rien de la regle. Vecu sur
#3335, ou une compilation cassee a servi de fausse preuve.
"""

from __future__ import annotations

import os
import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
RACINE_INJECTEE = "RENVOIS_RACINE"


def ateliers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les fichiers d atelier que ce garde LIT."""
    base = racine or pathlib.Path(os.environ.get(RACINE_INJECTEE, RACINE))
    dossier = base / ".github" / "workflows"
    return sorted(dossier.glob("*.yml")) if dossier.is_dir() else []


def releve(racine: pathlib.Path | None = None) -> tuple[dict[str, str], list[tuple[str, str]]]:
    """Les libelles portes, et les renvois declares.

    `on:` est lu par YAML comme le booleen True (norme YAML 1.1). Les deux ecritures sont acceptees
    plutot que d imposer `"on":` aux auteurs.
    """
    import yaml

    libelles: dict[str, str] = {}
    renvois: list[tuple[str, str]] = []
    for chemin in ateliers(racine):
        document = yaml.safe_load(chemin.read_text(encoding="utf-8")) or {}
        libelle = document.get("name")
        if isinstance(libelle, str):
            libelles[libelle] = chemin.name
        declencheurs = document.get("on", document.get(True)) or {}
        if not isinstance(declencheurs, dict):
            continue
        workflow_run = declencheurs.get("workflow_run") or {}
        if not isinstance(workflow_run, dict):
            continue
        for cite in workflow_run.get("workflows") or []:
            renvois.append((chemin.name, cite))
    return libelles, renvois


def juge(racine: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("❌ PyYAML est absent : la garde ne peut pas lire les workflows.")
        return 1
    libelles, renvois = releve(racine)
    if not renvois:
        # La phrase « n'a rien examiné » reste D UN SEUL TENANT : un cas d auto-test l exige dans
        # la sortie, et la couper sur deux lignes la rendrait introuvable. Le portage l a cassee, et
        # c est ce cas-la qui l a dit - un message est une INTERFACE quand un temoin le lit.
        print(
            "❌ Aucun renvoi « workflow_run » trouvé sous .github/workflows/ : "
            "la garde n'a rien examiné."
        )
        print("   Soit les renvois ont disparu, soit le motif de détection a cessé de correspondre")
        print("   - dans les deux cas, son vert ne mesurerait plus rien.")
        return 1
    problemes = []
    for citant, cite in renvois:
        if cite not in libelles:
            connus = ", ".join(f"« {l} »" for l in sorted(libelles)) or "(aucun)"
            problemes.append(
                f"{citant} attend « {cite} », qui n'est le name: d'aucun workflow. "
                f"Libellés existants : {connus}"
            )
    if problemes:
        print("❌ Renvoi(s) entre workflows visant un libellé inexistant :")
        for p in problemes:
            print(f"   {p}")
        print()
        print("   GitHub apparie par égalité STRICTE : un libellé introuvable n'arme rien et ne")
        print("   rougit pas. Corrigez le libellé cité, ou le « name: » du workflow attendu.")
        return 1
    print(f"Garde des renvois : OK ({len(renvois)} renvoi(s), tous vers un libellé existant).")
    return 0


PRODUCTEUR = """name: Aperçus des vues
on:
  push: {}
jobs:
  a:
    runs-on: ubuntu-latest
"""
CONSOMMATEUR = """name: docs
on:
  workflow_run:
    workflows: ["Aperçus des vues"]
    types: [completed]
jobs:
  b:
    runs-on: ubuntu-latest
"""


def _consommateur(libelle: str) -> str:
    return (
        "name: docs\non:\n  workflow_run:\n"
        f'    workflows: ["{libelle}"]\n'
        "jobs:\n  b:\n    runs-on: ubuntu-latest\n"
    )


# (attendu, libelle du cas, {fichier: contenu} ECRASANT le bac coherent, motif exige dans la sortie)
CAS = (
    # Controle NEGATIF d abord : sans lui, une garde qui refuse tout passerait tous les autres cas.
    (0, "un renvoi qui désigne un libellé existant passe", {}, ""),
    (
        1,
        "un libellé renommé chez le producteur est refusé (le vécu de #3279)",
        {"producteur.yml": PRODUCTEUR.replace("Aperçus des vues", "Aperçus des vues (main)")},
        "visant un libellé inexistant",
    ),
    (
        1,
        "une différence de CASSE est refusée (GitHub ne la tolère pas)",
        {"consommateur.yml": _consommateur("aperçus des vues")},
        "visant un libellé inexistant",
    ),
    (
        1,
        "une différence d ACCENT est refusée",
        {"consommateur.yml": _consommateur("Apercus des vues")},
        "visant un libellé inexistant",
    ),
    (
        1,
        "un espace en trop est refusé (comparaison stricte)",
        {"consommateur.yml": _consommateur("Aperçus des vues ")},
        "visant un libellé inexistant",
    ),
    # Non-vacuite : un balayage qui ne trouve rien a examiner ne se declare pas conforme.
    (
        1,
        "aucun renvoi à examiner : la garde refuse au lieu de passer",
        {
            "consommateur.yml": "name: docs\non:\n  push: {}\njobs:\n  b:\n    runs-on: ubuntu-latest\n"
        },
        "a rien examiné",
    ),
    # La tolerance reste etroite : un producteur en plus ne gene pas.
    (
        0,
        "un workflow sans renvoi ne déclenche pas",
        {
            "tiers.yml": "name: Quality gate\non:\n  push: {}\njobs:\n  c:\n    runs-on: ubuntu-latest\n"
        },
        "",
    ),
)


def _auto_test() -> int:
    """Les sept cas de la version bash, et chacun exige le MESSAGE autant que le code."""
    import contextlib
    import io
    import tempfile

    echecs = cas = rouges = 0
    for attendu, libelle, ecrasements, motif in CAS:
        cas += 1
        if attendu != 0:
            rouges += 1
        with tempfile.TemporaryDirectory(prefix="vc-renv-") as tmp:
            wf = pathlib.Path(tmp) / ".github" / "workflows"
            wf.mkdir(parents=True)
            (wf / "producteur.yml").write_text(PRODUCTEUR, encoding="utf-8")
            (wf / "consommateur.yml").write_text(CONSOMMATEUR, encoding="utf-8")
            for nom, contenu in ecrasements.items():
                (wf / nom).write_text(contenu, encoding="utf-8")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon):
                code = juge(pathlib.Path(tmp))
            sortie = tampon.getvalue()
        if code != attendu:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
            echecs = 1
            continue
        if motif and motif not in sortie:
            print(
                f"  ✘ {libelle} : code {attendu} obtenu, mais sans le message attendu (« {motif} »)."
            )
            print("       La garde n'a pas parlé : ce rouge vient d'ailleurs.")
            echecs = 1
            continue
        print(f"  ✔ {libelle}")

    verbe = "DOIT" if rouges == 1 else "DOIVENT"
    print(f"{cas} cas, dont {rouges} qui {verbe} rougir.")
    if echecs == 0:
        print("Auto-test de la garde des renvois : OK")
    else:
        print("Auto-test de la garde des renvois : ÉCHEC", file=sys.stderr)
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge())
