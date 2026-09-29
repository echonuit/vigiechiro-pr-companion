"""Le lecteur de `target/index-implementations.json` : quel type tient ce contrat, a travers le corpus.

C est la DEUXIEME des trois questions que #5464 ecrivait dans sa frontiere, et la premiere qu aucun
lecteur du depot ne savait poser. `arbre.py` lit un fichier a la fois, donc il voit qu une classe
declare `implements Contrat` et jamais qui d autre le declare ; PMD ne juge que des regles de
conception.

**Ce lecteur NE PRODUIT PAS l index**, il le lit. L extracteur le bâtit a la compilation, en meme
temps que celui des appels et sur le MEME modele : deux modeles dans une JVM levent
`UnsatisfiedLinkError` sur les natifs JavaFX (#5464).

## Ce qu il ne dit PAS, et le contresens a ne pas commettre

**Un contrat sans implementation n est pas un contrat mort.** Sur les 123 contrats du depot, mesure du
2026-09-29, **27 n ont aucun porteur** : une interface posee pour un point d extension a venir, une
classe abstraite dont la seule fille est anonyme, un contrat que seul un test implemente en ligne.
C est l ADR 5532 appliquee a cet index-ci : il REPOND, il ne refuse pas, et le seul cas ou son
consommateur sort non nul est l absence de l index.

## Son refus se DERIVE, il ne se recopie pas

`charge_index` et `refus_de` vivent dans `index.py`, et les deux lecteurs les appellent. Ce n est pas
une economie de lignes : deux loupes du depot ont partage un plafond en s y conduisant differemment,
l une refusant et l autre avertissant, et le partage a REVELE la divergence plutot que de la causer
(#5567). Un cas de ce module le surveille, et c est ce qu il manquait la-bas.
"""

from __future__ import annotations

import pathlib

from _commun.index import RACINE_DEPOT, IndexAbsent, charge_index, refus_de

INDEX = RACINE_DEPOT / "target" / "index-implementations.json"

_REFUS = refus_de(
    "index-implementations.json",
    "des implementations",
    "aucune implementation pour TOUT contrat, donc un corpus sans polymorphisme",
)


def charge(chemin: pathlib.Path | None = None) -> dict[str, list[str]]:
    """L index des implementations entier, ou un REFUS."""
    return charge_index(INDEX if chemin is None else chemin, _REFUS)


def porteurs(contrat: str, index: dict[str, list[str]] | None = None) -> list[str]:
    """Les types qui tiennent ce contrat, nom QUALIFIE a l appui.

    La cle est celle que l extracteur ecrit : `fr.univ_amu.iut.commun.view.SelecteurFichier`, et un
    type imbrique porte un `$`, `fr.X.Englobe$Dedans`. Les 117 interfaces du depot en comptent 22 de
    cette forme, et les ecarter faisait repondre « personne ne l implemente » a une question dont la
    reponse existe.
    """
    return (charge() if index is None else index).get(contrat, [])


def sans_porteur(index: dict[str, list[str]] | None = None) -> list[str]:
    """Les contrats que rien n implemente dans le corpus.

    **Ce n est pas une liste de contrats morts**, et les confondre serait le contresens de ce lecteur.
    Une interface posee pour un point d extension, une classe abstraite dont la seule fille est
    anonyme, un contrat qu un seul test implemente en ligne : les trois sont vivants et figurent ici.
    """
    lu = charge() if index is None else index
    return sorted(k for k, v in lu.items() if not v)


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de ce lecteur, sur un index FABRIQUE plutot que sur celui du depot.

    Meme raison que pour `index.py` : un auto-test qui lirait `target/index-implementations.json`
    mesurerait le corpus du jour et non le lecteur. Il rougirait au premier refactoring, et resterait
    vert si le lecteur cessait de lire.

    Le DERNIER cas est celui que ce module doit a #5567 : il ne porte pas sur ce lecteur mais sur
    l accord des DEUX, parce qu un refus partage qui divergerait ne se verrait nulle part ailleurs.
    """
    from _commun import index as lecteur_des_appels

    faux = {
        "fr.Contrat": ["fr.Une", "fr.Deux"],
        "fr.Seul": [],
        "fr.Englobe$Dedans": ["fr.Englobe$Tient"],
    }
    absent = pathlib.Path("/index-qui-n-existe-pas/index.json")

    def refuse(charger) -> bool:
        try:
            charger(absent)
        except IndexAbsent:
            return True
        return False

    return [
        (
            "les porteurs d un contrat sont rendus",
            porteurs("fr.Contrat", faux) == ["fr.Une", "fr.Deux"],
        ),
        (
            "un contrat IMBRIQUE se lit par sa cle a dollar",
            porteurs("fr.Englobe$Dedans", faux) == ["fr.Englobe$Tient"],
        ),
        ("un contrat sans porteur rend une liste vide", porteurs("fr.Seul", faux) == []),
        ("un contrat inconnu rend une liste vide, sans lever", porteurs("fr.absent", faux) == []),
        ("les contrats sans porteur se listent", sans_porteur(faux) == ["fr.Seul"]),
        ("un index ABSENT fait refuser ce lecteur", refuse(charge)),
        (
            "et les DEUX lecteurs refusent pareil sur la meme absence",
            refuse(charge) == refuse(lecteur_des_appels.charge),
        ),
    ]
