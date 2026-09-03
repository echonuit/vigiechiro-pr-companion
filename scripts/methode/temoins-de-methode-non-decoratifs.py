#!/usr/bin/env python3
"""Aucun auto-test des gardes de METHODE n est decoratif (#4760, article A2).

`scripts/adr/verifie_temoins_non_decoratifs.py` rend l article A2 mecanique : il neutralise chaque
detecteur et exige que la suite rougisse. Il ne couvre que `scripts/adr/`. Les gardes de
`scripts/methode/`, dont onze sont bloquants dans `lint.yml`, avaient chacun un `--auto-test` que
RIEN n obligeait a detecter quoi que ce soit : un motif elargi jusqu a tout accepter, un cas retire
« parce qu il genait », et le vert reste vert.

**La transposition n est pas celle qu on croit, et la mesure l a dit.** Les gardes d ADR sont mutes
en AJOUTANT la neutralisation en fin de fichier, parce que le harnais les IMPORTE : tout le fichier
s execute, puis les fonctions sont appelees. Un garde de methode, lui, se lance en `--auto-test` :
son `raise SystemExit` part AVANT d atteindre une neutralisation ajoutee a la fin, qui n agit donc
jamais. Cinq essais ont rendu « decoratif » pour cinq gardes dont deux avaient ete vus rougir sur un
vrai defaut le meme jour. La neutralisation s INSERE donc avant le point d entree.

**Il refuse plutot que de sauter.** Six gardes du corpus n ont aucun `if __name__` : leur corps
s execute au niveau du module, et aucune insertion sure n existe. Les passer en silence rendrait
vert sur une couverture partielle, ce qui est le defaut que ce garde traite. Ils sont donc NOMMES,
et leur sort est une decision a part - voir l issue citee dans le message de refus.

**Le corpus se derive de `lint.yml`**, et non d un glob : ce sont les gardes que la CI lance
vraiment. Un glob vieillit, et un script d appoint pose dans le dossier passerait pour un garde.

Usage :
    python3 scripts/methode/temoins-de-methode-non-decoratifs.py
    python3 scripts/methode/temoins-de-methode-non-decoratifs.py --auto-test
"""

import contextlib
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from _commun import sort_si_contrat_demande

ATELIER = RACINE / ".github" / "workflows" / "lint.yml"
LANCE = re.compile(r"scripts/methode/([a-z0-9-]+\.py)")
POINT_D_ENTREE = re.compile(r'^if __name__ == ["\']__main__["\']:', re.M)

# Ce qu on insere pour retirer sa detection a un garde, sans toucher a ce qui le decrit.
#
# **La fonction d auto-test est EPARGNEE, et c est ce qui rend la mesure honnete.** La neutraliser
# ferait rougir le garde trivialement - non parce qu il a cesse de detecter, mais parce que son
# point d entree rend une liste au lieu d un entier. Deux des sept gardes du corpus nomment la leur
# `auto_test`, sans souligne : sans cette exemption, leur verdict ne voulait rien dire.
#
# L exemption se DERIVE du nom, elle ne s enumere pas : toute fonction dont le nom porte « auto »
# et « test ». Une liste aurait vieilli au premier garde neuf, comme trois listes tenues a la main
# l ont fait dans ce depot.
NEUTRALISATION = """
import types as _t_mutation
for _nom_mutation, _val_mutation in list(globals().items()):
    _bas_mutation = _nom_mutation.lower()
    if (isinstance(_val_mutation, _t_mutation.FunctionType)
            and not _nom_mutation.startswith("_")
            and not ("auto" in _bas_mutation and "test" in _bas_mutation)):
        globals()[_nom_mutation] = (lambda *a, **k: [])

"""


def corpus() -> list[str]:
    """Les gardes de methode que `lint.yml` lance, derives et non enumeres."""
    return sorted(set(LANCE.findall(ATELIER.read_text(encoding="utf-8"))))


def porte_un_auto_test(source: str) -> bool:
    return "--auto-test" in source


def mutable(source: str) -> bool:
    """Un garde n est mutable que si son point d entree est reperable.

    Sans lui, il n existe aucun endroit sur ou inserer la neutralisation : la deviner reviendrait
    a rendre « decoratif » un garde qui ne l est pas, ce que ce garde existe pour eviter.
    """
    return POINT_D_ENTREE.search(source) is not None


def mute(source: str) -> str:
    """La source, neutralisation INSEREE avant le point d entree."""
    m = POINT_D_ENTREE.search(source)
    return source[: m.start()] + NEUTRALISATION + source[m.start() :]


@contextlib.contextmanager
def arbre_jetable():
    """Un depot ou muter sans toucher a celui-ci, comme le fait le garde des ADR depuis #4700."""
    with tempfile.TemporaryDirectory(prefix="vc-temoins-methode-") as tmp:
        faux = pathlib.Path(tmp) / "depot"
        faux.mkdir()
        shutil.copytree(RACINE / "scripts", faux / "scripts", symlinks=True)
        for entree in RACINE.iterdir():
            if entree.name not in {"scripts", ".git"}:
                (faux / entree.name).symlink_to(entree)
        yield faux


def auto_test_rougit(nom: str, faux: pathlib.Path) -> bool:
    """Son auto-test rougit-il quand le garde perd sa detection ?"""
    cible = faux / "scripts" / "methode" / nom
    original = cible.read_text(encoding="utf-8")
    try:
        cible.write_text(mute(original), encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(cible), "--auto-test"], capture_output=True, cwd=faux, check=False
        )
    finally:
        cible.write_text(original, encoding="utf-8")
    return rendu.returncode != 0


