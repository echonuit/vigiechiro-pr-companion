#!/usr/bin/env python3
"""Une citation de passe de cloture designe une passe qui EXISTE (#4844).

Le depot compte 299 citations de ses passes, dans 145 fichiers : AGENTS.md, CONTRIBUTING.md,
`dev-docs/cycle-de-chantier.md`, six competences et leurs copies, une commande, deux gardes, un
workflow, une quinzaine d ADR et l index des decisions. Avant ce garde, RIEN ne verifiait qu une
citation designe encore une passe qui existe.

Mesure de #4518, qui a insere une passe et decale les deux suivantes : en remettant UNE citation a
l ancienne numerotation, `DocumentationAJourTest`, `DecisionsRespecteesTest`, la boucle complete de
`scripts/adr/`, `etapes-sans-renvoi-aval.py` et `synchronise-adaptateurs.py --verifie` sont TOUS
restes verts. Trente tests, aucun rouge. La seule verification qui ait mordu est une relecture
humaine, qui tient une fois et pas la suivante.

CE QUE CE GARDE NE COUVRE PAS, et il faut le lire avant de s y fier.

Il attrape une citation HORS BORNES : « passe 13 » quand le cycle en compte treize, numerotees 0 a
12. Il n attrape PAS une citation existante mais FAUSSE : « les ADR s ecrivent en passe 10 » quand 10
est devenue l archivage. Le numero est valide, seul le sens a glisse, et aucun garde ne le voit sans
savoir ce que la phrase veut dire.

Un garde partiel vaut mieux que rien, ET il doit dire ce qu il laisse passer : sans cette phrase il
se lirait comme couvrant tout, ce que l article A3 interdit.

Il ne compare pas non plus les trois listes du cycle entre elles. Leur desaccord est le sujet de
#4839, et l arbitrage de `6b` ne lui appartient pas.

Les bornes sont DERIVEES des titres `### N.` de `cycle-de-chantier.md`, jamais ecrites en dur : un
garde qui porte le compte en dur ment au prochain ajout, et c est le defaut qu il est cense empecher.

Usage :
    python3 scripts/methode/passes-citees-existent.py
    python3 scripts/methode/passes-citees-existent.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from _commun import sort_si_contrat_demande

CYCLE = RACINE / "dev-docs" / "cycle-de-chantier.md"

# Les titres de passes : `### 0. Relecture...`. Le suffixe litteral d une case `6b` n en est pas un.
TITRE = re.compile(r"^### (\d+)\. ", re.M)

# Une citation : « passe 10 », « passes 0 a 9 », « passes 3 et 9 ». Le `b` d une case suffixee est
# accepte et ignore : ce garde ne juge pas `6b`, qui est le sujet de #4839.
#
# LES DEUX BOUTS D UN INTERVALLE SE LISENT. Un premier dessin ne prenait que le nombre suivant
# « passes », si bien que « passes 0 a 13 » passait sur un cycle qui s arrete a 12 : la borne haute,
# celle qui bouge quand on ajoute une passe, n etait pas regardee. L auto-test l a dit.
CITATION = re.compile(r"\bpasses?\s+(\d+)b?(?:\s*(?:\u00e0|a|et|-)\s*(\d+)b?)?", re.I)

# Ce qu on ne lit pas : l historique, les artefacts de compilation, le graphe, et les archives de
# changements OpenSpec, qui datent un etat passe et n ont pas a suivre la numerotation courante.
EXCLUS = (".git", "target", "graphify-out", "node_modules", "openspec/changes/archive")

LISIBLES = (".md", ".py", ".sh", ".yml", ".yaml", ".bats", ".java", ".txt")


def bornes(texte: str):
    """Le plus petit et le plus grand numero de passe, derives des titres du cycle."""
    numeros = [int(n) for n in TITRE.findall(texte)]
    return (min(numeros), max(numeros)) if numeros else (None, None)


def citations_hors_bornes(texte: str, bas: int, haut: int):
    """Les numeros cites par `texte` qui ne designent aucune passe."""
    cites = set()
    for debut, fin in CITATION.findall(texte):
        cites.add(int(debut))
        if fin:
            cites.add(int(fin))
    return sorted(n for n in cites if not bas <= n <= haut)


# Ce fichier-ci ne se lit pas lui-meme. Son auto-test porte NECESSAIREMENT des contre-exemples
# (« la passe 13 » sur un cycle a douze), qui sont ce qu il refuse et non ce qu il affirme. Se lire
# reviendrait a se denoncer d exister. Sa prose, elle, reste sous la regle : elle ne cite aucun
# numero de passe.
MOI = pathlib.Path(__file__).resolve()


def _a_lire(chemin: pathlib.Path) -> bool:
    if chemin.resolve() == MOI:
        return False
    relatif = chemin.relative_to(RACINE).as_posix()
    return chemin.suffix in LISIBLES and not any(part in relatif for part in EXCLUS)


def suspects():
    bas, haut = bornes(CYCLE.read_text(encoding="utf-8"))
    if bas is None:
        return [f"{CYCLE.name} ne porte aucun titre `### N.` : les bornes ne se derivent pas"]
    fautes = []
    for f in sorted(RACINE.rglob("*")):
        if not f.is_file() or not _a_lire(f):
            continue
        try:
            texte = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for numero in citations_hors_bornes(texte, bas, haut):
            fautes.append(
                f"{f.relative_to(RACINE).as_posix()} cite « passe {numero} », "
                f"hors des bornes {bas} a {haut}"
            )
    return fautes


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    cycle = "### 0. Une\n\n### 1. Deux\n\n### 2. Trois\n"
    verifie("les bornes se derivent des titres", bornes(cycle), (0, 2))
    verifie("un cycle sans titre ne rend pas de bornes", bornes("du texte"), (None, None))

    # Le sens POSITIF : une citation valide ne doit pas etre denoncee.
    verifie("une passe dans les bornes passe", citations_hors_bornes("la passe 1 fait X", 0, 2), [])
    verifie("la borne haute passe", citations_hors_bornes("la passe 2 fait X", 0, 2), [])
    verifie("la borne basse passe", citations_hors_bornes("la passe 0 fait X", 0, 2), [])
    verifie("un intervalle valide passe", citations_hors_bornes("les passes 0 a 2", 0, 2), [])
    # La BORNE HAUTE d un intervalle est celle qui bouge quand on ajoute une passe. Sans ce cas, un
    # motif qui ne lit que le premier nombre passe pour bon.
    verifie(
        "la borne haute d un intervalle est lue",
        citations_hors_bornes("les passes 0 a 13", 0, 12),
        [13],
    )

    # Le sens NEGATIF, sans lequel un motif qui n accepterait jamais rien passerait tout ce qui
    # precede et rendrait vert sur un depot casse.
    verifie("une passe hors bornes est denoncee", citations_hors_bornes("la passe 3", 0, 2), [3])
    verifie("plusieurs le sont toutes", citations_hors_bornes("passes 3 et 9", 0, 2), [3, 9])
    verifie("la borne haute + 1 est denoncee", citations_hors_bornes("la passe 13", 0, 12), [13])

    # Une case suffixee est acceptee et son suffixe ignore : `6b` n est pas le sujet de ce garde.
    verifie("une case 6b n est pas denoncee", citations_hors_bornes("la passe 6b", 0, 12), [])

    # Ce que le garde NE PEUT PAS voir, eprouve pour que personne ne lui prete cette portee.
    verifie(
        "une citation valide mais FAUSSE passe, et c est une limite declaree",
        citations_hors_bornes("les ADR s ecrivent en passe 10", 0, 12),
        [],
    )
    return echecs


CONTRAT = {
    "geste": "citation de passe de cloture qui designe une passe inexistante",
    "population": "les competences de .agents/skills et leur copie de .claude/skills, et dev-docs/cycle-de-chantier.md",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/passes-citees-existent.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    fautes = suspects()
    for f in fautes:
        print(f"ÉCHEC : {f}", file=sys.stderr)
    if fautes:
        print(
            "\nUne citation qui nomme une passe inexistante envoie son lecteur nulle part. Les bornes\n"
            "sont dérivées des titres `### N.` de dev-docs/cycle-de-chantier.md : si une passe a été\n"
            "ajoutée ou retirée, ce sont les citations qui suivent, pas ce garde.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    print("Toutes les citations de passes désignent une passe qui existe.")
    raise SystemExit(0)
