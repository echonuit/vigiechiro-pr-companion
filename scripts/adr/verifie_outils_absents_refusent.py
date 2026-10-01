#!/usr/bin/env python3
"""Un outil externe ABSENT fait REFUSER, il ne fait pas lever (#5692).

    python3 scripts/adr/verifie_outils_absents_refusent.py
    python3 scripts/adr/verifie_outils_absents_refusent.py --auto-test

## Le defaut, et pourquoi il ne se voit pas

`subprocess.run(["gh", ...], check=False)` ne couvre PAS un executable introuvable. `check=False`
parle du code de sortie ; quand `gh` n est pas dans le `PATH`, `subprocess.run` leve un
`FileNotFoundError` avant qu il y ait un code. Le dispositif s arrete alors sur une trace de pile.

Une trace est bruyante, donc personne n y lira un faux vert. Ce n est pas la question. Ce depot
distingue partout « je refuse de conclure », qui nomme sa cause et son geste de reparation, d un
plantage qui nomme une ligne de Python : le premier dit « ce refus ne parle pas de votre diff », le
second fait chercher un bug dans le garde.

Et le cas n est pas theorique. Il n y a plus de `node` sur le poste de developpement : les deux gardes
OpenSpec le disent proprement en refusant. Le jour ou `gh` manquera pareil - un conteneur minimal, un
poste neuf, un `PATH` ampute - six dispositifs rendront une trace la ou trois rendent une phrase.

## Ce qui rend ce garde necessaire plutot qu une relecture

`mesure_duree_portail.py` et `mesure_minutes_par_pr.py` ont ete repares UN PAR UN, chacun par son lot,
et six appels sont restes. Le pire portait la promesse : `_forge.liste_issues` documentait « gh absent
est un REFUS et non une liste vide » et levait avant d atteindre son propre test. Une docstring qui
promet un comportement absent est pire qu un silence, parce qu elle se cite.

Corriger les six sans garde, c est accepter que le septieme soit oublie. Ce lot fait les deux.

## Comment la protection se lit

Par l arbre, et non par un motif de texte. Un appel est protege quand il est **descendant** d un
`try` dont un gestionnaire attrape `FileNotFoundError`, `OSError`, `Exception` ou `BaseException` -
les deux premiers nomment le defaut, les deux derniers le couvrent par ratissage.

Chercher `try` dans le fichier ne suffirait pas : un `try` ailleurs dans le meme module ne protege
rien. Et chercher le texte `except OSError` encore moins, pour la meme raison.

## Ce que ce garde ne fait pas

**Il ne juge que `gh`.** C est le seul outil externe que le depot appelle par `subprocess.run` avec un
premier argument litteral. Un autre outil s ajouterait a `OUTILS` avec sa raison.

**Il ne suit pas une couture.** Un appel passe par un parametre - `lanceur(arguments, ...)`, comme
`surveille_la_demande.py` le fait pour ses cas hors ligne - n a pas de premier argument litteral, donc
il n entre pas dans la population. C est juste : la protection appartient alors au lanceur reel, et le
site qui le fournit est deja dans la population s il nomme `gh`.
"""

from __future__ import annotations

import ast
import pathlib
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))

from _commun import cas_d_auto_test, refuse, sort_si_contrat_demande

# ⟨les outils dont l absence doit REFUSER⟩ Un seul aujourd hui : `gh` est le seul outil externe que
# le depot appelle avec un premier argument litteral. En ajouter un demande d ecrire pourquoi.
OUTILS = ("gh",)

# ⟨ce qui couvre un executable introuvable⟩ Les deux premiers le nomment, les deux derniers le
# couvrent par ratissage. `CalledProcessError` n y est pas : elle parle d un code de sortie, pas d un
# outil manquant, et l attraper ne protege de rien ici.
COUVRENT = ("FileNotFoundError", "OSError", "Exception", "BaseException")


