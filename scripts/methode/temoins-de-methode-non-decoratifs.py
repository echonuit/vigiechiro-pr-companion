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

import ast
import contextlib
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test, sort_si_contrat_demande

ATELIER = RACINE / ".github" / "workflows" / "lint.yml"
LANCE = re.compile(r"scripts/methode/([a-z0-9-]+\.py)")

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


def ligne_du_point_d_entree(source: str) -> int | None:
    """La ligne du `if __name__ == "__main__":` de MODULE, lue dans l ARBRE et non par un motif.

    Un motif ne distingue pas le code d une chaine. Des qu un garde porte un litteral contenant ce
    texte - et c est le cas de tout banc dont l auto-test ecrit de faux gardes - `search` prend la
    PREMIERE occurrence, la neutralisation s insere au milieu du litteral, et elle n y neutralise
    rien. Le garde tourne alors normalement, son auto-test passe, et ce banc conclut « decoratif »
    sur un garde qui ne l est pas.

    Ce n est pas une hypothese : le banc de `.github/scripts` s est fait prendre sur LUI-MEME en
    #5254, ou le motif trouvait quatre occurrences pour un seul point d entree. Le cas
    `un point d entree cache dans une chaine ne trompe pas` de l auto-test ci-dessous rejoue le
    piege, et il est vu ROUGE avant cette correction (#5263).

    L arbre ne peut pas se tromper : il ne voit que les `if` de niveau module. C est la difference
    entre reconnaitre une forme et demander a la chose ([ADR 5102]).

    ## Pourquoi elle est RECOPIEE et non partagee

    `.github/scripts/temoins_de_ci_non_decoratifs.py` en porte une jumelle. La question du partage
    s est posee, et la mesure la tranche contre : elle fait QUATORZE lignes la-bas et DIX-SEPT ici,
    et les deux bancs ne partagent RIEN d autre - ni fonds, ni import, ni arbre. Les relier
    demanderait un QUATRIEME domicile commun, apres `scripts/_commun/` et
    `.github/scripts/_forge.py`, pour une fonction que chacun peut lire en entier sans quitter son
    fichier.

    C est la mesure de #5216 appliquee a ces deux-la : « le partage reel est plus etroit qu il n y
    parait ». Ce qui protege ici n est pas le partage, c est que CHACUN porte son cas rouge : celui
    de ce banc est plus bas, et celui de l autre vit dans son propre auto-test.
    """
    for noeud in ast.parse(source).body:
        cible = getattr(noeud, "test", None)
        if (
            isinstance(noeud, ast.If)
            and isinstance(cible, ast.Compare)
            and isinstance(cible.left, ast.Name)
            and cible.left.id == "__name__"
        ):
            return noeud.lineno
    return None


def mutable(source: str) -> bool:
    """Un garde n est mutable que si son point d entree est reperable.

    Sans lui, il n existe aucun endroit sur ou inserer la neutralisation : la deviner reviendrait
    a rendre « decoratif » un garde qui ne l est pas, ce que ce garde existe pour eviter.
    """
    try:
        return ligne_du_point_d_entree(source) is not None
    except SyntaxError:
        return False


def mute(source: str) -> str:
    """La source, neutralisation INSEREE avant le point d entree de module."""
    ligne = ligne_du_point_d_entree(source)
    lignes = source.splitlines(keepends=True)
    return "".join(lignes[: ligne - 1]) + NEUTRALISATION + "".join(lignes[ligne - 1 :])


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


TRACE = "Traceback (most recent call last)"


def verdict_sous_mutation(nom: str, faux: pathlib.Path) -> tuple[str, str]:
    """Ce que rend l auto-test du garde MUTE : « tient », « non concluant » ou « decoratif ».

    TROIS valeurs et non deux, et c est la mesure qui l a impose (ADR 5257). La neutralisation
    remplace chaque fonction par un `lambda` rendant `[]` : un garde dont une fonction rend un tuple,
    un entier ou un chemin PLANTE au lieu d assertir. Ce rouge-la ne prouve rien (ADR 4918), et le
    compter comme une reussite revient a annoncer 22 gardes eprouves quand il y en a 16.

    Mesure du 2026-09-05 sur les 23 de la population : 16 tiennent, 6 ne concluent pas, 0 decoratifs.

    La cause est rendue avec le verdict : elle est ce qu on lit pour savoir POURQUOI un garde ne
    conclut pas, et c est elle qui distingue « il faudrait le rendre mutable » de « le banc a un
    defaut ».
    """
    cible = faux / "scripts" / "methode" / nom
    original = cible.read_text(encoding="utf-8")
    try:
        cible.write_text(mute(original), encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(cible), "--auto-test"], capture_output=True, cwd=faux, check=False
        )
    finally:
        cible.write_text(original, encoding="utf-8")
    if rendu.returncode == 0:
        return "decoratif", "reste vert sans sa detection"
    erreur = rendu.stderr.decode("utf-8", "replace")
    if TRACE in erreur:
        lignes = [l for l in erreur.strip().splitlines() if l and not l.startswith(" ")]
        return "non concluant", (lignes[-1] if lignes else "trace illisible")[:110]
    return "tient", ""


def suspects() -> tuple[list[str], list[str], list[str]]:
    """TROIS listes : les decoratifs, les illisibles, et les NON CONCLUANTS.

    La troisieme est ce que l ADR 5257 ajoute. Elle n est pas un refus - un garde qui plante sous
    mutation n a rien prouve, mais il n a rien montre de faux non plus - et elle n est pas un
    silence : chacun est nomme avec sa cause. Le compter parmi les eprouves annoncerait 23 gardes
    tenus quand il y en a 16.
    """
    decoratifs, illisibles, non_concluants = [], [], []
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
            verdict, cause = verdict_sous_mutation(nom, faux)
            if verdict == "decoratif":
                decoratifs.append(f"{nom} : son auto-test reste vert, detection neutralisee")
            elif verdict == "non concluant":
                non_concluants.append(f"{nom} : {cause}")
    return decoratifs, illisibles, non_concluants


