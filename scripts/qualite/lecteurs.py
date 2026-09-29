#!/usr/bin/env python3
"""Qui lit ce champ hors de sa classe, quand le depot en declare 8 933.

`arbre.py` voit qu une classe declare un champ. Il ne voit pas **qui le lit** ailleurs, parce qu il
lit un fichier a la fois. PMD juge `UnusedPrivateField` dans la seule classe qui le declare. La
question « qui lit ce champ » est la troisieme des trois que #5464 ecrivait dans sa frontiere, et la
derniere a n avoir aucun repondant.

Mesure du 2026-09-29, sur les 8 933 champs indexes :

    grep -rn service --include=*.java src/   ->  3 398 lignes a trier
    cet outil                                ->  168 declarations, dont 24 lues d ailleurs

Le gain n est pas le compte, c est la RESOLUTION. Un `grep` par nom de champ ne distingue pas le champ
de la variable locale, du parametre ni de la methode homonymes, et il rate le champ herite lu sans se
nommer. L index resout chaque acces jusqu a sa declaration.

**Et le taux d homonymes rend la resolution necessaire** : 995 des 3 730 noms de champ du depot sont
declares dans plusieurs classes, les pires massivement - `service` dans 168, `dossier` dans 131,
`ID_USER` dans 105. Devant eux, un `grep` par nom ne rend pas une reponse, il rend une liste de
fichiers a ouvrir.

## Ce qu il ne dit PAS, et le contresens que ce fichier existe pour ne pas commettre

« Aucun lecteur hors de sa classe » n est PAS un champ mort, et c est sur ce troisieme index que la
confusion couterait le plus cher : **8 195 champs sur 8 933 sont dans ce cas, soit 92 % du corpus**,
contre 69 % des methodes pour l index des appels. Un etat prive lu par les methodes de sa propre
classe est exactement cela, et c est la forme normale d une classe.

Cet outil ne juge donc rien, ne compte aucun suspect et n a pas de cliquet. Il REPOND. Le seul cas ou
il sort non nul est l absence de l index.

## Les lectures seules

Spoon distingue `CtFieldRead` de `CtFieldWrite`, et cet index ne retient que les lectures. Confondre
les deux ferait passer un champ qu un constructeur ecrit et que personne ne lit pour « utilise
ailleurs », soit le faux negatif exact que l index existe pour eviter. Les 4 347 ecritures du corpus
ne sont pas indexees : cet outil ne repond pas a « qui ecrit ce champ ».

## Aucun banc ne mute ce fichier

Comme `appelants.py` et `implemente.py`, il entre dans la derivation brute du banc de
`scripts/methode/temoins-de-methode-non-decoratifs.py` et en sort par le filtre `declare_un_contrat`.
Lui donner un `CONTRAT` le ferait entrer au banc sans un mot. En l etat ses mutations ont ete jouees
A LA MAIN, et qui touche a ce fichier sans les rejouer ne saura pas si ses cas jugent encore.

Usage :
    python3 scripts/qualite/lecteurs.py <nom|cle> [--index CHEMIN]
    python3 scripts/qualite/lecteurs.py --auto-test
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from _commun import cas_d_auto_test
from _commun import champs as lecteur
from _commun.champs import charge
from _commun.outil_d_index import PLAFOND, index_temoin, joue, message_du_refus, sans_bruit


def declarations(cible: str, index: dict[str, list[str]]) -> list[str]:
    """Les cles que `cible` designe : une cle ENTIERE telle quelle, ou un nom simple resolu.

    Deux formes, parce que l utilisateur a rarement la cle qualifiee sous la main. Le nom simple est
    ce qui suit le `#`, et la comparaison est une EGALITE : `racine` ne doit pas rendre `racineDuDepot`
    sous pretexte qu il en est un prefixe, sinon la reponse melange deux champs sans rapport.
    """
    if "#" in cible:
        return [cible] if cible in index else []
    return sorted(k for k in index if k.rsplit("#", 1)[-1] == cible)


def reponse(cible: str, index: dict[str, list[str]]) -> tuple[list[tuple[str, list[str]]], int]:
    """Les declarations LUES d ailleurs, et le NOMBRE de celles que personne ne lit.

    Les muettes sont comptees et non listees, et c est le choix de `appelants.py` pour la meme
    raison : a 92 % du corpus, les lister ferait de chaque reponse un mur ou la reponse se perdrait.
    Le compte, lui, dit qu elles existent.
    """
    trouvees = [(cle, index[cle]) for cle in declarations(cible, index)]
    lues = [(cle, qui) for cle, qui in trouvees if qui]
    lues.sort(key=lambda couple: (-len(couple[1]), couple[0]))
    return lues, len(trouvees) - len(lues)


def rendu(cible: str, lues: list[tuple[str, list[str]]], muettes: int) -> str:
    lignes = [f"LECTEURS DE {cible}"]
    if not lues and not muettes:
        lignes.append("  aucun champ de ce nom dans l index")
        return "\n".join(lignes + [f"\nLECTEURS DE {cible} | declarations=0 | lues=0"])

    total = 0
    for cle, qui in lues:
        total += len(qui)
        lignes.append(f"  {cle}")
        for lecteur in qui[:PLAFOND]:
            lignes.append(f"      <- {lecteur}")
        if len(qui) > PLAFOND:
            # Dire ce qu on tronque : une liste coupee en silence se lit comme une liste complete.
            lignes.append(f"      <- ... {len(qui) - PLAFOND} autre(s)")
    if muettes:
        lignes.append(
            f"  et {muettes} declaration(s) que rien ne lit hors de leur classe, ce qui est"
            " l etat NORMAL d un etat prive : 92 % du corpus est dans ce cas"
        )
    lignes.append(f"\nLECTEURS DE {cible} | declarations={len(lues) + muettes} | lues={total}")
    return "\n".join(lignes)


def auto_test() -> int:
    """Les cas de cet outil, sur un index FABRIQUE plutot que sur celui du depot.

    Le cas du REFUS passe par `main` ENTIER et non par `charge`, et le cas POSITIF emprunte le meme
    chemin. Les deux outils qui precedent ont livre leur premiere version sans ce contraste : leurs
    temoins appelaient la fonction de chargement, restaient VERTS quand on retirait le `except` du
    point d entree, et eprouvaient donc la bibliotheque plutot que l outil.
    """
    verifie, echecs = cas_d_auto_test()

    faux = {
        "fr.p.A#partage": ["fr.p.U", "fr.p.V", "fr.p.W"],
        "fr.p.B#partage": ["fr.p.X"],
        "fr.p.C#partage": [],
        "fr.p.A#partageDeux": ["fr.p.Y"],
        "fr.p.Englobe$Dedans#cache": ["fr.p.Englobe"],
    }

    verifie(
        "un nom simple rend toutes ses declarations",
        lambda: declarations("partage", faux),
        ["fr.p.A#partage", "fr.p.B#partage", "fr.p.C#partage"],
    )
    verifie(
        "un nom ne capture pas un PREFIXE : `partage` ne rend pas `partageDeux`",
        lambda: declarations("partag", faux),
        [],
    )
    verifie(
        "une cle ENTIERE passe telle quelle",
        lambda: declarations("fr.p.A#partage", faux),
        ["fr.p.A#partage"],
    )
    verifie(
        "un champ d un type IMBRIQUE se resout par son nom simple",
        lambda: declarations("cache", faux),
        ["fr.p.Englobe$Dedans#cache"],
    )
    verifie(
        "les declarations lues sont rendues, les muettes seulement COMPTEES",
        lambda: reponse("partage", faux),
        ([("fr.p.A#partage", ["fr.p.U", "fr.p.V", "fr.p.W"]), ("fr.p.B#partage", ["fr.p.X"])], 1),
    )
    verifie(
        "elles sont triees par nombre de lecteurs decroissant",
        lambda: [cle for cle, _ in reponse("partage", faux)[0]],
        ["fr.p.A#partage", "fr.p.B#partage"],
    )
    verifie(
        "un nom inconnu rend une reponse vide, sans lever",
        lambda: reponse("inexistant", faux),
        ([], 0),
    )
    verifie(
        "le rendu DIT que les muettes ne sont pas des champs morts",
        lambda: "etat NORMAL d un etat prive" in rendu("partage", *reponse("partage", faux)),
        True,
    )

    absent = pathlib.Path("/index-qui-n-existe-pas/index-champs.json")
    verifie(
        "un index ABSENT fait REFUSER l outil entier, au lieu de rendre « aucun lecteur »",
        lambda: main(["lecteurs.py", "--index", str(absent), "partage"]),
        1,
    )
    verifie(
        "et le refus DIT comment produire l index",
        lambda: "ExtracteurIndex" in message_du_refus(charge, absent),
        True,
    )
    verifie(
        "sur un index FABRIQUE, le meme chemin rend la reponse et sort en 0",
        lambda: sans_bruit(
            main, ["lecteurs.py", "--index", str(index_temoin(faux, "lecteurs")), "partage"]
        ),
        0,
    )

    # ⟨les cas du LECTEUR, joues ici parce que rien d autre ne les jouait⟩ Les trois modules de
    # `_commun` portent un `verifie_grammaire()` depuis #5473 et #5564, et la mesure du 2026-09-29
    # dit qu AUCUN harnais ne les appelait : seul celui d `arbre.py` est joue, par
    # `scripts/adr/4472-commentaire-en-corps.py`. Trois gages inertes, ce que l ADR 5546 nomme un
    # defaut. L outil est le bon endroit : il est le seul lecteur de son module, et son `--auto-test`
    # est joue par la porte comme par `lint.yml`.
    for libelle, tenu in lecteur.verifie_grammaire():
        verifie(f"lecteur des lectures de champ : {libelle}", lambda tenu=tenu: tenu, True)

    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    return joue(
        argv,
        nom="lecteurs.py",
        charge=charge,
        repond=lambda cible, index: rendu(cible, *reponse(cible, index)),
    )


if __name__ == "__main__":
    sys.exit(main(sys.argv))
