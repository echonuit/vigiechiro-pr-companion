#!/usr/bin/env python3
"""Socle des scripts de vérification « probable » d'une ADR.

Un script `probable` ne prouve rien : il liste des **suspects** qu'un humain trie. Son signal utile
n'est donc pas « zéro », mais « aucun **nouveau** ». D'où le cliquet.

Le cliquet vit dans l'ADR elle-même, pas dans le script : c'est la seule façon qu'un lecteur de la
décision voie du même coup la marge en vigueur. Le script va l'y chercher, et le garde-fou
`DocumentationAJourTest` vérifie que la déclaration est bien formée.

Sortie normalisée, pour que le rapport hebdomadaire puisse agréger sans deviner :

    ADR 0010 | suspects=6 | cliquet=6 | verdict=ok
"""

import pathlib
import re
import sys

DECISIONS = pathlib.Path("dev-docs/decisions")

CLIQUET = re.compile(r"^- \*\*Vérification\*\* : probable — `[^`]+` \(cliquet : (\d+)\)$", re.M)


def cliquet(numero: str) -> int:
    """Le cliquet déclaré par l'ADR `numero`, lu dans son en-tête."""
    fichiers = sorted(DECISIONS.glob(f"{numero}-*.md"))
    if not fichiers:
        raise SystemExit(f"ADR {numero} introuvable sous {DECISIONS}")
    trouve = CLIQUET.search(fichiers[0].read_text(encoding="utf-8"))
    if not trouve:
        raise SystemExit(
            f"ADR {numero} ne déclare aucun cliquet lisible. Attendu, dans son en-tête :\n"
            f"  - **Vérification** : probable — `chemin/du/script` (cliquet : N)"
        )
    return int(trouve.group(1))


def rapporte(numero: str, titre: str, suspects: list[str]) -> int:
    """Affiche les suspects, confronte leur nombre au cliquet, et rend le code de sortie.

    Dépasser le cliquet est un échec : c'est une régression, quelqu'un a ajouté un cas.
    Passer *sous* le cliquet n'est pas un échec, c'est une bonne nouvelle - mais elle est signalée,
    parce que la marge doit alors être resserrée. Un cliquet qu'on ne resserre jamais redevient un
    tapis sous lequel on pousse.
    """
    marge = cliquet(numero)
    print(f"ADR {numero} — {titre}")
    for suspect in suspects:
        print(f"  {suspect}")

    verdict = "ok"
    if len(suspects) > marge:
        verdict = "regression"
    elif len(suspects) < marge:
        verdict = "a-resserrer"

    print(f"\nADR {numero} | suspects={len(suspects)} | cliquet={marge} | verdict={verdict}")

    if verdict == "regression":
        print(
            f"\nÉCHEC : {len(suspects)} suspects pour un cliquet de {marge}. Un cas a été ajouté.\n"
            f"Corrigez-le, ou justifiez-le et relevez le cliquet dans l'ADR — mais un cliquet qui\n"
            f"monte est une décision, pas une formalité.",
            file=sys.stderr,
        )
        return 1
    if verdict == "a-resserrer":
        print(
            f"\nLe dépôt fait mieux que sa marge ({len(suspects)} < {marge}) : resserrez le cliquet\n"
            f"à {len(suspects)} dans l'ADR, sinon la marge regagnée se reperdra en silence."
        )
    return 0
