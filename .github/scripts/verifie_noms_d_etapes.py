#!/usr/bin/env python3
"""Un nom d etape ou de job dit a l ecran ce qu il ecrit dans le fichier (#5219, porte du bash).

En YAML, un `#` precede d une espace ouvre un commentaire, y compris au milieu d un scalaire non
cite. Un nom d etape ecrit

    - name: Auto-test des scripts ADR (bloquant, #2467)

vaut donc `Auto-test des scripts ADR (bloquant,` : le numero d issue est mange avant que GitHub ne
voie quoi que ce soit. Six etapes de ce depot etaient dans ce cas (#4255), quatre dans `lint.yml` et
deux dans `maven.yml`.

**Ce qui rend le defaut durable : le fichier a raison.** On relit le YAML, on y voit le numero, et on
conclut que c est l interface qui coupe a l affichage. Rien ne rougit, rien ne previent, et ce qu on
perd est precisement ce qui sert a retrouver POURQUOI une etape existe.

Le remede tient en deux guillemets : `- name: "… (bloquant, #2467)"`.

## Le recoupement, et ce qu il evite

Une ligne de shell dans un `run: |` peut ressembler a `name: quelque chose #x`. Le garde ne se fie
donc pas au motif seul : ce que YAML a retenu doit etre **un nom que l arbre porte vraiment**. Sans
ce recoupement, il accuserait une chaine imprimee par un `echo`, et c est un de ses six cas.
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
FLUX = "FLUX"

# Une ligne `name:` dont la valeur n est ni citee ni un scalaire de bloc.
LIGNE = re.compile(r"^\s*(?:-\s+)?name:[ \t]+(?![|>&*!\"'])(.+?)[ \t]*$")


def ateliers(flux: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les fichiers d atelier que ce garde LIT, `.yml` et `.yaml`."""
    base = flux or pathlib.Path(os.environ.get(FLUX, RACINE / ".github" / "workflows"))
    if not base.is_dir():
        return []
    return sorted(list(base.glob("*.yml")) + list(base.glob("*.yaml")))


def _noms_de_l_arbre(arbre) -> set[str]:
    """Les noms que YAML a REELLEMENT retenus : le recoupement du motif."""
    lus = set()
    if isinstance(arbre.get("name"), str):
        lus.add(arbre["name"])
    for job in (arbre.get("jobs") or {}).values():
        if not isinstance(job, dict):
            continue
        if isinstance(job.get("name"), str):
            lus.add(job["name"])
        for etape in job.get("steps") or []:
            if isinstance(etape, dict) and isinstance(etape.get("name"), str):
                lus.add(etape["name"])
    return lus


def ecarts(flux: pathlib.Path | None = None) -> list[tuple[str, int, str, str]] | None:
    """Les noms qui perdent du texte, ou `None` si un atelier est illisible.

    Rendre `None` plutot qu une liste vide n est pas un detail : un YAML illisible est une PANNE, et
    une liste vide se lirait « aucun nom ne perd de texte ».
    """
    import yaml

    trouves = []
    for chemin in ateliers(flux):
        texte = chemin.read_text(encoding="utf-8")
        try:
            arbre = yaml.safe_load(texte)
        except yaml.YAMLError as erreur:
            print(f"❌ {chemin.name} : YAML illisible ({erreur.__class__.__name__})")
            return None
        if not isinstance(arbre, dict):
            continue
        lus = _noms_de_l_arbre(arbre)
        for numero, ligne in enumerate(texte.splitlines(), 1):
            trouve = LIGNE.match(ligne)
            if not trouve:
                continue
            brut = trouve.group(1).strip()
            try:
                retenu = yaml.safe_load(brut)
            except yaml.YAMLError:
                retenu = None
            if not isinstance(retenu, str):
                continue
            retenu = retenu.strip()
            if retenu == brut or retenu not in lus:
                continue
            trouves.append((chemin.name, numero, brut, retenu))
    return trouves


