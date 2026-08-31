#!/usr/bin/env python3
"""Applique dans les ADR les resserrements de cliquet, et repose les balises sur ce qui est mesuré.

**Deux gestes, et les confondre coûte cher.** RESORBER descend un seuil ; MESURER rend la prose
conforme à l'en-tête. La version précédente ne faisait le second qu'à l'intérieur du premier :
une valeur qui MONTE - un corpus qu'on élargit, un garde qui cesse d'être aveugle - ou qui ne
bouge pas laissait ses balises telles quelles, et le script annonçait « 0 cliquet resserré », ce
qui se lit comme « rien à écrire ». Trois chiffres périmés en une seule journée, chaque fois vus
par `DocumentationAJourTest`, donc par un test Java, donc après le seul job que personne ne
relance quand il expire (#4469).

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
from _commun import DECISIONS, RACINE_DEPOT  # noqa: E402

# Les racines de prose susceptibles de porter une balise. Mêmes que celles que balaie
# `DocumentationAJourTest`, et pour la même raison : une balise vit où on la lit.
#
# ANCRÉES sur la racine, comme `DECISIONS` qu'on importe au lieu de la recopier (issue #4781). Ce
# script ÉCRIT, et sous sa forme relative il annonçait « 0 cliquet resserré » depuis n'importe quel
# répertoire, y compris un qui n'est pas un dépôt. Un succès sans avoir rien lu.
PROSE = tuple(RACINE_DEPOT / nom for nom in ("dev-docs", "docs", "brief"))

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
            neuf = motif.sub(
                lambda m: m.group(1) + _formate(m.group(2), nouvelle) + m.group(3), texte
            )
            if neuf != texte:
                fichier.write_text(neuf, encoding="utf-8")
                touches.append(str(fichier.relative_to(RACINE_DEPOT)))
    return touches


def _formate(ancien: str, valeur: int) -> str:
    """`valeur` écrite comme `ancien` l'était : avec ses espaces de milliers, ou sans."""
    return f"{valeur:,}".replace(",", " ") if " " in ancien else str(valeur)


SEUIL_DECLARE = re.compile(r"^(?:ratchet|floor):\s*(\d+)\s*$", re.M)


def aligner_les_balises() -> list[str]:
    """Repose chaque balise sur la valeur que SON ADR declare, quel que soit le sens du mouvement.

    Resserrer et MESURER sont deux gestes, et les confondre a laisse trois chiffres perimes derriere
    en une seule journee (#4469). L ancienne version n ecrivait les balises que dans la boucle des
    resserrements : une valeur qui MONTE - un corpus qu on elargit, un garde qui cesse d etre aveugle
    - ou qui ne bouge pas laissait ses balises telles quelles, et le script annoncait « 0 cliquet
    resserre », ce qui se lit comme « rien a ecrire ».

    Cette passe-ci ne juge rien : elle rend la prose conforme a l en-tete. C est
    `DocumentationAJourTest` qui refusait l ecart, donc un test Java, donc apres le seul job que
    personne ne relance quand il expire.
    """
    faits = []
    for fichier in sorted(DECISIONS.glob("[0-9]*.md")):
        texte = fichier.read_text(encoding="utf-8")
        cle = cle_de(texte)
        if not cle:
            continue
        declare = SEUIL_DECLARE.search(texte)
        if not declare:
            continue
        touches = ecrire_les_balises(cle, int(declare.group(1)))
        if touches:
            faits.append(
                f"balise `{cle}` reposee a {declare.group(1)} dans {len(touches)} fichier(s)"
            )
    return faits


def appliquer() -> list[str]:
    # `collecter()` rend quatre listes depuis #4635 : cliquets, planchers, loupes, et les scripts
    # dont le verdict n a pas ete lu. Seuls les cliquets se resserrent ici.
    cliquets, _, _, _ = rapport.collecter()
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
    # MESURER vient apres RESORBER, et se dit a part : une passe qui ne resserre rien peut avoir
    # des balises a reposer, et « 0 cliquet resserré » ne doit plus se lire comme « rien à faire ».
    reposees = aligner_les_balises()
    for f in reposees:
        print(f)
    print(f"\n{len(faits)} cliquet(s) resserré(s), {len(reposees)} balise(s) reposée(s).")
