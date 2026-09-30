"""Le bloc `relations:` d une ADR, lu en UN seul endroit et comparé APRÈS normalisation.

Deux dispositifs lisaient ce bloc et n en lisaient pas la même chose. Mesure du 2026-09-30, en
important le prédicat de chacun plutôt qu en réécrivant son motif :

    lecteur                                      son motif              ce qu il rate
    verifie_okf.lit_entete                       `partition(":")`       rien
    verifie_encart_de_revision.relations         `^\\s{2}([a-zé_]+):`    « complète », « fait évoluer »

Sur les 101 ADR qui portent un bloc `relations:`, **neuf** étaient lues différemment : sept écrivent
`complète` avec un accent grave, deux écrivent `fait évoluer` avec une espace.

## Le défaut n était PAS dans le motif, il était dans la comparaison

C est ce que l issue #5579 annonçait et ce que la mesure a démenti. Joué :

    verbe écrit           relations() le voit   fautes() refuse
    amendee_par                          True              True
    amendée_par                          True             False  <- ECHAPPE
    complétée_par                        True             False  <- ECHAPPE

Le motif `[a-zé_]+` **voit** `amendée_par`. C est `SUBIES`, un tuple littéral sans accents, qui ne le
reconnaît pas. Le garde d encart sortait donc vert sur une ADR amendée qui n annonçait rien sous son
titre, ce qui est exactement ce qu il existe pour empêcher.

Corriger le motif seul n aurait rien corrigé. D où la séparation de ce module : on **voit** tout, et on
**juge** explicitement.

## Pourquoi `é` ne servait à rien

Le motif admettait `é` U+00E9 quand le corpus écrit `complète` avec `è` U+00E8. Quelqu un a voulu gérer
les accents et a pris le mauvais : son `é` ne correspondait à aucun verbe du dépôt.

## Voir n est pas admettre

Un verbe portant une **espace** est vu par `verbes`, et refusé par `mal_ecrits`. L inverse - ne pas le
voir - est le défaut d origine : une clé invisible ne se signale pas, elle disparaît.

## Ce que ce module ne décide pas

Le vocabulaire des verbes n est déclaré nulle part, et ce module ne le clôt pas : il normalise ce qu il
lit au lieu d énumérer ce qu il accepte. Seules les relations SUBIES sont une liste close, parce que ce
sont les seules dont une obligation découle.
"""

from __future__ import annotations

import re
import unicodedata

# Les relations qu une ADR SUBIT, par opposition a celles qu elle exerce. Ecrites sous leur forme
# NORMALISEE : la comparaison passe par `normalise`, donc « amendée_par » les rejoint.
SUBIES = ("amendee_par", "completee_par", "remplacee_par")

# Les verbes qui DEPASSENT une decision, par opposition a ceux qui la citent ou la prolongent. Ils
# vivaient en DOUBLE dans `verifie_okf.py`, lignes 98 et 259, la seconde declaration masquant la
# premiere (#5579). Ils vivent ici parce que c est du vocabulaire de relation, comme `SUBIES`.
DEPASSEMENT = ("renverse", "remplace", "annule")

# Ce qu une ADR « deprecated » doit nommer pour ne pas rester sans successeur. Ce n est PAS `SUBIES` :
# les deux listes se recoupent sur `remplacee_par` et divergent ailleurs, `renversee_par` n etant pas
# dans l une et `amendee_par` pas dans l autre. La divergence est consignee, pas tranchee ici : dire
# laquelle des deux a raison est une question de vocabulaire, et personne ne l a posee.
SUCCESSEURS = ("remplacee_par", "renversee_par")

# Une cle du bloc, a deux espaces d indentation, suivie d une liste entre crochets. Tout ce qui
# precede le deux-points est le verbe, ESPACES ET ACCENTS COMPRIS : c est ce qui permet de refuser un
# verbe mal ecrit plutot que de le perdre.
LIGNE = re.compile(r"^  ([^:\n]+):\s*\[([^\]]*)\]", re.M)


def normalise(verbe: str) -> str:
    """Le verbe sans accents ni casse, pour que la comparaison cesse d etre litterale.

    `complète` et `complete` sont le MEME verbe, et le corpus ecrit les deux : 11 fois sans accent,
    7 fois avec. Aucun vocabulaire ne les departage, l en-tete etant libre.
    """
    sans_accents = "".join(
        c for c in unicodedata.normalize("NFD", verbe) if not unicodedata.combining(c)
    )
    return sans_accents.strip().lower()


def verbes(texte: str) -> dict[str, list[str]]:
    """Les relations declarees en en-tete, par verbe, TEL QU IL EST ECRIT.

    Le verbe n est pas normalise ici : un appelant qui signale une faute doit pouvoir citer ce que
    l auteur a ecrit. La normalisation est le fait de `normalise`, et de lui seul.
    """
    m = re.search(r"^relations:\s*$", texte, re.M)
    if not m:
        return {}
    bloc = texte[m.end() :].split("\n---", 1)[0]
    return {
        verbe.strip(): [c.strip().strip('"') for c in cibles.split(",") if c.strip()]
        for verbe, cibles in LIGNE.findall(bloc)
    }


