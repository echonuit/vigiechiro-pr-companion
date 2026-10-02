"""La forme d une ligne de tableau markdown : son compte de cellules contre celui de son en-tete.

## Pourquoi ce module existe

Le controle est ne dans `scripts/adr/verifie_okf.py` avec #5700, et il ne jugeait qu un tableau :
celui de `dev-docs/decisions/index.md`. La passe 0 de la cloture de #5584 a mesure le depot entier
avec son motif : **697 fichiers markdown, 651 tableaux, et quatre lignes amputees** - toutes dans
`dev-docs/ci-cd-release.md`, dont TROIS ecrites le jour meme par la session qui livrait le controle.

Un concept qui vit dans un seul lecteur n est pas un concept, c est un cas particulier. Il vit donc
ici, et deux dispositifs l appellent : `verifie_okf.py` sur l index des ADR, et
`verifie_inventaires_ci.py` sur le tableau des ateliers et celui des gardes.

## Les deux pieges, tous deux mesures

**Un accent grave ne protege pas un pipe.** Dans un tableau markdown, un `|` ecrit a l interieur d un
code en ligne coupe quand meme la cellule. Les trois pipes du jour etaient dans `` `lus=101 | muets=81` ``
et dans `` `| # |` ``, et la cinquieme occurrence vivait dans la phrase qui expliquait qu un pipe de
prose doit etre echappe. Un `|` litteral s ecrit `\\|`, et c est pourquoi le compte se fait avec un
regard arriere - le corpus en porte un depuis l ADR 3099, « Taxon parent \\| Chiropteres ».

**La reference est l en-tete LE PLUS PROCHE au-dessus, jamais le premier du fichier.** Une session pair
a vu sa ligne atterrir 760 lignes plus bas, dans un tableau plus large : son compte de barres ne l a
attrapee que parce que les largeurs differaient. Prendre le premier au lieu du proche rend un resultat
juste tant que le fichier ne porte qu un tableau, et faux des le second.

## Ce que ce module ne fait pas

Il ne juge pas l alignement, ni la largeur, ni le contenu des cellules : seulement leur NOMBRE. Et il
ne lit pas ce qui vit dans un bloc a trois accents graves, ou un `|` en debut de ligne est de
l illustration et non une cellule.
"""

from __future__ import annotations

import re

# Un separateur de cellule, et SEULEMENT lui : `(?<!\\)` ecarte le pipe litteral `\|`.
SEPARATEUR = re.compile(r"(?<!\\)\|")

# Une ligne de tableau. La ligne de TIRETS ne fait pas exception, et c est un choix mesure : elle
# etait ecartee, et muter cette exception ne tuait aucun cas - sur les 651 tableaux du depot, aucune
# ligne de tirets ne porte un compte different de son en-tete. Une branche qu aucun cas ne peut faire
# rougir ne se livre pas, et une separation malformee est un defaut comme un autre.
_LIGNE = re.compile(r"^\s*\|")
_CLOTURE = "```"


def amputees(texte: str) -> list[tuple[int, str, int, int]]:
    """Les lignes dont le compte de cellules differe de leur en-tete, numero de ligne en premier.

    Rend des quadruplets `(no, ligne, vus, attendu)` et NON des phrases : chaque appelant nomme ce
    qu il juge dans ses propres termes - l index des ADR cite le numero de la decision, le tableau des
    ateliers cite le fichier. Une fonction qui rendrait la phrase imposerait son vocabulaire aux deux.

    L en-tete d un tableau est sa PREMIERE ligne, et un tableau se termine a la premiere ligne qui ne
    commence pas par un pipe. Deux tableaux separes par de la prose ont donc deux en-tetes, ce qui est
    exactement ce qu il faut : c est la seule lecture qui attrape une ligne tombee dans le voisin.
    """
    fautes: list[tuple[int, str, int, int]] = []
    attendu: int | None = None
    dans_un_bloc = False
    for no, ligne in enumerate(texte.splitlines(), 1):
        if ligne.lstrip().startswith(_CLOTURE):
            dans_un_bloc = not dans_un_bloc
            continue
        if dans_un_bloc:
            continue
        if not _LIGNE.match(ligne):
            attendu = None
            continue
        if attendu is None:
            attendu = len(SEPARATEUR.findall(ligne))
            continue
        vus = len(SEPARATEUR.findall(ligne))
        if vus != attendu:
            fautes.append((no, ligne, vus, attendu))
    return fautes


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de la forme, joues par les auto-tests des DEUX dispositifs qui appellent ce module.

    Le cas du pipe echappe est celui qui compte : un controle ecrit avec `ligne.count("|")` passe tous
    les autres et declare malformee l ADR 3099, qui cite « Taxon parent \\| Chiropteres ». Un garde qui
    crie sur du bon travail est un garde qu on apprend a ignorer (ADR 4002).
    """
    sain = "| a | b |\n|---|---|\n| 1 | 2 |\n"
    manque = "| a | b |\n|---|---|\n| 1 |\n"
    trop = "| a | b |\n|---|---|\n| 1 | 2 | 3 |\n"
    echappe = "| a | b |\n|---|---|\n| 1 | x \\| y |\n"
    brut = "| a | b |\n|---|---|\n| 1 | x | y |\n"
    deux = "| a | b |\n|---|---|\n| 1 | 2 |\n\n| a | b | c |\n|---|---|---|\n| 1 | 2 | 3 |\n"
    voisin = "| a | b |\n|---|---|\n| 1 | 2 |\n\n| a | b | c |\n|---|---|---|\n| 1 | 2 |\n"
    bloc = "```\n| a | b |\n| 1 |\n```\n"
    hors = "| a | b |\n|---|---|\n| 1 | 2 |\n"
    separation = "| a | b |\n|---|\n| 1 | 2 |\n"

    return [
        ("un tableau sain ne rend rien", amputees(sain) == []),
        ("une cellule qui manque est vue", len(amputees(manque)) == 1),
        ("une cellule de trop aussi", len(amputees(trop)) == 1),
        ("un pipe ECHAPPE ne compte pas pour une cellule", amputees(echappe) == []),
        (
            "et le meme pipe NU est vu, ce qui prouve que le cas precedent discrimine",
            len(amputees(brut)) == 1,
        ),
        (
            "deux tableaux de largeurs differentes sont jugés chacun sur le sien",
            amputees(deux) == [],
        ),
        ("une ligne tombee dans le tableau VOISIN est vue", len(amputees(voisin)) == 1),
        ("un bloc a trois accents graves n est pas un tableau", amputees(bloc) == []),
        ("le numero de ligne rendu est celui du fichier", [c[0] for c in amputees(manque)] == [3]),
        (
            "et les comptes rendus sont vus puis attendu",
            [c[2:] for c in amputees(manque)] == [(2, 3)],
        ),
        # La non-vacuite : sans ce cas, une fonction qui rendrait toujours une liste vide passerait
        # les quatre negations ci-dessus.
        (
            "la fonction DISCRIMINE : elle rend quelque chose sur au moins un cas",
            len(amputees(voisin)) > 0,
        ),
        ("une SEPARATION malformee est vue aussi", len(amputees(separation)) == 1),
        ("et rien sur un corpus sain", amputees(hors) == []),
    ]
