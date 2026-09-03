#!/usr/bin/env python3
"""Aucun temoin de `verifie_scripts.py` n est decoratif (#4490, article A2).

`verifie_scripts.py` clot sur « les N scripts charges DETECTENT leur violation temoin ». La phrase
affirme plus que ce que la suite verifie : un temoin peut exister, s executer, passer, et ne rien
tenir. Celui du cliquet de longueur d ADR n affirmait que `isinstance(suspects(), list)`, si bien
qu un garde ayant cesse de detecter le passait (#4487). C etait le faux vert que le depot refuse
partout ailleurs, installe dans le dispositif meme qui le refuse.

**Le geste.** Pour chaque garde, neutraliser ses fonctions de detection, relancer la suite, et
exiger qu elle rougisse. C est l article A2 rendu mecanique : un garde est vu rouge sur sa propre
mutation, et la mutation se refait apres toute reecriture plutot qu a la prochaine cloture.

**La liste se DERIVE, elle ne s ecrit pas.** Les gardes viennent des appels `_charge("...")` de
`verifie_scripts.py`, et non d un glob. Un glob vieillit, et un garde neuf passerait au travers :
c est exactement le defaut que ce script existe pour attraper, et il serait cocasse de l y poser.

**Le sens de la panne est le bon.** Si la neutralisation cessait de fonctionner, le garde
continuerait de detecter, la suite resterait verte, et ce script crierait « temoin decoratif » a
tort. Un faux positif est bruyant ; c est le silence qu il fallait eviter.

**La cecite declaree.** La mutation ne remplace que les fonctions de MODULE non prefixees. Un temoin
qui n eprouverait qu une constante, une expression reguliere ou une classe survit sans etre
decoratif pour autant : ce script ne prononce donc rien sur ceux-la, et la liste des exemptions le
dit une par une.
"""

import ast
import contextlib
import pathlib
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte, sort_si_contrat_demande

ADR = "4490"
DOSSIER = pathlib.Path(__file__).resolve().parent
# Ce garde LANCE les scripts qu il eprouve : il ne se prend jamais lui-meme pour cible, sous peine
# de se rappeler sans fin. Meme barriere que `verifie_contrats_tiennent.py` (#5119).
MOI = pathlib.Path(__file__).name
SUITE = DOSSIER / "verifie_scripts.py"

# Ce qu on ajoute a la fin d un garde pour lui retirer sa detection, sans toucher a ce qui le decrit.
# `rapporte` est epargne : il vient de `_commun` et sert a rendre le verdict, il ne detecte rien.
NEUTRALISATION = """

import types as _t_mutation
for _nom_mutation, _val_mutation in list(globals().items()):
    if (isinstance(_val_mutation, _t_mutation.FunctionType)
            and not _nom_mutation.startswith("_")
            and _nom_mutation != "rapporte"):
        globals()[_nom_mutation] = (lambda *a, **k: [])
"""

# Les temoins qui n eprouvent AUCUNE fonction de module, et que la mutation ne peut donc pas tuer.
# Chacun est nomme avec ce qu il eprouve reellement, sinon cette liste deviendrait le tapis sous
# lequel on pousse les temoins faibles.
HORS_PORTEE = {
    "resserre_cliquets.py": "eprouve une expression reguliere et la PRESENCE d une fonction, pas son effet",
}


def charges(source: str) -> list[str]:
    """Les gardes qu une suite charge, litteral OU constante (issue #5134).

    Le motif precedent ne lisait que la forme LITTERALE de `_charge`. Le harnais ecrit aussi
    `_charge(ADR_2843)`, et ce garde ne voyait donc pas `2843-tiret-cadratin.py` : il le laissait
    hors de sa population SANS LE DIRE, et son compte paraissait sain.

    **C est le defaut que ce garde existe pour combattre, dans le garde lui-meme.** Un idiome n est
    pas une capacite, lecon de #5032, #5103, #5108 et #5128. La liaison se resout par l AST.
    """
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        return []
    # Les constantes de MODULE seules : ce qui est indente appartient a un cas, et un cas peut
    # nommer un garde qu il ne charge pas.
    const = {
        cible.id: noeud.value.value
        for noeud in arbre.body
        if isinstance(noeud, ast.Assign)
        and isinstance(noeud.value, ast.Constant)
        and isinstance(noeud.value.value, str)
        for cible in noeud.targets
        if isinstance(cible, ast.Name)
    }
    trouves = set()
    for noeud in ast.walk(arbre):
        if not (isinstance(noeud, ast.Call) and isinstance(noeud.func, ast.Name)):
            continue
        if noeud.func.id != "_charge" or not noeud.args:
            continue
        argument = noeud.args[0]
        if isinstance(argument, ast.Constant) and isinstance(argument.value, str):
            trouves.add(argument.value)
        elif isinstance(argument, ast.Name) and argument.id in const:
            trouves.add(const[argument.id])
    return sorted(trouves)