def est_subie(verbe: str) -> bool:
    """Ce verbe designe-t-il une relation SUBIE, quelle que soit son orthographe ?"""
    return normalise(verbe) in SUBIES


def depasse(verbe: str) -> bool:
    """Ce verbe DEPASSE-t-il la decision qu il vise, quelle que soit son orthographe ?"""
    return normalise(verbe) in DEPASSEMENT


def nomme_un_successeur(relations_lues: dict[str, list[str]]) -> bool:
    """Ces relations nomment-elles un successeur, comme une ADR « deprecated » le doit ?"""
    return any(
        cibles for verbe, cibles in relations_lues.items() if normalise(verbe) in SUCCESSEURS
    )


def mal_ecrits(texte: str) -> list[str]:
    """Les verbes qu on REFUSE : ceux qui portent une espace.

    Un verbe de relation est une cle, pas une phrase. `fait évoluer` a existe deux fois, dans les ADR
    0047 et 2213, et aucun motif de cle ne pouvait le lire : les deux relations etaient donc
    invisibles au garde d encart. Elles ont ete reecrites en `amende`, le verbe existant qui dit ce
    qu elles faisaient.

    Ce controle est SEPARE de la lecture : `verbes` le voit, celui-ci le refuse. Ne pas le voir serait
    rejouer le defaut d origine.
    """
    return sorted(v for v in verbes(texte) if " " in v)


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de ce module, dont les trois formes mesurees dans le corpus.

    Le cas qui compte est l avant-dernier : sans lui, revenir a une comparaison litterale passerait
    tous les autres, puisque la forme sans accent a toujours ete reconnue.
    """
    entete = """---
type: adr
relations:
  {bloc}
---
"""

    def lu(bloc: str) -> dict[str, list[str]]:
        return verbes(entete.format(bloc=bloc))

    accent = lu('amendée_par: ["1234-x"]')
    grave = lu('complète: ["1234-x"]')
    espace = lu('fait évoluer: ["1234-x"]')
    plusieurs = lu('amende: ["1-a", "2-b"]')

    return [
        # La LECTURE : tout se voit, y compris ce qui sera refuse.
        ("un verbe ACCENTUE se lit", list(accent) == ["amendée_par"]),
        (
            "un verbe a l accent GRAVE se lit, celui que le motif d avant ratait",
            list(grave) == ["complète"],
        ),
        (
            "un verbe a ESPACE se lit aussi : ne pas le voir serait le perdre",
            list(espace) == ["fait évoluer"],
        ),
        ("les cibles se separent sur la virgule", plusieurs.get("amende") == ["1-a", "2-b"]),
        ("sans bloc relations, rien", verbes("---\ntype: adr\n---\n") == {}),
        # La NORMALISATION : deux orthographes, un seul concept.
        (
            "« complète » et « complete » se normalisent pareil",
            normalise("complète") == normalise("complete"),
        ),
        ("la casse ne compte pas", normalise("Amende") == "amende"),
        # Le JUGEMENT sur les relations subies, qui est la ou le defaut vivait.
        ("« amendee_par » est SUBIE", est_subie("amendee_par")),
        ("« amendée_par » AUSSI, et c est le defaut de #5579", est_subie("amendée_par")),
        ("« complétée_par » aussi", est_subie("complétée_par")),
        ("« amende » n est PAS subie : elle est exercee", not est_subie("amende")),
        ("« prolonge » non plus", not est_subie("prolonge")),
        # Le DEPASSEMENT et la SUCCESSION : deux vocabulaires de plus, et leurs comparaisons
        # passaient elles aussi par une liste en dur dans `verifie_okf.py`. Sans ces cas,
        # `depasse` et `nomme_un_successeur` n auraient AUCUN temoin : mesure du 2026-09-30, la
        # mutation de `depasse` en comparaison litterale survivait a tous les harnais.
        ("« remplace » DEPASSE la decision qu elle vise", depasse("remplace")),
        ("« Remplace » aussi, la casse ne protege pas", depasse("Remplace")),
        ("« amende » ne depasse pas : elle amende", not depasse("amende")),
        ("« prolonge » non plus", not depasse("prolonge")),
        (
            "« remplacee_par » nomme un successeur",
            nomme_un_successeur({"remplacee_par": ["1-a"]}),
        ),
        (
            "« remplacée_par » AUSSI, accent compris",
            nomme_un_successeur({"remplacée_par": ["1-a"]}),
        ),
        (
            "« renversée_par » aussi, c est l autre forme",
            nomme_un_successeur({"renversée_par": ["1-a"]}),
        ),
        (
            "« amendee_par » ne nomme PAS un successeur : les deux listes divergent",
            not nomme_un_successeur({"amendee_par": ["1-a"]}),
        ),
        (
            "une relation sans cible ne nomme personne",
            not nomme_un_successeur({"remplacee_par": []}),
        ),
        # Le REFUS d un verbe a espace, separe de sa lecture.
        (
            "un verbe a espace est REFUSE",
            mal_ecrits(entete.format(bloc='fait évoluer: ["1-a"]')) == ["fait évoluer"],
        ),
        (
            "un verbe sans espace ne l est pas",
            mal_ecrits(entete.format(bloc='amende: ["1-a"]')) == [],
        ),
    ]
