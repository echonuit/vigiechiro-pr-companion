#!/usr/bin/env python3
"""Applique dans les ADR les resserrements de cliquet que le rapport a détectés.

C'est la calibration « sur la base de la réalité » : quand le dépôt fait mieux que la marge, on ramène
le cliquet à la réalité, jamais l'inverse. Desserrer reste un geste humain, explicite, dans une PR -
c'est ce qui distingue un cliquet d'un tapis.

Le script N'ÉCRIT que des baisses. Il rend le nombre de cliquets modifiés, et 0 si rien à faire.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import rapport  # noqa: E402

DECISIONS = pathlib.Path("dev-docs/decisions")


def appliquer() -> list[str]:
    cliquets, _ = rapport.collecter()
    faits = []
    for num, nouvelle in rapport.resserrements(cliquets):
        fichier = sorted(DECISIONS.glob(f"{num}-*.md"))[0]
        texte = fichier.read_text(encoding="utf-8")
        # On ne baisse que le champ `ratchet` de CETTE ADR, dans son en-tête.
        nouveau, n = re.subn(
            r"^ratchet:\s*\d+\s*$",
            f"ratchet: {nouvelle}",
            texte,
            count=1,
            flags=re.M,
        )
        if n == 1 and nouveau != texte:
            fichier.write_text(nouveau, encoding="utf-8")
            faits.append(f"ADR {num} → cliquet {nouvelle}")
        elif n == 0:
            # Sans ceci, un en-tête que l'expression ne reconnaît plus ferait rendre « 0 cliquet
            # resserré », c'est-à-dire un vert qui ressemble à « rien à faire ». Article A12.
            raise SystemExit(
                f"ADR {num} : aucun champ `ratchet:` trouvé dans l'en-tête. "
                f"Le resserrement à {nouvelle} n'a pas été écrit."
            )
    return faits


if __name__ == "__main__":
    faits = appliquer()
    for f in faits:
        print(f)
    print(f"\n{len(faits)} cliquet(s) resserré(s).")