def gardes() -> list[str]:
    """Les gardes que la suite charge reellement."""
    return charges(SUITE.read_text(encoding="utf-8"))


@contextlib.contextmanager
def arbre_jetable():
    """Un depot ou muter sans toucher a celui-ci (#4700, #4686).

    `scripts/` est COPIE, tout le reste est lie. Les gardes lisent `src/` et `dev-docs/` sans
    jamais y ecrire - mesure faite sur les 36 gardes, leurs seules ecritures allant dans un
    `TemporaryDirectory` a eux. Et tous resolvent leurs chemins depuis leur propre emplacement,
    par `parents[1]` ou `parents[2]`, donc l arbre copie leur suffit.

    Le poids decide de la forme : `scripts/` pese 1,1 Mo quand le depot en fait 1,4 Go hors
    `target/` et `.git`. Copier le tout serait impensable ; copier `scripts/` ne coute rien, et se
    fait UNE fois pour toutes les mutations.

    **`.git` n est PAS lie, et la mesure le justifie.** Le harnais eprouve chaque garde sur un
    temoin synthetique dans un `TemporaryDirectory` a lui, jamais sur le corpus versionne : son
    verdict ne depend donc pas de git. Mesure du 2026-08-29, harnais lance dans les deux arbres :

        .git absent :  18,2 s, code 0, 104 lignes
        .git lie    : 229,7 s, code 0, 104 lignes   -- sorties IDENTIQUES

    Douze fois plus vite pour le meme verdict. Ce n est donc pas un garde qui regarde moins ; c est
    un garde qui ne paie plus un acces dont il n a pas l usage.

    **C est le remede qui supprime la classe, pas ses instances.** Muter la source laissait des
    gardes neutralisees des qu un signal coupait le processus : le `finally` couvre l exception,
    pas le `SIGTERM`. Cinq gardes ont ete retrouves ainsi en deux jours, sans que rien ne le dise.
    """
    racine = DOSSIER.parents[1]
    with tempfile.TemporaryDirectory(prefix="vc-temoins-") as tmp:
        faux = pathlib.Path(tmp) / "depot"
        faux.mkdir()
        shutil.copytree(racine / "scripts", faux / "scripts", symlinks=True)
        for entree in racine.iterdir():
            if entree.name not in {"scripts", ".git"}:
                (faux / entree.name).symlink_to(entree)
        yield faux


def suite_rougit(nom: str, faux: pathlib.Path) -> bool:
    """La suite rougit-elle quand ce garde perd sa detection ?

    La mutation porte sur l arbre JETABLE. Interrompre ce script ne peut donc plus laisser un
    garde neutralise dans le depot : il n y a jamais rien eu a restaurer.
    """
    cible = faux / "scripts" / "adr" / nom
    original = cible.read_text(encoding="utf-8")
    try:
        cible.write_text(original + NEUTRALISATION, encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(faux / "scripts" / "adr" / "verifie_scripts.py")],
            capture_output=True,
            cwd=faux,
            check=False,
        )
    finally:
        cible.write_text(original, encoding="utf-8")
    return rendu.returncode != 0


MARQUE_MAIN = 'if __name__ == "__main__":'


def porte_son_auto_test(nom: str) -> bool:
    """Ce garde dispatche-t-il `--auto-test` dans son CODE, et non dans sa seule prose ?

    Barriere de SURETE : lancer un script qui ne porte pas cette branche ne coute pas du temps, il
    IGNORE l argument et FAIT SON TRAVAIL. Un generateur reecrirait des fichiers.
    """
    try:
        arbre = ast.parse((DOSSIER / nom).read_text(encoding="utf-8"))
    except (SyntaxError, OSError):
        return False
    docstrings = set()
    for noeud in ast.walk(arbre):
        corps = getattr(noeud, "body", None)
        porteur = (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)
        if isinstance(noeud, porteur) and corps and isinstance(corps[0], ast.Expr):
            tete = corps[0].value
            if isinstance(tete, ast.Constant) and isinstance(tete.value, str):
                docstrings.add(id(tete))
    return any(
        isinstance(n, ast.Constant)
        and isinstance(n.value, str)
        and "--auto-test" in n.value
        and id(n) not in docstrings
        for n in ast.walk(arbre)
    )


