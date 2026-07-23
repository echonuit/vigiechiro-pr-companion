#!/usr/bin/env python3
"""ADR 0008 — Aucun échec silencieux ; la sévérité de journalisation se décide à l'émission.

Pourquoi « probable » et non « certaine » : décider si un `catch` « expose » vraiment son échec
demande de comprendre le corps. Un catch qui journalise, relance, ou traduit l'échec en un état visible
respecte l'ADR ; il n'existe pas de motif syntaxique qui les distingue tous à coup sûr des catch muets.

Le script se concentre donc sur le cas le moins ambigu : le `catch` dont le corps est **vide**, une fois
les commentaires retirés. Là, l'échec disparaît sans laisser de trace, quel que soit le contexte. Un
commentaire du type « ignoré volontairement : … » ne suffit pas à sortir du compte : l'ADR veut une
trace observable à l'exécution, pas une note dans le source. C'est précisément ce qu'un humain doit
arbitrer suspect par suspect.

Ce que la règle protège : un échec avalé se paie plus tard, ailleurs, sous une forme méconnaissable -
un état incohérent, une opération qui « n'a rien fait » sans dire pourquoi.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

SOURCES = pathlib.Path("src/main/java")

# Un bloc catch sans accolade imbriquée : suffisant pour repérer les corps vides ou quasi vides.
CATCH = re.compile(r"catch\s*\([^)]*\)\s*\{([^{}]*)\}", re.S)
COMMENTAIRE = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)


def suspects() -> list[str]:
    trouves = []
    for source in sorted(SOURCES.rglob("*.java")):
        texte = source.read_text(encoding="utf-8")
        for bloc in CATCH.finditer(texte):
            corps = COMMENTAIRE.sub("", bloc.group(1)).strip()
            if not corps:
                ligne = texte[: bloc.start()].count("\n") + 1
                trouves.append(f"{source}:{ligne}  catch au corps vide")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0008", "échec silencieux : catch au corps vide", suspects()))
