#!/usr/bin/env python3
"""Cliquet sur les gardes qui ne declarent pas leurs `chemins` : le compte descend (ADR 5340).

`scripts/batterie.py` repond a « quels controles ce diff engage-t-il ? ». Elle le fait a partir du
champ `chemins` que chaque garde declare, et **lance ceux qui n en declarent pas**. Le defaut penche
du cote couteux, jamais du cote muet.

Ce repli rend la porte JUSTE des le premier jour, avec neuf declarants sur soixante et onze. Il la
rend aussi IMPRECISE : chaque garde muet est une commande lancee pour rien, a chaque appel. Le
cliquet est ce qui transforme cette imprecision en dette qui se resorbe, plutot qu en etat stable.

## Pourquoi « probable » et non « certaine »

Il compte des declarations, ce qui est exact. Mais la DECISION qu il sert est « la porte sait ce
qu elle lance ». Un `chemins` peut etre declare et FAUX - trop etroit, il ferait taire un garde qui
devait juger. Aucun compte ne le voit. C est `loupe-5175` qui porte cette question-la pour la
`population`, et elle y a mesure que l evaluation symbolique ne resout que treize gardes sur
quarante et un.

Le cliquet borne la dette et rend la cible opposable ; il ne juge pas une declaration en
particulier, et c est une relecture qui trie.

## Ce qu il n empeche pas

Qu un `chemins` declare soit trop LARGE. Un garde qui declarerait `**` serait toujours lance, et le
cliquet le compterait comme declarant. La faille est reelle et connue : elle se refermera si on la
constate, pas par precaution.
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import rapporte, sort_si_contrat_demande

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "scripts"))

ADR = "5340"


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les gardes qui portent un CONTRAT sans y declarer leurs `chemins`.

    `racine` est injectable pour que `verifie_scripts.py` puisse monter un arbre jouet et eprouver
    les DEUX sens - un arbre ou tous declarent ne rend rien, un garde muet est vu. Sans elle, le cas
    temoin devrait muter le depot lui-meme, ce que le harnais refuse (#4700).
    """
    from batterie import gardes

    return [g for g, chemins in gardes(racine) if not chemins]


def lus(racine: pathlib.Path | None = None) -> int:
    from batterie import gardes

    return len(gardes(racine))


def _auto_test() -> int:
    """Le cliquet compte ce que la porte lit, donc son temoin est celui de la porte.

    Ecrire ici un second parcours des contrats ferait DIVERGER le compte du cliquet de ce que la
    porte lance reellement : c est le defaut que #5175 a mesure ailleurs, un garde et sa mesure qui
    ne lisent pas la meme chose. Ce cas verifie donc l accord des deux, et non un parcours a lui.
    """
    from batterie import gardes

    tous = gardes()
    muets = suspects()
    declarants = [g for g, c in tous if c]

    echecs = 0
    if len(muets) + len(declarants) == len(tous):
        print("  ✔ chaque garde est soit déclarant, soit muet, jamais les deux")
    else:
        print("  ✘ le compte des muets et des déclarants ne fait pas le corpus")
        echecs += 1

    if declarants:
        print(f"  ✔ {len(declarants)} garde(s) déclarent leurs chemins")
    else:
        print("  ✘ aucun garde ne déclare ses chemins : le cliquet n'aurait rien à faire descendre")
        echecs += 1

    if muets and all(isinstance(m, str) and m.startswith("scripts/") for m in muets):
        print("  ✔ les muets sont nommés par leur chemin, et non comptés en aveugle")
    else:
        print("  ✘ les muets ne sont pas nommés")
        echecs += 1

    print("\n3 cas d'accord entre le cliquet et la porte.")
    return 1 if echecs else 0


CONTRAT = {
    "geste": "garde qui porte un contrat sans y declarer ses `chemins`, donc lance a chaque appel",
    "population": "les gardes de scripts/adr et scripts/methode qui portent un CONTRAT",
    "dispositif": "cliquet",
    "seuil": "48, polarite=descend",
    "temoin": "scripts/adr/5340-chemins-non-declares.py --auto-test",
    "decision": "ADR 5340",
    "chemins": """
scripts/**
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(rapporte(ADR, "garde qui ne declare pas ses chemins", suspects(), lus=lus()))