def neutralise_avant_main(source: str) -> str | None:
    """La neutralisation posee AVANT le bloc `__main__`, ou rien si le fichier n en a pas.

    **C est toute la difference entre les deux moities, et elle a failli rendre huit faux suspects.**
    `suite_rougit` mute un garde que le harnais CHARGE comme module : `__main__` est saute, le code
    ajoute en fin de fichier s execute, les fonctions sont remplacees. Lance en SCRIPT, le meme
    fichier fait l inverse : le bloc `__main__` s execute d abord et `sys.exit` part avant que la fin
    du fichier ne soit atteinte. La neutralisation en queue ne s applique alors JAMAIS, et l auto-test
    passe en voyant les vraies fonctions - ce qui se lit « ce temoin ne prouve rien » alors qu il n a
    rien eu a prouver. Verifie sur un fichier temoin de six lignes avant d etre corrige (#5134).
    """
    if MARQUE_MAIN not in source:
        return None
    tete, _, queue = source.partition(MARQUE_MAIN)
    return tete + NEUTRALISATION + "\n" + MARQUE_MAIN + queue


def auto_test_rougit(nom: str, faux: pathlib.Path) -> bool:
    """L auto-test PROPRE de ce garde rougit-il quand sa detection est neutralisee ?

    L autre moitie du dispositif (issue #5134). `suite_rougit` eprouve les gardes dont le temoin vit
    dans le harnais ; celui-ci eprouve ceux qui portent leur propre `--auto-test`, que rien ne mutait.
    """
    cible = faux / "scripts" / "adr" / nom
    original = cible.read_text(encoding="utf-8")
    mute = neutralise_avant_main(original)
    if mute is None:
        return False
    try:
        cible.write_text(mute, encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(cible), "--auto-test"],
            capture_output=True,
            cwd=faux,
            check=False,
            timeout=300,
        )
    except subprocess.TimeoutExpired:
        return False
    finally:
        cible.write_text(original, encoding="utf-8")
    return rendu.returncode != 0


def autonomes(noms: list[str] | None = None) -> list[str]:
    """Les points d entree que le harnais ne charge PAS, et qui portent leur propre `--auto-test`.

    Population DERIVEE et non enumeree : un garde neuf y entre tout seul. C est la lecon que
    `gardes()` avait apprise avant d etre elle-meme prise en defaut par une constante (#5134).
    """
    if noms is not None:
        return [n for n in noms if n not in HORS_PORTEE and porte_son_auto_test(n)]
    par_le_harnais = set(gardes())
    return sorted(
        f.name
        for f in DOSSIER.glob("*.py")
        if not f.name.startswith("_")
        and f.name not in par_le_harnais
        and f.name not in HORS_PORTEE
        and f.name != MOI
        and porte_son_auto_test(f.name)
    )


def mutes(noms: list[str] | None = None) -> list[str]:
    """Les gardes que ce garde MUTE reellement, extraits pour que `lus` les compte (issue #5007).

    L unite n est ni le fichier ni la ligne : c est le GARDE. Et c est bien le garde MUTE, non
    celui que la suite charge : `HORS_PORTEE` et les absents ne sont jamais neutralises, donc
    jamais lus. Les compter gonflerait le nombre d une population que ce garde n eprouve pas.
    """
    return [
        nom
        for nom in (noms if noms is not None else gardes())
        if nom not in HORS_PORTEE and (DOSSIER / nom).is_file()
    ]


def suspects(noms: list[str] | None = None) -> list[str]:
    """Un suspect par garde dont la suite reste verte alors qu il ne detecte plus rien."""
    trouves = []
    with arbre_jetable() as faux:
        for nom in mutes(noms):
            if not suite_rougit(nom, faux):
                trouves.append(f"{nom}  la suite reste verte, sa detection neutralisee")
        # L autre moitie : les gardes que le HARNAIS ne charge pas, dont le temoin est leur propre
        # `--auto-test`. Sans ce passage, ce temoin n etait verifie qu EXISTANT (#5134).
        for nom in autonomes(noms):
            if not auto_test_rougit(nom, faux):
                trouves.append(f"{nom}  son --auto-test reste vert, sa detection neutralisee")
    return trouves


