"""Le squelette des outils qui REPONDENT depuis un index : `--index`, les cibles, et le REFUS.

Ce module existe parce que le code livre s y etait engage. La docstring de `implemente.py` ecrivait,
le 2026-09-29 : « Le squelette se ressemble, et c est assume tant qu ils sont deux ; au troisieme, il
se factorise ». Le troisieme est `lecteurs.py`, et une promesse tenue par personne serait pire que
pas de promesse : elle laisserait le depot affirmer une regle qu il ne suit pas.

## Ce que le partage REVELE, et qui est le vrai gain

Les trois outils doivent refuser pareil sur un index absent. Tant que chacun portait son `main`, cet
accord etait une coincidence que seul un cas croise surveillait, et ce cas vivait dans `champs.py`
sans pouvoir dire QUOI avait diverge. Ici l accord est **structurel** : il n y a qu un chemin de
refus, donc il n y a rien a faire diverger.

C est la lecon de #5567 dans l autre sens. La-bas, deux loupes partageaient un plafond et se
conduisaient differemment, et le partage a REVELE la divergence plutot que de la causer. Ici le
partage la rend impossible.

## Ce qu il ne prend PAS en charge, et pourquoi le BRANCHEMENT reste dehors

L usage aussi est passe par l appelant plutot que fabrique ici, et pour une raison mesuree :
`verifie_inventaires_ci.py` compte comme garde tout fichier ou `--auto-test` figure hors commentaire,
ce qui incluait le message d usage de ce module et exigeait sa ligne au tableau des gardes. Il aurait
fallu y declarer un garde qui n en est pas un. Chaque outil porte donc son usage, ce qui est de toute
facon juste : ses options sont les siennes.

L `--auto-test` reste chez chaque outil, et pas seulement ses cas : le `if` qui l appelle aussi.

La premiere ecriture de ce module le portait, et une mutation l a refuse. Remplacer
`return auto_test()` par `return 0` ici laissait les TROIS outils verts en n executant aucun cas, et
rien ne l aurait vu : la porte comme `lint.yml` ne lisent que le code de sortie. Un branchement chez
chaque outil ne concentre pas ce risque - une mutation n en eteint qu un, ce qui est l exposition qui
existait avant ce module.

Le partage a donc une frontiere, et elle se lit ainsi : ce qui gagne a etre commun est ce dont la
DIVERGENCE est le defaut, comme le refus. Ce qui doit rester local est ce dont la PANNE est
silencieuse.
"""

from __future__ import annotations

import io
import json
import pathlib
import sys
import tempfile
from collections.abc import Callable

from _commun import PLAFOND_RENDU
from _commun.index import IndexAbsent

# Re-exporte pour que les trois outils l importent d ICI, avec le reste de leur squelette. Le
# plafond lui-meme vit dans `_commun`, partage avec `rapport_mutation.py` depuis la passe 7 de #5553.
PLAFOND = PLAFOND_RENDU


def joue(
    argv: list[str],
    *,
    usage: tuple[str, ...],
    charge: Callable[[pathlib.Path | None], dict[str, list[str]]],
    repond: Callable[[str, dict[str, list[str]]], str],
) -> int:
    """Le `main` des trois outils. Rend 0, 1 sur un index absent, 2 sans cible.

    Le REFUS est le point de cette fonction, et il vaut pour les trois. Un index absent rendrait un
    dictionnaire vide, donc « aucun appelant », « aucun porteur » et « aucun lecteur » selon l outil
    - le verdict le plus rassurant sur chaque question posee, et le seul faux negatif que rien
    d autre ne rattraperait.
    """
    ou = None
    if "--index" in argv:
        ou = pathlib.Path(argv[argv.index("--index") + 1])

    passes = {"--markdown", "--index", "" if ou is None else str(ou)}
    cibles = [a for a in argv[1:] if not a.startswith("--") and a not in passes]
    if not cibles:
        for ligne in usage:
            print(ligne, file=sys.stderr)
        return 2

    try:
        index = charge(ou)
    except IndexAbsent as refus:
        print(refus, file=sys.stderr)
        return 1

    for cible in cibles:
        print(repond(cible, index))
    return 0


def sans_bruit(main: Callable[[list[str]], int], argv: list[str]) -> int:
    """`main` sans sa sortie, pour que la reponse du cas positif ne se lise pas comme des cas.

    Un auto-test dont la trace porte les lignes de l outil lui-meme se compte mal : un releve qui
    cherche les cas joues y voit la reponse, et le nombre annonce cesse d etre le nombre de cas.
    """
    vraie = sys.stdout
    try:
        sys.stdout = io.StringIO()
        return main(argv)
    finally:
        sys.stdout = vraie


def message_du_refus(charge: Callable[[pathlib.Path], dict[str, list[str]]], chemin) -> str:
    """Le texte du refus, pour qu un cas puisse verifier qu il dit comment produire l index."""
    try:
        charge(chemin)
    except IndexAbsent as refus:
        return str(refus)
    return ""


def index_temoin(faux: dict[str, list[str]], nom: str) -> pathlib.Path:
    """Un index ecrit sur disque, pour que le cas positif emprunte le MEME chemin que le refus.

    Sans lui, le cas de refus serait le seul a passer par `main`, et rien ne dirait si l outil refuse
    parce que l index manque ou parce qu il refuse toujours. C est le controle de contraste, et les
    trois outils le doivent a une mutation qui avait laisse leurs premiers temoins verts.
    """
    ou = pathlib.Path(tempfile.gettempdir()) / f"{nom}-auto-test-index.json"
    ou.write_text(json.dumps(faux), encoding="utf-8")
    return ou