def juge(flux: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("❌ PyYAML est absent : la garde ne peut rien analyser.")
        print("   C'est la GARDE qui est en cause, pas les workflows.")
        return 1
    if not ateliers(flux):
        print(f"❌ Aucun workflow sous {flux or os.environ.get(FLUX, '')}")
        print("   La garde ne peut rien confronter : chemin déplacé ?")
        return 1
    trouves = ecarts(flux)
    if trouves is None:
        return 1
    if trouves:
        print(f"❌ {len(trouves)} nom(s) perdent du texte à l'analyse YAML :")
        for fichier, numero, brut, retenu in trouves:
            print(f"   {fichier}:{numero}")
            print(f"      écrit   : {brut}")
            print(f"      retenu  : {retenu}")
        print()
        print(
            "   Un `#` précédé d'une espace ouvre un commentaire. Citer le nom entre guillemets :"
        )
        print('      - name: "… (bloquant, #1234)"')
        return 1
    print("✓ Aucun nom d'étape ou de job ne perd de texte à l'analyse YAML.")
    return 0


# Les six ateliers d essai, nommes : une fixture qui porte un nom se relit, et la concatenation
# implicite dans un tuple est refusee par ruff (ISC004).
ETAPE_NUE = """on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Auto-test des scripts ADR (bloquant, #2467)
        run: echo
"""
ETAPE_CITEE = """on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: "Auto-test des scripts ADR (bloquant, #2467)"
        run: echo
"""
JOB_NU = """on: [push]
jobs:
  j:
    name: Portail qualite (PMD, #3300)
    runs-on: ubuntu-latest
    steps:
      - run: echo
"""
DIESE_COLLE = """on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Auto-test des scripts ADR (bloquant,#2467)
        run: echo
"""
COMMENTAIRE_LEGITIME = """on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Une etape ordinaire
        run: echo bonjour  # ceci est un vrai commentaire
"""
SHELL_QUI_RESSEMBLE = """on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Une etape ordinaire
        run: |
          echo "name: quelque chose (bloquant, #2467)"
"""

CAS = (
    # Le cas de #4255, reduit : le numero d issue disparait avant GitHub.
    ("rouge", "un nom d etape non cite perd ce qui suit le diese", ETAPE_NUE),
    # Deux guillemets suffisent, et c est tout ce que la garde demande.
    ("vert", "le meme nom, cite, passe", ETAPE_CITEE),
    # Un nom de JOB est atteint de la meme facon.
    ("rouge", "un nom de job non cite est vu lui aussi", JOB_NU),
    # Sans espace avant le diese, YAML ne coupe rien : refuser ici serait refuser une forme juste.
    ("vert", "un diese colle ne mange rien, la forme reste acceptee", DIESE_COLLE),
    # Un commentaire au bout d un `run:` est legitime : la garde ne regarde que les `name:`.
    ("vert", "un commentaire hors d un name reste permis", COMMENTAIRE_LEGITIME),
    # Une ligne de shell qui ressemble a un `name:` ne doit pas etre prise pour un nom d etape.
    ("vert", "une ligne de shell qui ressemble a un name est ignoree", SHELL_QUI_RESSEMBLE),
)


def _auto_test() -> int:
    """Les six cas de la version bash, dont DEUX qui doivent rougir."""
    import tempfile

    echecs = total = 0
    print("AUTO-TEST")
    for attendu, libelle, contenu in CAS:
        total += 1
        with tempfile.TemporaryDirectory(prefix="vc-noms-") as tmp:
            flux = pathlib.Path(tmp) / ".github" / "workflows"
            flux.mkdir(parents=True)
            (flux / "essai.yml").write_text(contenu, encoding="utf-8")
            code = juge(flux)
        obtenu = "vert" if code == 0 else "rouge"
        if obtenu == attendu:
            print(f"  [OK   ] {libelle:<62} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {libelle:<62} -> {obtenu} (attendu {attendu})")
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
