#!/usr/bin/env python3
"""Applique dans les ADR les resserrements de cliquet que le rapport a détectés.

C'est la calibration « sur la base de la réalité » : quand le dépôt fait mieux que la marge, on ramène
le cliquet à la réalité, jamais l'inverse. Desserrer reste un geste humain, explicite, dans une PR -
c'est ce qui distingue un cliquet d'un tapis.

Le script N'ÉCRIT que des baisses. Il rend le nombre de cliquets modifiés, et 0 si rien à faire.

**Il écrit la valeur PARTOUT où elle est déclarée**, et pas seulement dans l'en-tête. Depuis #4403 un
seuil se porte aussi en balise `<!--inv:clé-->N<!--/inv-->` dans la prose qui l'annonce, à plusieurs
endroits. La première version ne connaissait que l'en-tête : elle laissait les balises derrière et
annonçait « 1 cliquet resserré », ce qui se lit comme « c'est fait ». `DocumentationAJourTest` les
rattrapait, mais après le push et dans un job de plusieurs minutes (#4407).

La clé de balise est déclarée par l'ADR elle-même, dans son champ `inv_key`, à côté du seuil. C'est
la même source pour l'outil qui écrit et pour le test qui vérifie : une carte tenue à deux endroits
serait exactement le défaut qu'on corrige ici.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import rapport  # noqa: E402

DECISIONS = pathlib.Path("dev-docs/decisions")

# Les racines de prose susceptibles de porter une balise. Mêmes que celles que balaie
# `DocumentationAJourTest`, et pour la même raison : une balise vit où on la lit.
PROSE = (pathlib.Path("dev-docs"), pathlib.Path("docs"), pathlib.Path("brief"))

# `<!--inv:clé-->N<!--/inv-->`, où N peut porter ses espaces de milliers.
BALISE = re.compile(r"(<!--inv:%s-->)([\d ]+)(<!--/inv-->)")

# La clé que l'ADR déclare pour sa propre valeur, dans son en-tête.
CLE_BALISE = re.compile(r"^inv_key:\s*([a-z-]+)\s*$", re.M)


def cle_de(texte: str) -> str | None:
    """La clé de balise déclarée par cette ADR, ou `None` si sa valeur ne se porte qu'en en-tête."""
    trouve = CLE_BALISE.search(texte)
    return trouve.group(1) if trouve else None


def ecrire_les_balises(cle: str, nouvelle: int) -> list[str]:
    """Réécrit toutes les balises `cle` avec `nouvelle`, et rend les fichiers touchés.

    Le séparateur de milliers est REPOSÉ comme il était : une prose française écrit « 3 248 », et
    remplacer par « 3248 » corrigerait un chiffre en abîmant une phrase.
    """
    motif = re.compile(BALISE.pattern % re.escape(cle))
    touches = []
    for racine in PROSE:
        if not racine.is_dir():
            continue
        for fichier in sorted(racine.rglob("*.md")):
            texte = fichier.read_text(encoding="utf-8")
            if f"<!--inv:{cle}-->" not in texte:
                continue
            neuf = motif.sub(lambda m: m.group(1) + _formate(m.group(2), nouvelle) + m.group(3), texte)
            if neuf != texte:
                fichier.write_text(neuf, encoding="utf-8")
                touches.append(str(fichier))
    return touches


def _formate(ancien: str, valeur: int) -> str:
    """`valeur` écrite comme `ancien` l'était : avec ses espaces de milliers, ou sans."""
    return f"{valeur:,}".replace(",", " ") if " " in ancien else str(valeur)


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
            cle = cle_de(nouveau)
            balises = ecrire_les_balises(cle, nouvelle) if cle else []
            ou = f", {len(balises)} balise(s)" if cle else ", aucune balise déclarée"
            faits.append(f"ADR {num} → cliquet {nouvelle}{ou}")
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