def code_de_sortie(decoratifs: list[str], illisibles: list[str]) -> int:
    """Le verdict, extrait du point d entree pour qu un temoin puisse l atteindre (issue #4788).

    Les DEUX refusent, et c est la decision de cette issue. Un garde illisible etait signale en
    sortant 0 : six sur quinze etaient dans ce cas, la CI restait verte, et la liste ne se vidait
    pas. Une ligne de journal sous un vert ne se lit pas.
    """
    return 1 if (decoratifs or illisibles) else 0


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()

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

    # #5264. Un garde qui PLANTE sous mutation n est pas eprouve. Sans ce cas, le banc comptait ces
    # rouges-la parmi les reussites et annoncait 23 gardes tenus quand il y en a 17.
    with arbre_jetable() as faux_verdict:
        planteur = faux_verdict / "scripts" / "methode" / "faux-planteur.py"
        planteur.write_text(
            "def detecte():\n    return [1], 1\n\n\n"
            "def _auto_test():\n    liste, compte = detecte()\n"
            '    print("ok" if compte == 1 else "ECHEC")\n'
            "    return 0 if compte == 1 else 1\n\n\n"
            'if __name__ == "__main__":\n    raise SystemExit(_auto_test())\n',
            encoding="utf-8",
        )
        verdict, cause = verdict_sous_mutation("faux-planteur.py", faux_verdict)
        verifie("un garde qui plante ne CONCLUT pas", verdict, "non concluant")
        verifie("et sa cause est rendue", "ValueError" in cause or "TypeError" in cause, True)

        tenu = faux_verdict / "scripts" / "methode" / "faux-tenu.py"
        tenu.write_text(
            "def detecte():\n    return [1]\n\n\n"
            "def _auto_test():\n"
            '    print("ok" if detecte() == [1] else "ECHEC")\n'
            "    return 0 if detecte() == [1] else 1\n\n\n"
            'if __name__ == "__main__":\n    raise SystemExit(_auto_test())\n',
            encoding="utf-8",
        )
        verifie(
            "un garde qui lit sa detection TIENT",
            verdict_sous_mutation("faux-tenu.py", faux_verdict)[0],
            "tient",
        )

        muet = faux_verdict / "scripts" / "methode" / "faux-muet.py"
        muet.write_text(
            "def detecte():\n    return [1]\n\n\n"
            'def _auto_test():\n    print("ok, sans rien lire")\n    return 0\n\n\n'
            'if __name__ == "__main__":\n    raise SystemExit(_auto_test())\n',
            encoding="utf-8",
        )
        verifie(
            "un garde qui ne la lit pas est DECORATIF",
            verdict_sous_mutation("faux-muet.py", faux_verdict)[0],
            "decoratif",
        )

    # #5263. Le piege qui a fait tomber le banc de `.github/scripts` sur LUI-MEME : un garde qui
    # porte `if __name__` dans une CHAINE avant de le porter pour de vrai. Un motif prenait la
    # premiere occurrence, la neutralisation atterrissait dans le litteral, le garde tournait
    # normalement, et ce banc le declarait « decoratif ». Vu ROUGE avant la lecture par l arbre.
    piege = (
        'MODELE = """\nif __name__ == "__main__":\n    print("un modele")\n"""\n\n\n'
        "def detecte():\n    return [1]\n\n\n"
        'if __name__ == "__main__":\n    detecte()\n'
    )
    verifie(
        "un point d entree cache dans une chaine ne trompe pas",
        ligne_du_point_d_entree(piege),
        11,
    )
    verifie(
        "et la neutralisation se pose APRES le litteral",
        mute(piege).index("_t_mutation") > mute(piege).index('MODELE = """'),
        True,
    )

    # #4788. Un garde sans point d entree REFUSE, au lieu d etre signale sous un vert.
    verifie("rien a signaler passe", code_de_sortie([], []), 0)
    verifie("un garde decoratif refuse", code_de_sortie(["x"], []), 1)
    verifie("un garde SANS POINT D ENTREE refuse aussi", code_de_sortie([], ["y"]), 1)
    verifie("et les deux ensemble refusent", code_de_sortie(["x"], ["y"]), 1)
    return echecs()


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
    decoratifs, illisibles, non_concluants = suspects()
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
    for l in non_concluants:
        print(f"NON CONCLUANT : {l}", file=sys.stderr)
    if non_concluants:
        print(
            "\nUn garde qui PLANTE sous mutation n'a rien prouvé : il est mort avant sa première\n"
            "assertion, et un rouge pour la mauvaise raison ne prouve rien (ADR 4918). Ces gardes\n"
            "ne font pas refuser, parce que refuser dessus reviendrait à refuser sur ce que ce banc\n"
            "n'a pas su lire. Ils se comptent à part, et les rendre mutables est un travail en soi.",
            file=sys.stderr,
        )
    if not code_de_sortie(decoratifs, illisibles):
        tenus = len(corpus()) - len(non_concluants) - len(illisibles)
        print(
            f"{tenus} garde(s) de méthode rougissent sous mutation, "
            f"{len(non_concluants)} ne concluent pas, {len(illisibles)} sont illisibles : "
            f"soit {tenus + len(non_concluants) + len(illisibles)} sur {len(corpus())}."
        )
    raise SystemExit(code_de_sortie(decoratifs, illisibles))
