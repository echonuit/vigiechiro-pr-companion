#!/usr/bin/env python3
"""ADR 2843 — Le tiret cadratin ne se corrige pas d'un coup, il se cliquette.

La convention est écrite deux fois (`CONTRIBUTING.md`, `dev-docs/ajouter-une-fonctionnalite.md`) et
n'était appliquée par rien. Elle a été enfreinte pendant la clôture du chantier #2348, dans un message
de commit et un corps de PR, sans qu'aucun contrôle ne bronche : l'écart n'a été vu que parce que
quelqu'un a relu.

Pourquoi « probable » et non « certaine » : un tiret cadratin peut être **cité** légitimement, par
exemple dans un commentaire qui explique justement la règle, ou dans une chaîne reproduisant un texte
externe. Aucun motif ne sait faire cette différence.

Périmètre : les **sources Java** (`src/main/java`, `src/test/java`). La documentation Markdown en est
exclue à dessein. Un cliquet unique couvrant les deux populations pourrait **masquer** une régression
dans l'une par un nettoyage dans l'autre : le total resterait stable et le verdict vert. Une seule
population, un seul nombre, aucun angle mort. Si la documentation doit être tenue de même, elle aura
son propre cliquet.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

SOURCES = [pathlib.Path("src/main/java"), pathlib.Path("src/test/java")]

CADRATIN = "—"

# Fin de ligne conservée pour situer le suspect : c'est un humain qui tranche, extrait en main.
EXTRAIT = 90


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les lignes Java portant un tiret cadratin.

    `racine` sert au garde-fou `verifie_scripts.py`, qui pointe le script vers une fixture jetable.
    Sans elle, on balaie les deux arbres de sources du dépôt.
    """
    arbres = [racine] if racine is not None else SOURCES
    trouves = []
    for arbre in arbres:
        if not arbre.exists():
            continue
        for source in sorted(arbre.rglob("*.java")):
            contenu = source.read_text(encoding="utf-8")
            for numero, ligne in enumerate(contenu.splitlines(), 1):
                if CADRATIN in ligne:
                    trouves.append(f"{source}:{numero}  {ligne.strip()[:EXTRAIT]}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("2843", "tiret cadratin dans une source Java", suspects()))
