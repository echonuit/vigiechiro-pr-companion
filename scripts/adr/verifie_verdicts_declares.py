#!/usr/bin/env python3
"""Un appel de verdict declare ce qu'il a lu, ou il se nomme parmi les exceptions (ADR 5015).

`rapporte`, `rapporte_plancher` et `loupe` acceptent un champ `lus` depuis l'issue #5007, et
refusent sur `lus=0`. Un appel qui ne le declare pas rend `lus=?` : son zero reste indiscernable de
« n'a rien balaye », si bien que le refus ne le protege pas.

Le sous-chantier #5015 a converti 33 appels en cinq demandes, et son bilan nommait la dette que ce
garde ferme : rien n'empechait un appel neuf de naitre muet. **La regression a eu lieu en quelques
heures.** `5068-clic-sur-reference-tenue.py` est arrive avec `lus=?`, et sa signature reproduisait
meme l'une des trois divergences que la PR #5040 avait documentees. Rien ne pouvait le dire a son
auteur.

## Pourquoi une LISTE NOMMEE et non un cliquet

Un cliquet a N se satisfait de convertir un garde et d'en ajouter un autre muet : le nombre ne
bouge pas, et la dette a change de main. Les exceptions se nomment donc une par une, avec leur
raison, comme `verifie_temoins_non_decoratifs.py` le fait pour sa propre portee.

## Ce qu'il faut resoudre, et que ni un motif ni un nom ne donnent

Compter par LIGNE est faux : plusieurs appels tiennent sur plusieurs lignes. Compter par NOM l'est
aussi, et cela a coute une mesure fausse au lot 4 : `scripts/methode/couverture-openspec.py` definit
son propre `rapporte`, qui prend une racine et ne rend rien. Ce garde resout donc la LIAISON de
chaque nom : un appel par attribut vient du module charge, un appel direct ne vaut que s'il est
importe de `_commun` et non redefini sur place.
"""

from __future__ import annotations

import ast
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, rapporte, sort_si_contrat_demande

ADR = "5015"

VERDICTS = {"rapporte", "rapporte_plancher", "loupe"}

# Le fonds commun n'est pas un garde : il DEFINIT ces fonctions, il ne les appelle pas.
FONDS = {"_commun.py"}

# Les appels qui ne declarent rien, et pour lesquels c'est une DECISION (ADR 5015). Chacun est
# nomme avec sa raison, sinon cette liste deviendrait le tapis sous lequel on pousse la dette.
HORS_PORTEE = {
    "verifie_scripts.py": (
        "ses appels sont des TEMOINS du harnais, non des gardes qui lisent une population ; deux "
        "d entre eux eprouvent precisement la semantique du compte non declare, et leur mettre "
        "`lus=` detruirait ce qu ils gardent EN RENDANT LES TESTS VERTS"
    ),
    "compte-les-reliquats.py": (
        "sa population vide est sa REUSSITE : sur un runner vierge dont la suite nettoie bien, ni "
        "le temoin d avant ni les repertoires presents ne portent rien, et `lus=0` refuserait"
    ),
}


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les sources Python que ce garde LIT, extraites pour que `lus` les compte (issue #5007)."""
    base = racine or RACINE_DEPOT
    return sorted(
        f
        for f in (base / "scripts").rglob("*.py")
        if "__pycache__" not in f.parts and f.name not in FONDS
    )


def _appels_muets(source: str) -> list[tuple[int, str]]:
    """Les appels de verdict de `_commun` qui ne portent pas `lus`, avec leur ligne."""
    arbre = ast.parse(source)
    importes = {
        alias.asname or alias.name
        for n in ast.walk(arbre)
        if isinstance(n, ast.ImportFrom) and (n.module or "").endswith("_commun")
        for alias in n.names
    }
    locales = {n.name for n in ast.walk(arbre) if isinstance(n, ast.FunctionDef)}
    muets = []
    for noeud in ast.walk(arbre):
        if not isinstance(noeud, ast.Call):
            continue
        direct = getattr(noeud.func, "id", None)
        nom = direct or getattr(noeud.func, "attr", None)
        if nom not in VERDICTS:
            continue
        # Un appel DIRECT ne vaut que s il vient de `_commun` et n est pas redefini sur place.
        if direct is not None and (direct in locales or direct not in importes):
            continue
        if not any(kw.arg == "lus" for kw in noeud.keywords):
            muets.append((noeud.lineno, nom))
    return muets


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par appel de verdict muet qui n est pas nomme dans `HORS_PORTEE`."""
    base = racine or RACINE_DEPOT
    trouves = []
    for chemin in fichiers(racine):
        if chemin.name in HORS_PORTEE:
            continue
        try:
            source = chemin.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        try:
            muets = _appels_muets(source)
        except SyntaxError:
            continue
        vu = chemin.relative_to(base).as_posix() if chemin.is_relative_to(base) else chemin.name
        for ligne, nom in muets:
            trouves.append(f"{vu}:{ligne}  {nom}() ne declare pas ce qu il a lu")
    return trouves


def _auto_test() -> int:
    """Les deux moities : un appel muet est vu, et un appel qui declare ne l est pas.

    La seconde n est pas une formalite. Un garde qui refuserait TOUT, exceptions comprises, passe
    la premiere ; c est la meme cecite qu un temoin qui n affirmerait que des vides (ADR 5054).
    """
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    muet = 'from _commun import rapporte\nrapporte("0000", "t", [])\n'
    declare = 'from _commun import rapporte\nrapporte("0000", "t", [], lus=12)\n'
    local = "def rapporte(x):\n    return x\n\n\nrapporte(3)\n"
    attribut = 'commun = charge()\ncommun.loupe("0000", "t", [])\n'

    verifie("un appel muet est vu", len(_appels_muets(muet)), 1)
    verifie("un appel qui declare ne l est pas", len(_appels_muets(declare)), 0)
    verifie("un homonyme LOCAL n est pas un verdict", len(_appels_muets(local)), 0)
    verifie("un appel par attribut compte", len(_appels_muets(attribut)), 1)
    verifie("et la loupe est un verdict comme les autres", _appels_muets(attribut)[0][1], "loupe")

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


CONTRAT = {
    "geste": "appel de verdict qui ne declare pas ce qu il a lu",
    "population": "les points d entree de scripts/",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_verdicts_declares.py --auto-test",
    "decision": "ADR 5015",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(
        rapporte(
            ADR,
            "appels de verdict qui ne declarent pas ce qu ils ont lu",
            suspects(),
            lus=len(fichiers()),
        )
    )
