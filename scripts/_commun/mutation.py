"""Ce qu une neutralisation epargne, et pourquoi elle ne peut pas le deduire d un prefixe (#5524).

Les trois bancs de mutation du depot - ADR, methode, CI - neutralisent les fonctions de module du
garde qu ils eprouvent, puis relancent son auto-test : s il reste vert, le temoin est decoratif.

Tous trois epargnaient la MACHINERIE de l auto-test, et c est juste : neutraliser un `_auto_test`
le fait echouer trivialement au lieu de prouver qu il a cesse de detecter. Mais tous trois la
reconnaissaient a son NOM, et un nom ne porte pas cette reponse :

- le banc ADR epargnait tout nom commencant par un souligne. Une detection nommee `_completude`,
  `_forge` ou `_decoupe` par convention d interne devenait inatteignable ;
- les bancs de methode et de CI epargnaient tout nom portant « auto » et « test ». Ce motif epargne
  aussi `porte_son_auto_test`, `auto_test_rougit`, `porte_un_auto_test` et `autotestes`, qui sont la
  DETECTION des gardes qui les portent.

Mesure du 2026-09-28 sur les trois corpus : six gardes portaient une detection nommee avec un
souligne, pour dix-neuf fonctions. Mutees une a une, **sept** faisaient rougir leur auto-test -
sept couvertures reelles que le prefixe cachait - et **six** survivaient, revelant des auto-tests
qui n eprouvent pas ce qu ils semblent eprouver. Quatre detections de plus etaient epargnees par le
motif des deux autres bancs.

## Ce qui remplace le nom

La question n est pas « comment ce nom s ecrit-il » mais « a quoi cette fonction sert-elle ». Elle se
tranche sur le GRAPHE D APPEL : une fonction atteignable depuis la racine du verdict reel participe au
jugement, donc c est de la detection ; une fonction que seul le point d entree d auto-test atteint ne
sert qu a mettre le test en scene, donc c est de la machinerie.

C est la lecon de #5530 appliquee aux bancs eux-memes : une population se derive de ce que la chose
FAIT, jamais de la facon dont elle est ecrite. Un garde qui renommerait ses fonctions demain serait
juge pareil.
"""

from __future__ import annotations

import ast
from collections.abc import Iterable

# Les noms sous lesquels un garde de ce depot expose son auto-test, par NOM EXACT. Ils s epargnent
# comme points d ENTREE, au meme titre que `main` : les detruire fait sortir le garde sans qu un cas
# ait joue, et c est le rouge muet que #5499 a ferme.
#
# Par nom exact et non par motif, precisement parce que le motif est ce que ce module corrige :
# mesure du 2026-09-28, 103 des 111 fonctions des trois corpus portant « auto » et « test » sont
# l une de ces quatre ; les huit autres sont soit de la machinerie derivable, soit de la DETECTION.
ENTREES_D_AUTO_TEST = ("auto_test", "_auto_test", "_autoTest", "autoTest")

# La fabrique d auto-test que les trois bancs partagent. Elle n est pas definie dans le garde mute,
# elle y est IMPORTEE - mais un nom importe est une `FunctionType` du module, donc la neutralisation
# l atteint comme les autres, et `verifie, echecs = cas_d_auto_test()` rend alors `[]` : le garde
# meurt sur un depaquetage avant sa premiere assertion.
#
# Les bancs de methode et de CI l epargnaient SANS LE SAVOIR, par leur motif « auto » + « test ».
# Retirer le motif sans la nommer a fait passer le banc de methode de 9 a 16 non concluants, tous
# sur ce depaquetage. Mesure du 2026-09-28, et c est la raison pour laquelle elle est ici plutot que
# dans la liste d un seul banc.
FABRIQUE_PARTAGEE = ("cas_d_auto_test",)


def _defs(arbre: ast.Module) -> dict[str, ast.FunctionDef | ast.AsyncFunctionDef]:
    return {n.name: n for n in arbre.body if isinstance(n, ast.FunctionDef | ast.AsyncFunctionDef)}