def suspects() -> tuple[list[str], list[str]]:
    """Les auto-tests qui restent verts sans detection, et ce qui n a pas pu etre mute."""
    decoratifs, illisibles = [], []
    with arbre_jetable() as faux:
        for nom in corpus():
            f = RACINE / "scripts" / "methode" / nom
            if not f.is_file():
                illisibles.append(f"{nom} : absent de scripts/methode")
                continue
            source = f.read_text(encoding="utf-8")
            if not porte_un_auto_test(source):
                continue
            if not mutable(source):
                illisibles.append(f"{nom} : aucun `if __name__` ou inserer la neutralisation")
                continue
            if not auto_test_rougit(nom, faux):
                decoratifs.append(f"{nom} : son auto-test reste vert, detection neutralisee")
    return decoratifs, illisibles


def code_de_sortie(decoratifs: list[str], illisibles: list[str]) -> int:
    """Le verdict, extrait du point d entree pour qu un temoin puisse l atteindre (issue #4788).

    Les DEUX refusent, et c est la decision de cette issue. Un garde illisible etait signale en
    sortant 0 : six sur quinze etaient dans ce cas, la CI restait verte, et la liste ne se vidait
    pas. Une ligne de journal sous un vert ne se lit pas.
    """
    return 1 if (decoratifs or illisibles) else 0


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    verifie("le corpus vient de lint.yml, et n est pas vide", len(corpus()) > 5, True)
    verifie(
        "un garde a point d entree est mutable",
        mutable('def f():\n    pass\n\n\nif __name__ == "__main__":\n    f()\n'),
        True,
    )
    verifie(
        "un garde sans point d entree ne l est pas", mutable("def f():\n    pass\n\n\nf()\n"), False
    )

    src = 'def detecte():\n    return [1]\n\n\nif __name__ == "__main__":\n    detecte()\n'
    # Le point qui a coute cinq mesures fausses : la neutralisation s INSERE, elle ne s ajoute pas.
    verifie(
        "la neutralisation se pose AVANT le point d entree",
        mute(src).index("_t_mutation") < mute(src).index("if __name__"),
        True,
    )
    verifie("elle ne se pose pas apres", mute(src).rstrip().endswith("detecte()"), True)
    # Le sens NEGATIF : sans lui, un `mute` qui rendrait son entree passerait les deux precedents.
    verifie("la source est bien changee", mute(src) != src, True)

    # La fonction d auto-test est epargnee, sinon le garde rougit pour la mauvaise raison (#4760).
    verifie(
        "`auto_test` est épargnée par la neutralisation",
        'not ("auto" in _bas_mutation and "test" in _bas_mutation)' in NEUTRALISATION,
        True,
    )
    # Et l exemption se derive : elle ne nomme aucun garde en particulier.
    verifie(
        "l exemption ne cite aucun nom de garde",
        any(g.split(".")[0] in NEUTRALISATION for g in corpus()),
        False,
    )

    # #4788. Un garde sans point d entree REFUSE, au lieu d etre signale sous un vert.
    verifie("rien a signaler passe", code_de_sortie([], []), 0)
    verifie("un garde decoratif refuse", code_de_sortie(["x"], []), 1)
    verifie("un garde SANS POINT D ENTREE refuse aussi", code_de_sortie([], ["y"]), 1)
    verifie("et les deux ensemble refusent", code_de_sortie(["x"], ["y"]), 1)
    return echecs


CONTRAT = {
    "geste": "auto-test de garde de methode qui reste vert sans detection",
    "population": "les gardes de scripts/methode que la suite charge",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/temoins-de-methode-non-decoratifs.py --auto-test",
    "decision": "ADR 4490",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    decoratifs, illisibles = suspects()
    for l in illisibles:
        print(f"NON ÉPROUVÉ : {l}", file=sys.stderr)
    for l in decoratifs:
        print(f"ÉCHEC : {l}", file=sys.stderr)
    if decoratifs:
        print(
            "\nUn auto-test qui reste vert alors que le garde ne détecte plus rien ne prouve rien.\n"
            "L'article A2 demande qu'un garde soit vu rouge sur sa propre mutation.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    if illisibles:
        print(
            "\nCes gardes exécutent leur corps au niveau du module : aucun endroit sûr où insérer\n"
            "la neutralisation, donc aucune preuve au titre de l'article A2.\n"
            "\nLe remède tient en une ligne : placez la partie qui S'EXÉCUTE sous\n"
            '`if __name__ == "__main__":`, en laissant AU-DESSUS tout ce qui se définit.\n'
            "La neutralisation s'insère juste avant ce point d'entrée, et une fonction\n"
            "définie après lui y échapperait.\n"
            "\nCe garde REFUSE désormais au lieu de le signaler (issue #4788). Il l'a signalé\n"
            "en sortant 0 tant que six gardes sur quinze étaient dans ce cas : une ligne de\n"
            "journal sous une CI verte ne se lit pas, et la liste ne se vidait pas.",
            file=sys.stderr,
        )
    if not code_de_sortie(decoratifs, illisibles):
        print(f"Les {len(corpus())} gardes de méthode éprouvés rougissent sous mutation.")
    raise SystemExit(code_de_sortie(decoratifs, illisibles))
