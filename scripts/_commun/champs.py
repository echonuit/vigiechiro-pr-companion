"""Le lecteur de `target/index-champs.json` : qui LIT ce champ hors de sa classe.

C est la TROISIEME des trois questions que #5464 ecrivait dans sa frontiere, et la derniere. Les deux
autres lecteurs du fonds ne savent pas la poser : `arbre.py` lit un fichier a la fois, donc il voit
la declaration d un champ et jamais ses lectures ailleurs ; PMD juge `UnusedPrivateField` dans la
seule classe qui declare le champ.

**Les LECTURES seules, et c est une limite declaree plutot qu un oubli.** Spoon distingue
`CtFieldRead` de `CtFieldWrite`, et confondre les deux ferait passer un champ qu un constructeur
ecrit et que personne ne lit pour « utilise ailleurs », soit le faux negatif exact que cet index
existe pour eviter. Les 4 347 ecritures du corpus ne sont donc pas indexees, et ce lecteur ne repond
pas a « qui ecrit ce champ ».

**Ce lecteur NE PRODUIT PAS l index**, il le lit. L extracteur le batit a la compilation, en meme
temps que les deux autres et sur le MEME modele : deux modeles dans une JVM levent
`UnsatisfiedLinkError` sur les natifs JavaFX (#5464).

## Ce qu il ne dit PAS, et le contresens a ne pas commettre

**Un champ sans lecteur externe n est pas un champ mort**, et ici la proportion rend le contresens
couteux : sur les 8 933 champs du depot, mesure du 2026-09-29, **8 195 n en ont aucun, soit 92 %**.
C est l etat normal d un etat prive, lu par les methodes de sa propre classe. Un garde bati sur cette
liste signalerait presque tout le corpus, et c est l ADR 5532 appliquee au troisieme index comme aux
deux premiers : il REPOND, il ne refuse pas.

La crainte de ce lot etait le volume, et la chaine mesuree le 2026-09-29 la dement. Elle vaut d etre
lue en entier, parce qu aucune de ses trois marches n a la meme cause :

    40 175 lectures brutes
    34 234 resolues            (les autres visent un champ hors du modele)
     5 993 hors de leur classe (la plupart des lectures sont intra-classe)
     3 173 aretes              (un lecteur qui lit deux fois ne compte qu une)

Plus 4 347 ecritures, jamais candidates. L index pese donc 785 Ko contre 2 353 pour celui des appels,
et son calcul coute 2,5 s sur un modele deja bati.
"""

from __future__ import annotations

import pathlib

from _commun.index import RACINE_DEPOT, IndexAbsent, charge_index, refus_de

INDEX = RACINE_DEPOT / "target" / "index-champs.json"

_REFUS = refus_de(
    "index-champs.json",
    "des lectures de champ",
    "aucun lecteur pour TOUT champ, donc un etat que personne ne lit",
)


def charge(chemin: pathlib.Path | None = None) -> dict[str, list[str]]:
    """L index des lectures de champ entier, ou un REFUS."""
    return charge_index(INDEX if chemin is None else chemin, _REFUS)


def lecteurs(champ: str, index: dict[str, list[str]] | None = None) -> list[str]:
    """Les types qui lisent ce champ depuis une AUTRE classe, nom QUALIFIE a l appui.

    La cle est celle que l extracteur ecrit : `fr.univ_amu.iut.X.Y#champ`. La meme forme que la
    signature d une methode, **sans les parentheses** - un champ n a pas de surcharge, donc rien a
    desambiguiser au-dela de son porteur qualifie. Un champ d un type imbrique porte un `$`,
    `fr.X.Englobe$Dedans#champ`.
    """
    return (charge() if index is None else index).get(champ, [])


def sans_lecteur_externe(index: dict[str, list[str]] | None = None) -> list[str]:
    """Les champs que rien ne lit hors de leur classe.

    **Ce n est pas une liste de champs morts**, et c est ici que les confondre coute le plus cher :
    ils sont 8 195 sur 8 933, soit 92 % du corpus. Un champ prive lu par les methodes de sa propre
    classe est vivant et figure ici. Ce que cette liste designe est plus etroit : un etat expose qui
    n avait pas besoin de l etre.
    """
    lu = charge() if index is None else index
    return sorted(k for k, v in lu.items() if not v)


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de ce lecteur, sur un index FABRIQUE plutot que sur celui du depot.

    Meme raison que pour les deux autres : un auto-test qui lirait `target/index-champs.json`
    mesurerait le corpus du jour et non le lecteur. Il rougirait au premier refactoring, et resterait
    vert si le lecteur cessait de lire.

    Les DEUX DERNIERS cas ne portent pas sur ce lecteur mais sur l accord des TROIS, et ils ne
    disent pas la meme chose. Que les trois refusent est desormais structurel, `joue` de
    `outil_d_index.py` ne portant qu un seul chemin de refus ; ce qui ne l est pas, c est que chacun
    refuse sur SON index. Un lecteur cable au `_REFUS` du voisin refuserait pareil, en nommant le
    mauvais fichier - un copier-coller que rien d autre ne verrait.
    """
    from _commun import implementations as lecteur_des_contrats
    from _commun import index as lecteur_des_appels

    faux = {
        "fr.X#partage": ["fr.Y", "fr.Z"],
        "fr.X#interne": [],
        "fr.Englobe$Dedans#cache": ["fr.Englobe"],
    }
    absent = pathlib.Path("/index-qui-n-existe-pas/index.json")

    def refuse(charger) -> bool:
        try:
            charger(absent)
        except IndexAbsent:
            return True
        return False

    def fichier_du_refus(charger) -> str:
        try:
            charger(absent)
        except IndexAbsent as refus:
            return next(m for m in str(refus).split() if m.startswith("`target/"))
        return ""

    trois = (charge, lecteur_des_appels.charge, lecteur_des_contrats.charge)

    return [
        (
            "les lecteurs d un champ sont rendus",
            lecteurs("fr.X#partage", faux) == ["fr.Y", "fr.Z"],
        ),
        (
            "un champ d un type IMBRIQUE se lit par sa cle a dollar",
            lecteurs("fr.Englobe$Dedans#cache", faux) == ["fr.Englobe"],
        ),
        ("un champ sans lecteur externe rend une liste vide", lecteurs("fr.X#interne", faux) == []),
        (
            "un champ inconnu rend une liste vide, sans lever",
            lecteurs("fr.absent#rien", faux) == [],
        ),
        (
            "les champs sans lecteur externe se listent",
            sans_lecteur_externe(faux) == ["fr.X#interne"],
        ),
        ("un index ABSENT fait refuser ce lecteur", refuse(charge)),
        (
            "et les TROIS lecteurs refusent pareil sur la meme absence",
            all(refuse(c) for c in trois),
        ),
        (
            "mais chacun nomme SON index, donc aucun n est cable sur le refus du voisin",
            len({fichier_du_refus(c) for c in trois}) == 3,
        ),
    ]
