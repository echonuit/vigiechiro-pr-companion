#!/usr/bin/env python3
"""Un point d entree de `scripts/adr` declare ce qu il est (ADR 4636, issue #5149).

Les 41 points d entree de ce dossier portent un contrat depuis le sous-chantier #5117. Rien
n empechait le 42e d arriver sans le sien.

## Ce que ce garde tient, et pourquoi il ne compte pas

**Un corpus complet se reperd sans bruit.** Un garde neuf s ecrit sur le modele d un voisin, l auteur
copie le bloc de verdict et pas la declaration, et le compte redescend sans que personne ne le voie.
#5117 a passe une journee a le remonter de 2 a 41 ; ce garde existe pour que cela ne se refasse pas.

**C est un INVARIANT, pas un cliquet**, et la distinction vient de l ADR 5125. Un cliquet borne ce
qu on tolere et sa marge se releve ; ici il n y a rien a tolerer. #5102 l annoncait comme « le
cliquet », puis demandait « des exceptions NOMMEES, non un compte » : les deux ne vont pas ensemble,
et c est la description qui a raison contre l etiquette.

## Sa population est `scripts/adr`, et c est une decision

Le depot porte 70 points d entree Python, dont 42 declarent un contrat. A cette echelle, la liste
d exceptions en compterait vingt-huit, plus quarante-sept gardes shell : ce ne serait plus une liste
d exceptions mais l inventaire de ce qui n est pas fait, deguise en regle.

Elle s elargira quand les lots la rejoindront : le lot 4 de #5102 porte `scripts/methode`, et le
lot 5 mesure les gardes shell sans leur ecrire de contrat, la cible du depot etant de les convertir.

## Ce qu il ne fait PAS

Il ne juge pas le CONTENU du contrat. `verifie_contrats_tiennent.py` le fait deja, et mieux : il
confronte ce qui est declare a ce que le garde fait. Celui-ci ne repond qu a la question de
l existence, et deux gardes qui se partagent une question la tiennent mieux qu un seul qui la melange.
"""

from __future__ import annotations

import ast
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import sort_si_contrat_demande

ADR = "4636"
DOSSIER = pathlib.Path(__file__).resolve().parent

# Les points d entree qui n ont PAS a declarer de contrat, et pourquoi. Chacun est nomme avec sa
# raison, sinon cette liste devient le tapis sous lequel on pousse la dette : c est l idiome de
# `HORS_PORTEE` dans `verifie_verdicts_declares.py`.
#
# **Elle est VIDE a la livraison**, et c est la mesure qui compte : les 41 points d entree de
# `scripts/adr` declarent tous leur contrat. Une entree ajoutee ici est une decision, pas une
# formalite, et elle se defend dans la demande qui l ajoute.
SANS_CONTRAT: dict[str, str] = {}


