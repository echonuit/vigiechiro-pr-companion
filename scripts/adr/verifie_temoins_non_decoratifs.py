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

import pathlib
import re
import contextlib
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

ADR = "4490"
DOSSIER = pathlib.Path(__file__).resolve().parent
SUITE = DOSSIER / "verifie_scripts.py"
CHARGE = re.compile(r'_charge\("([^"]+)"\)')

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


def gardes() -> list[str]:
    """Les gardes que la suite charge reellement."""
    return sorted(set(CHARGE.findall(SUITE.read_text(encoding="utf-8"))))


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
        )
    finally:
        cible.write_text(original, encoding="utf-8")
    return rendu.returncode != 0


def suspects(noms: list[str] | None = None) -> list[str]:
    """Un suspect par garde dont la suite reste verte alors qu il ne detecte plus rien."""
    trouves = []
    with arbre_jetable() as faux:
        for nom in noms if noms is not None else gardes():
            if nom in HORS_PORTEE or not (DOSSIER / nom).is_file():
                continue
            if not suite_rougit(nom, faux):
                trouves.append(f"{nom}  la suite reste verte, sa detection neutralisee")
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
    with arbre_jetable() as faux:
        # 2. Le sens POSITIF : un garde dont le temoin tient fait bien rougir la suite une fois mute.
        #    `2843-tiret-cadratin.py` sert de reference : son temoin compte des cadratins.
        verifie("un garde au temoin solide fait rougir la suite sous mutation",
                suite_rougit("2843-tiret-cadratin.py", faux), True)
        # 3. Le sens NEGATIF : sans mutation, la suite est verte. Sans ce cas, un script qui rendrait
        #    TOUJOURS `True` passerait le cas precedent et n aurait rien prouve.
        rendu = subprocess.run(
            [sys.executable, str(faux / "scripts" / "adr" / "verifie_scripts.py")],
            capture_output=True, cwd=faux)
        verifie("sans mutation, la suite est verte", rendu.returncode, 0)
        # 4. Ce que ce lot prouve (#4700) : muter n a PAS touche le depot. Sans ce cas, une
        #    reecriture qui reviendrait a muter la source passerait les trois precedents.
        vraie = (DOSSIER / "2843-tiret-cadratin.py").read_text(encoding="utf-8")
        verifie("la mutation n a pas touche le depot", NEUTRALISATION.strip() in vraie, False)
        # 5. Et l arbre jetable est bien un AUTRE arbre, sinon le quatrieme cas ne prouve rien.
        verifie("l arbre mute n est pas le depot", faux.resolve() == DOSSIER.parents[1].resolve(), False)
        # 6. Il porte pourtant de quoi juger : `src/` est lie, donc lisible.
        verifie("l arbre jetable voit les sources du depot", (faux / "src").exists(), True)
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    sys.exit(rapporte(ADR, "temoin decoratif : la suite reste verte sans detection", suspects()))