def machinerie(source: str) -> tuple[str, ...]:
    """Les fonctions que SEUL le point d entree d auto-test atteint, derivees du graphe d appel."""
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        # Un fichier qui ne s analyse pas ne s execute pas davantage : la mutation est sans objet.
        return ()
    defs = _defs(arbre)

    def appelees(noeud: ast.AST) -> set[str]:
        # Un nom CHARGE suffit, l appel n est pas requis : `cas.append((libelle, _detecte))` passe la
        # fonction en valeur, et ne la chercher que sous un `Call` la manquerait.
        return {
            n.id for n in ast.walk(noeud) if isinstance(n, ast.Name) and isinstance(n.ctx, ast.Load)
        } & set(defs)

    graphe = {nom: appelees(n) for nom, n in defs.items()}

    def atteignables(racines: Iterable[str], sans: frozenset[str] = frozenset()) -> set[str]:
        """Le parcours s ARRETE sur `sans`, et c est ce qui rend la question decidable.

        Presque tous les gardes dispatchent leur auto-test DEPUIS `main` : `if "--auto-test" in
        sys.argv: return _auto_test()`. Sans cette coupe, tout le chemin de l auto-test est
        atteignable depuis le verdict reel, la soustraction rend l ensemble vide, et la derivation
        epargne zero fonction. Mesure : sur 51 gardes du corpus ADR, 30 portent une machinerie
        reelle, et aucune n etait vue avant la coupe.
        """
        vus: set[str] = set()
        pile = [r for r in racines if r in defs and r not in sans]
        while pile:
            courant = pile.pop()
            if courant not in vus:
                vus.add(courant)
                pile.extend(f for f in graphe.get(courant, ()) if f not in sans)
        return vus

    entrees = frozenset(d for d in defs if d in ENTREES_D_AUTO_TEST)
    # ⟨la racine du verdict reel n est pas seulement `main`⟩ Un harnais comme `verifie_scripts.py`
    # n en a AUCUN : son verdict sort du bloc `if __name__ == "__main__":`, qui appelle ses cas. Ne
    # prendre que `main` classait sa detection `_completude` en machinerie, donc l epargnait - le
    # defaut de #5524 reconduit sous une autre forme.
    hors_defs = [n for n in arbre.body if not isinstance(n, ast.FunctionDef | ast.AsyncFunctionDef)]
    racines = {"main"} | {nom for n in hors_defs for nom in appelees(n)}
    return tuple(sorted(atteignables(entrees) - atteignables(racines - entrees, sans=entrees)))


def epargnes_de(source: str, de_plus: Iterable[str] = (), *, prefixe: str = "_") -> tuple[str, ...]:
    """Ce que la neutralisation de CE garde doit laisser intact.

    ## Pourquoi le nom reste une condition NECESSAIRE

    Un garde dont le seul point d entree est son auto-test n a aucune racine de verdict : tout ce que
    l auto-test appelle y parait de la machinerie, detection comprise. Deux cas fabriques du banc ADR
    ont cette forme, et la derivation seule les classait « decoratif » a tort.

    L ancienne regle de nom est donc conservee comme FILTRE, et la derivation ne fait que la
    restreindre. Le changement est ainsi **monotone** : on epargne un sous-ensemble strict de ce
    qu on epargnait, donc aucun garde ne peut devenir decoratif a cause de ce module.
    """
    # ⟨la derivee passe AUSSI par le filtre de nom⟩ Sans ce filtre, une detection non prefixee que
    # seul l auto-test atteint - la forme exacte des deux cas fabriques du banc ADR - serait epargnee
    # alors que l ancienne regle la mutait. Le changement cesserait d etre monotone, et un temoin
    # decoratif passerait pour tenant. Mesure : trois cas du banc de methode sont tombes sur ce
    # defaut avant qu il soit corrige.
    derivee = {m for m in machinerie(source) if m.startswith(prefixe)}
    return tuple(sorted(set(de_plus) | set(ENTREES_D_AUTO_TEST) | set(FABRIQUE_PARTAGEE) | derivee))


def neutralisation(source: str, de_plus: Iterable[str] = (), *, prefixe: str = "_") -> str:
    """Le code qui neutralise les detections de CE garde et epargne SA machinerie.

    La liste depend du fichier, donc ce n est plus une constante : c est le prix de la derivation, et
    c est exactement ce qui permet aux bancs de cesser de deviner.

    `prefixe` est le filtre de nom conserve, pour la raison dite dans `epargnes_de`. Les noms qui le
    portent sans etre de la machinerie sont nommes un a un dans `mutables` : une condition en
    comprehension ne saurait pas les distinguer une fois le fichier charge.
    """
    epargnes = epargnes_de(source, de_plus, prefixe=prefixe)
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        portant_le_prefixe: set[str] = set()
    else:
        portant_le_prefixe = {n for n in _defs(arbre) if n.startswith(prefixe)}
    mutables = tuple(sorted(portant_le_prefixe - set(epargnes)))
    return f"""

import types as _t_mutation
for _nom_mutation, _val_mutation in list(globals().items()):
    if (isinstance(_val_mutation, _t_mutation.FunctionType)
            and not (_nom_mutation.startswith({prefixe!r})
                     and _nom_mutation not in {mutables!r})
            and _nom_mutation not in {epargnes!r}):
        globals()[_nom_mutation] = (lambda *a, **k: [])
"""