def points_d_entree(dossier: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les fichiers de `scripts/adr` qui SE LANCENT, c est-a-dire portent un bloc `__main__`.

    Par l AST et non par le nom : `verifie_`, `loupe-` et les numeros sont trois conventions pour la
    meme chose, et un motif qui chercherait l une manquerait les deux autres. C est la lecon de
    #5046, ou trois heuristiques ont rendu trois comptes tous faux.
    """
    base = dossier or DOSSIER
    trouves = []
    for f in sorted(base.glob("*.py")):
        if f.name.startswith("_") or "__pycache__" in f.parts:
            continue
        try:
            arbre = ast.parse(f.read_text(encoding="utf-8"))
        except (SyntaxError, OSError):
            continue
        if any(isinstance(n, ast.If) and "__main__" in ast.dump(n.test) for n in arbre.body):
            trouves.append(f)
    return trouves


def declare_un_contrat(texte: str) -> bool:
    """Ce module declare-t-il un `CONTRAT` au niveau module ?

    La declaration, non la mention : un fichier qui parle de `CONTRAT` en prose n en porte pas un.
    Meme lecture que `verifie_contrats_tiennent.py` depuis #5144, et pour la meme raison.
    """
    try:
        arbre = ast.parse(texte)
    except SyntaxError:
        return False
    return any(
        isinstance(noeud, ast.Assign)
        and any(isinstance(c, ast.Name) and c.id == "CONTRAT" for c in noeud.targets)
        for noeud in arbre.body
    )


def suspects(dossier: pathlib.Path | None = None) -> list[str]:
    """Un suspect par point d entree qui ne declare rien et ne figure pas dans les exceptions."""
    trouves = []
    for chemin in points_d_entree(dossier):
        if chemin.name in SANS_CONTRAT:
            continue
        if not declare_un_contrat(chemin.read_text(encoding="utf-8", errors="ignore")):
            trouves.append(f"{chemin.name}  point d entree sans contrat")
    return trouves


def _auto_test() -> int:
    """Le mecanisme se prouve dans les DEUX sens : il voit un manque, et il ne crie pas sans."""
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test du garde des contrats obligatoires (#5149) :")

    verifie(
        "un module qui declare un CONTRAT en porte un",
        declare_un_contrat('CONTRAT = {"geste": "x"}\n'),
        True,
    )
    verifie(
        "une MENTION en prose n en est pas une",
        declare_un_contrat("# le CONTRAT se declare ainsi\nx = 1\n"),
        False,
    )
    verifie(
        "ni une variable locale du meme nom",
        declare_un_contrat("def f():\n    CONTRAT = {}\n    return CONTRAT\n"),
        False,
    )

    with tempfile.TemporaryDirectory(prefix="vc-5149-") as tmp:
        faux = pathlib.Path(tmp)
        # Un point d entree est un fichier qui SE LANCE. Les trois conventions de nommage du dossier
        # sont representees, pour qu aucune ne decide de la reponse.
        (faux / "0001-un-garde.py").write_text(
            'CONTRAT = {"geste": "x"}\n\nif __name__ == "__main__":\n    pass\n', encoding="utf-8"
        )
        (faux / "loupe-0002-une-loupe.py").write_text(
            'CONTRAT = {"geste": "y"}\n\nif __name__ == "__main__":\n    pass\n', encoding="utf-8"
        )
        (faux / "verifie_un_troisieme.py").write_text(
            'CONTRAT = {"geste": "z"}\n\nif __name__ == "__main__":\n    pass\n', encoding="utf-8"
        )
        # Ni un module sans bloc `__main__`, ni un prefixe par `_` : ils ne se lancent pas.
        (faux / "pas_un_point_d_entree.py").write_text("x = 1\n", encoding="utf-8")
        (faux / "_commun.py").write_text('if __name__ == "__main__":\n    pass\n', encoding="utf-8")
        verifie("les trois conventions de nom sont vues", len(points_d_entree(faux)), 3)
        verifie("un module sans __main__ n en est pas un", suspects(faux), [])

        # LE SENS QUI COMPTE : un point d entree neuf, sans contrat, est vu.
        (faux / "0003-sans-contrat.py").write_text(
            'import sys\n\nif __name__ == "__main__":\n    sys.exit(0)\n', encoding="utf-8"
        )
        verifie(
            "un point d entree sans contrat est vu",
            suspects(faux),
            ["0003-sans-contrat.py  point d entree sans contrat"],
        )

    # La liste des exceptions est VIDE, et ce cas la tient : la remplir devient une decision visible.
    verifie("aucune exception n est ouverte", SANS_CONTRAT, {})
    # Et sur le depot reel, les 41 declarent.
    verifie("le corpus reel ne porte aucun suspect", suspects(), [])

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


# Ce que ce garde DECLARE etre. Il s applique a lui-meme : sa population le contient.
CONTRAT = {
    "geste": "point d entree de scripts/adr qui ne declare aucun contrat",
    "population": "les points d entree de scripts/adr, par leur bloc __main__",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_contrat_obligatoire.py --auto-test",
    "decision": "ADR 4636",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    manquants = suspects()
    print(f"ADR {ADR} - point d entree de scripts/adr sans contrat declare")
    for m in manquants:
        print(f"  {m}")
    print(
        f"\nADR {ADR} | lus={len(points_d_entree())} | suspects={len(manquants)} "
        f"| exceptions={len(SANS_CONTRAT)} | verdict={'refus' if manquants else 'ok'}"
    )
    if manquants:
        print(
            "\nÉCHEC : un point d'entrée de scripts/adr ne déclare pas ce qu'il est.\n"
            "Ajoutez-lui un CONTRAT de six champs, sur le modèle de 0008-echec-silencieux.py, et la\n"
            "branche qui l'imprime AVANT tout le reste. Ce n'est pas un cliquet : il n'y a pas de\n"
            "marge à relever. Si ce point d'entrée ne doit vraiment pas déclarer, nommez-le dans\n"
            "SANS_CONTRAT avec sa raison - c'est une décision, et elle se défend dans la demande.",
            file=sys.stderr,
        )
        sys.exit(1)
    sys.exit(0)