def _fichiers() -> list[pathlib.Path]:
    """Les `.py` que git suit, car un fichier non indexe est invisible des cliquets."""
    rendu = subprocess.run(
        ["git", "-C", str(RACINE), "ls-files", "*.py"],
        capture_output=True,
        text=True,
        check=False,
    )
    return [RACINE / ligne for ligne in rendu.stdout.split() if ligne]


def vise_un_outil(appel: ast.Call) -> str | None:
    """L outil que cet appel lance, ou `None`. Le premier argument doit etre une liste LITTERALE."""
    nom = getattr(appel.func, "attr", None) or getattr(appel.func, "id", None)
    if nom != "run" or not appel.args:
        return None
    premier = appel.args[0]
    if not isinstance(premier, (ast.List, ast.Tuple)) or not premier.elts:
        return None
    tete = premier.elts[0]
    if isinstance(tete, ast.Constant) and tete.value in OUTILS:
        return tete.value
    return None


def _sous_un_try_qui_couvre(appel: ast.Call, arbre: ast.AST) -> bool:
    """`appel` est-il DESCENDANT d un `try` qui attrape l absence de l executable ?

    Le lien de descendance est ce qui compte : un `try` ailleurs dans le meme module ne protege rien,
    et chercher `except OSError` dans le texte du fichier ferait exactement cette erreur.
    """
    for noeud in ast.walk(arbre):
        if not isinstance(noeud, ast.Try):
            continue
        if not any(appel is descendant for descendant in ast.walk(noeud)):
            continue
        for gestionnaire in noeud.handlers:
            vu = "BaseException" if gestionnaire.type is None else ast.unparse(gestionnaire.type)
            if any(couvre in vu for couvre in COUVRENT):
                return True
    return False


def _sonde_l_outil(fonction: ast.AST, outil: str) -> bool:
    """Cette fonction appelle-t-elle `which("<outil>")` ?"""
    for interne in ast.walk(fonction):
        if not isinstance(interne, ast.Call):
            continue
        nomme = getattr(interne.func, "attr", None) or getattr(interne.func, "id", None)
        if nomme != "which":
            continue
        if any(isinstance(a, ast.Constant) and a.value == outil for a in interne.args):
            return True
    return False


