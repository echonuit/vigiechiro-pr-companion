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


def _parcours_resolu(source: str, relatif: str) -> set[str]:
    """Les dossiers qu un garde parcourt, quand l evaluation symbolique sait les resoudre."""
    import importlib.util

    outil = pathlib.Path(__file__).resolve().parents[1] / "methode" / "contrats-des-gardes.py"
    spec = importlib.util.spec_from_file_location("_cdg", outil)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.chemins_lus(source, relatif)


def _couvre(motifs: list[str], dossier: str) -> bool:
    """Un dossier parcouru est couvert si un motif y apparie un fichier, a plat ou en profondeur."""
    from batterie import correspond

    return any(
        correspond(f"{dossier}/x.txt", m) or correspond(f"{dossier}/a/b/x.txt", m) for m in motifs
    )


def lacunes(racine: pathlib.Path | None = None) -> tuple[list[str], int]:
    """Les gardes dont le `chemins` DECLARE ne couvre pas un chemin qu ils PARCOURENT, et le compte lu.

    ## Pourquoi ce n est pas le meme defaut que le cliquet ci-dessus

    Un `chemins` **absent** est couvert par un repli : le garde est lance (ADR 5340). Un `chemins`
    **incomplet** n a aucun repli - il fait TAIRE le garde, en silence, et la porte affiche « non
    engage », ce qui se lit comme une bonne nouvelle. La lacune est donc plus dangereuse que
    l absence, alors que la doctrine de ce depot veut l inverse. Elle refuse, elle ne se cliquette
    pas.

    ## Ce que ce dispositif NE couvre pas, et il faut le lire avant son zero

    Il ne juge que les gardes dont l evaluation symbolique sait resoudre le parcours **et** qui
    declarent un `chemins`. Mesure du 2026-09-07 : soixante-seize gardes portent un CONTRAT,
    dix-neuf ont un parcours resolu, quatorze declarent leurs chemins, et **quatre** reunissent les
    deux. Son zero ne vaut donc que pour ces quatre-la, et c est ce que l article A3 lui demande de
    dire.
    """
    from batterie import gardes

    ecarts, confrontables = [], 0
    for relatif, chemins in gardes(racine):
        if not chemins:
            continue
        fichier = (racine or pathlib.Path(__file__).resolve().parents[2]) / relatif
        parcourus = _parcours_resolu(fichier.read_text(encoding="utf-8"), relatif)
        if not parcourus:
            continue
        confrontables += 1
        motifs = [m.strip() for m in chemins if m.strip()]
        manquants = sorted(p for p in parcourus if not _couvre(motifs, p))
        if manquants:
            ecarts.append(f"{relatif}  parcourt {', '.join(manquants)} sans le declarer")
    return ecarts, confrontables


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

    # ⟨la seconde confrontation, sur un arbre jouet⟩ Les DEUX sens : un `chemins` qui rate un
    # parcours est vu, un `chemins` qui le couvre ne l est pas. Sans le second, une fonction qui
    # signalerait tout le monde passerait le premier et serait aussi inutile qu une muette.
    import tempfile
    import textwrap

    with tempfile.TemporaryDirectory(prefix="vc-5340-") as bac:
        faux = pathlib.Path(bac) / "depot"
        (faux / "scripts" / "adr").mkdir(parents=True)
        (faux / "scripts" / "methode").mkdir(parents=True)

        def pose(nom: str, motifs: str) -> None:
            (faux / "scripts" / "methode" / nom).write_text(
                textwrap.dedent(f'''
                    import pathlib
                    RACINE = pathlib.Path(__file__).resolve().parents[2]
                    def lit():
                        return sorted((RACINE / "dev-docs").rglob("*.md"))
                    CONTRAT = {{"geste": "x", "population": "y", "dispositif": "invariant",
                               "seuil": "(sans objet)", "temoin": "t", "decision": "d",
                               "chemins": """
                    {motifs}
                    """}}
                '''),
                encoding="utf-8",
            )

        pose("etroit.py", "src/main/**")
        pose("couvrant.py", "dev-docs/**")
        ecarts, confrontables = lacunes(faux)
        vus = " ".join(ecarts)

        for libelle, attendu, present in (
            ("un `chemins` qui rate un parcours est vu", True, "etroit.py" in vus),
            ("un `chemins` qui couvre son parcours n est pas vu", False, "couvrant.py" in vus),
        ):
            if present is attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : {ecarts}")
                echecs += 1

        if confrontables == 2:
            print("  ✔ le compte des confrontables est celui des gardes lisibles")
        else:
            print(f"  ✘ confrontables={confrontables}, attendu 2")
            echecs += 1

    print("\n6 cas : l accord avec la porte, et la lacune dans les deux sens.")
    return 1 if echecs else 0


CONTRAT = {
    "geste": "garde qui porte un contrat sans y declarer ses `chemins`, donc lance a chaque appel",
    "population": "les gardes de scripts/adr et scripts/methode qui portent un CONTRAT",
    "dispositif": "cliquet",
    "seuil": "43, polarite=descend",
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
    # ⟨les deux polarites du meme champ⟩ L absence se cliquette parce qu elle est couteuse et sans
    # danger ; la LACUNE refuse parce qu elle fait taire un garde en silence. Melanger les deux
    # comptes ferait descendre l un en laissant monter l autre.
    ecarts, confrontables = lacunes()
    code = rapporte(ADR, "garde qui ne declare pas ses chemins", suspects(), lus=lus())
    if ecarts:
        print(
            f"\nÉCHEC : {len(ecarts)} `chemins` declare(s) ne couvre(nt) pas ce que le garde PARCOURT.",
            file=sys.stderr,
        )
        print(
            "Une lacune n a pas de repli : elle fait TAIRE le garde, et la porte affiche « non\n"
            "engage », ce qui se lit comme une bonne nouvelle. Elargissez le `chemins`.",
            file=sys.stderr,
        )
        for e in ecarts:
            print(f"  {e}", file=sys.stderr)
        code = 1
    print(f"ADR {ADR} | confrontables={confrontables} | lacunes={len(ecarts)}")
    sys.exit(code)