def auto_test() -> int:
    """Le mecanisme se prouve dans les DEUX sens, sinon il ne prouve rien.

    Un script qui ne saurait que dire « tout va bien » passerait le premier sens tout seul.
    """
    echecs = 0

    def verifie(libelle: str, obtenu, attendu) -> None:
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test du garde des temoins non decoratifs (#4490) :")
    # 1. La liste se derive de la suite, et elle n est pas vide.
    verifie("la liste des gardes vient des appels `_charge` de la suite", len(gardes()) > 15, True)
    verifie("elle ne contient pas la suite elle-meme", SUITE.name in gardes(), False)

    # --- LA SECONDE MOITIE (issue #5134) ---
    #
    # La liaison de `_charge` se resout, litteral ET constante. Sans cela `2843-tiret-cadratin.py`
    # restait hors population sans que rien ne le dise : le garde qui refuse les temoins decoratifs
    # etait lui-meme aveugle, pour la raison qu il existe pour combattre.
    verifie(
        "un _charge par CONSTANTE est vu comme un litteral",
        charges('ADR_X = "g.py"\n_charge(ADR_X)\n'),
        ["g.py"],
    )
    verifie("et le litteral l est toujours", charges('_charge("h.py")\n'), ["h.py"])
    verifie("2843 est desormais dans la population", "2843-tiret-cadratin.py" in gardes(), True)

    # La NEUTRALISATION se pose AVANT le bloc `__main__`, sinon elle ne s applique jamais quand le
    # fichier est lance en SCRIPT : `sys.exit` part avant la fin du fichier. La version qui ajoutait
    # en queue rendait huit faux suspects, et son erreur ressemblait a une trouvaille.
    avec_main = 'def f():\n    return [1]\n\n\nif __name__ == "__main__":\n    print(f())\n'
    mute = neutralise_avant_main(avec_main)
    verifie(
        "la neutralisation precede le bloc __main__",
        mute.index("_t_mutation") < mute.index(MARQUE_MAIN),
        True,
    )
    verifie(
        "un fichier sans bloc __main__ ne se mute pas",
        neutralise_avant_main("def f():\n    return []\n"),
        None,
    )

    # La population des autonomes se DERIVE, et ne recoupe pas celle du harnais.
    verifie("ce garde ne s eprouve jamais lui-meme", MOI in autonomes(), False)
    verifie("aucun garde n est compte deux fois", set(autonomes()) & set(gardes()), set())
    verifie("une mention en prose ne vaut pas dispatch", porte_son_auto_test("_commun.py"), False)
    with arbre_jetable() as faux:
        # 2. Le sens POSITIF : un garde dont le temoin tient fait bien rougir la suite une fois mute.
        #    `2843-tiret-cadratin.py` sert de reference : son temoin compte des cadratins.
        verifie(
            "un garde au temoin solide fait rougir la suite sous mutation",
            suite_rougit("2843-tiret-cadratin.py", faux),
            True,
        )
        # 3. Le sens NEGATIF : sans mutation, la suite est verte. Sans ce cas, un script qui rendrait
        #    TOUJOURS `True` passerait le cas precedent et n aurait rien prouve.
        rendu = subprocess.run(
            [sys.executable, str(faux / "scripts" / "adr" / "verifie_scripts.py")],
            capture_output=True,
            cwd=faux,
            check=False,
        )
        verifie("sans mutation, la suite est verte", rendu.returncode, 0)
        # 4. Ce que ce lot prouve (#4700) : muter n a PAS touche le depot. Sans ce cas, une
        #    reecriture qui reviendrait a muter la source passerait les trois precedents.
        vraie = (DOSSIER / "2843-tiret-cadratin.py").read_text(encoding="utf-8")
        verifie("la mutation n a pas touche le depot", NEUTRALISATION.strip() in vraie, False)
        # 5. Et l arbre jetable est bien un AUTRE arbre, sinon le quatrieme cas ne prouve rien.
        verifie(
            "l arbre mute n est pas le depot", faux.resolve() == DOSSIER.parents[1].resolve(), False
        )
        # 6. Il porte pourtant de quoi juger : `src/` est lie, donc lisible.
        verifie("l arbre jetable voit les sources du depot", (faux / "src").exists(), True)
        # 7. La seconde moitie, dans les DEUX sens elle aussi. `verifie_corpus_declare.py` porte
        #    son propre `--auto-test` et n est pas charge par le harnais : c est un des huit que
        #    rien n eprouvait avant #5134.
        verifie(
            "un auto-test propre qui tient fait rougir sous mutation",
            auto_test_rougit("verifie_corpus_declare.py", faux),
            True,
        )
        # 8. Et le sens negatif : sans mutation, ce meme auto-test conclut.
        sain = subprocess.run(
            [
                sys.executable,
                str(faux / "scripts" / "adr" / "verifie_corpus_declare.py"),
                "--auto-test",
            ],
            capture_output=True,
            cwd=faux,
            check=False,
        )
        verifie("sans mutation, son auto-test est vert", sain.returncode, 0)
    return echecs


CONTRAT = {
    "geste": "temoin decoratif : rien ne rougit quand la detection est neutralisee",
    "population": "les gardes que verifie_scripts.py charge, ET ceux qui portent leur propre "
    "--auto-test sans etre charges par lui",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_temoins_non_decoratifs.py --auto-test",
    "decision": "ADR 4490",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    sys.exit(
        rapporte(
            ADR,
            "temoin decoratif : la suite reste verte sans detection",
            suspects(),
            # Les DEUX populations : n en compter qu une ferait mentir `lus` de huit unites,
            # ce que l ADR 5007 refuse.
            lus=len(mutes()) + len(autonomes()),
        )
    )