def _sonde_avant(appel: ast.Call, outil: str, arbre: ast.AST) -> bool:
    """La fonction qui porte `appel` sonde-t-elle la presence de l outil AVANT de le lancer ?

    `shutil.which("gh") is None` suivi d un refus est une protection, et une MEILLEURE qu un `try` :
    elle nomme la cause avant d essayer, la ou le filet la rattrape apres. Ce garde l a d abord
    ignoree et a accuse `scripts/_commun/forge.py`, qui faisait exactement la bonne chose.

    Trouve en LISANT les six sites, pas en lançant la mesure : l instrument accusait, le site etait
    sain. La population est donc de cinq et non de six.
    """
    sondeuses = {
        f.name
        for f in ast.walk(arbre)
        if isinstance(f, (ast.FunctionDef, ast.AsyncFunctionDef)) and _sonde_l_outil(f, outil)
    }
    for noeud in ast.walk(arbre):
        if not isinstance(noeud, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        if not any(appel is descendant for descendant in ast.walk(noeud)):
            continue
        if _sonde_l_outil(noeud, outil):
            return True
        # ⟨UN cran d indirection, et pas plus⟩ Extraire la sonde dans une fonction partagee est
        # MEILLEUR que la recopier : `_forge.py` reproche lui-meme d avoir ecrit son refus « mot
        # pour mot, a deux endroits ». Ce garde a d abord puni cette extraction, donc il punissait
        # la bonne pratique. Un seul cran, parce qu une chaine plus longue se verifie mal et ne
        # s est jamais presentee - si elle se presente, ce paragraphe est a reprendre.
        for interne in ast.walk(noeud):
            if isinstance(interne, ast.Call):
                nomme = getattr(interne.func, "attr", None) or getattr(interne.func, "id", None)
                if nomme in sondeuses:
                    return True
    return False


def est_protege(appel: ast.Call, outil: str, arbre: ast.AST) -> bool:
    """Deux formes de protection, et la seconde est la meilleure des deux.

    Un filet qui rattrape l absence, ou une sonde qui la nomme avant d essayer.
    """
    return _sous_un_try_qui_couvre(appel, arbre) or _sonde_avant(appel, outil, arbre)


def nus(fichiers: list[pathlib.Path] | None = None) -> list[str]:
    """Les appels a un outil externe qu aucun `try` ne protege, nommes par fichier et ligne."""
    trouves = []
    for fichier in fichiers if fichiers is not None else _fichiers():
        try:
            source = fichier.read_text(encoding="utf-8")
            arbre = ast.parse(source)
        except (SyntaxError, UnicodeDecodeError, OSError):
            continue
        for noeud in ast.walk(arbre):
            if not isinstance(noeud, ast.Call):
                continue
            outil = vise_un_outil(noeud)
            if outil and not est_protege(noeud, outil, arbre):
                relatif = fichier.relative_to(RACINE) if RACINE in fichier.parents else fichier
                trouves.append(f"{relatif}:{noeud.lineno} lance « {outil} » sans filet")
    return sorted(trouves)


def juge() -> int:
    tous = []
    for fichier in _fichiers():
        try:
            arbre = ast.parse(fichier.read_text(encoding="utf-8"))
        except (SyntaxError, UnicodeDecodeError, OSError):
            continue
        tous += [n for n in ast.walk(arbre) if isinstance(n, ast.Call) and vise_un_outil(n)]
    muets = nus()
    print(f"OUTILS EXTERNES | appels={len(tous)} | sans filet={len(muets)}")
    for ligne in muets:
        print(f"  ✘ {ligne}")
    if muets:
        refuse(
            f"{len(muets)} appel(s) a un outil externe qui LEVENT au lieu de refuser : "
            + ", ".join(muets),
            "entourez l appel d un `try` qui attrape `OSError`, et faites-lui rendre un refus qui "
            "nomme sa cause et son geste - `check=False` ne couvre PAS un executable introuvable. "
            "`mesure_duree_portail.insiste` en est le patron.",
        )
    return 0


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()
    import tempfile

    def dans_un_bac(source: str) -> list[str]:
        with tempfile.TemporaryDirectory(prefix="vc-5692-") as bac:
            f = pathlib.Path(bac) / "sonde.py"
            f.write_text(source, encoding="utf-8")
            return nus([f])

    NU = "import subprocess\nsubprocess.run(['gh', 'pr', 'list'], check=False)\n"
    PROTEGE = (
        "import subprocess\n"
        "try:\n"
        "    subprocess.run(['gh', 'pr', 'list'], check=False)\n"
        "except OSError:\n"
        "    pass\n"
    )
    AILLEURS = (
        "import subprocess\n"
        "try:\n"
        "    pass\n"
        "except OSError:\n"
        "    pass\n"
        "subprocess.run(['gh', 'pr', 'list'], check=False)\n"
    )
    MAUVAIS_FILET = (
        "import json\nimport subprocess\n"
        "try:\n"
        "    subprocess.run(['gh', 'pr', 'list'], check=False)\n"
        "except json.JSONDecodeError:\n"
        "    pass\n"
    )

    verifie("un appel nu est vu", lambda: len(dans_un_bac(NU)), 1)
    verifie(
        "un appel sous un `try` qui attrape OSError ne l est pas", lambda: dans_un_bac(PROTEGE), []
    )
    # ⟨LE CAS QUI COMPTE⟩ Sans lui, chercher `try` dans le fichier passerait, et ce garde dirait
    # protege un appel qui ne l est pas. C est la difference entre lire l arbre et lire du texte.
    verifie(
        "un `try` AILLEURS dans le fichier ne protege pas", lambda: len(dans_un_bac(AILLEURS)), 1
    )
    verifie(
        "un filet qui n attrape pas l absence ne protege pas",
        lambda: len(dans_un_bac(MAUVAIS_FILET)),
        1,
    )
    verifie(
        "un `except` NU attrape tout, donc protege",
        lambda: dans_un_bac(
            NU.replace("subprocess.run", "pass  #") + PROTEGE.replace("OSError", "")
        ),
        [],
    )
    verifie(
        "un autre outil que ceux declares n entre pas dans la population",
        lambda: dans_un_bac("import subprocess\nsubprocess.run(['npm', 'ci'], check=False)\n"),
        [],
    )
    # ⟨une COUTURE n entre pas⟩ Un appel dont le premier argument n est pas une liste litterale ne se
    # juge pas ici : la protection appartient au lanceur reel. Declare plutot que subi.
    # ⟨LA SONDE, directe puis INDIRECTE⟩ `shutil.which` avant l appel protege, et l extraire dans une
    # fonction partagee protege aussi - c est meme meilleur, puisque le refus ne se recopie pas. Ce
    # garde a d abord puni l extraction : il accusait `_forge.py` apres sa reparation.
    SONDE_DIRECTE = (
        "import shutil\nimport subprocess\n"
        "def f():\n"
        "    if shutil.which('gh') is None:\n"
        "        raise SystemExit(2)\n"
        "    subprocess.run(['gh', 'pr', 'list'], check=False)\n"
    )
    SONDE_INDIRECTE = (
        "import shutil\nimport subprocess\n"
        "def present():\n"
        "    if shutil.which('gh') is None:\n"
        "        raise SystemExit(2)\n"
        "def f():\n"
        "    present()\n"
        "    subprocess.run(['gh', 'pr', 'list'], check=False)\n"
    )
    # ⟨LE NEGATIF, et il est indispensable⟩ Appeler une fonction qui ne sonde RIEN ne protege pas.
    # Sans ce cas, l indirection blanchirait tout appel precede de n importe quel appel.
    HELPER_MUET = (
        "import subprocess\n"
        "def prepare():\n"
        "    return 1\n"
        "def f():\n"
        "    prepare()\n"
        "    subprocess.run(['gh', 'pr', 'list'], check=False)\n"
    )
    verifie("une sonde DIRECTE avant l appel protege", lambda: dans_un_bac(SONDE_DIRECTE), [])
    verifie(
        "une sonde EXTRAITE dans une fonction appelee protege aussi",
        lambda: dans_un_bac(SONDE_INDIRECTE),
        [],
    )
    verifie(
        "mais appeler une fonction qui ne sonde RIEN ne protege pas",
        lambda: len(dans_un_bac(HELPER_MUET)),
        1,
    )
    verifie(
        "une sonde pour un AUTRE outil ne protege pas celui-ci",
        lambda: len(dans_un_bac(SONDE_DIRECTE.replace("which('gh')", "which('npm')"))),
        1,
    )
    verifie(
        "un appel par couture n a pas de premier argument litteral",
        lambda: dans_un_bac("def f(lanceur, args):\n    return lanceur(args, check=False)\n"),
        [],
    )
    return echecs()


CONTRAT = {
    "geste": "appel a un outil externe qui leve au lieu de refuser",
    "population": 'les `subprocess.run(["gh", ...])` a premier argument litteral que git suit',
    "dispositif": "invariant",
    "seuil": "(aucune marge : tout appel sans filet refuse)",
    "temoin": "scripts/adr/verifie_outils_absents_refusent.py --auto-test",
    "decision": "#5692",
    # ⟨les deux fonds de forge, et tout ce qui les appelle⟩ Les six appels d origine vivaient dans
    # `.github/scripts/` ET `scripts/`, ce qui est la raison pour laquelle un garde vaut mieux que
    # six relectures : aucun lot ne touche les deux a la fois.
    "chemins": """
scripts/**
.github/scripts/**
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(_auto_test() if "--auto-test" in sys.argv else juge())
