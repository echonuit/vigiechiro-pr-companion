#!/usr/bin/env python3
"""ADR 2843 — Le tiret cadratin ne se corrige pas d'un coup, il se cliquette.

La convention est écrite deux fois (`CONTRIBUTING.md`, `dev-docs/ajouter-une-fonctionnalite.md`) et
n'était appliquée par rien. Elle a été enfreinte pendant la clôture du chantier #2348, dans un message
de commit et un corps de PR, sans qu'aucun contrôle ne bronche : l'écart n'a été vu que parce que
quelqu'un a relu.

Pourquoi « probable » et non « certaine » : un tiret cadratin peut être **cité** légitimement, par
exemple dans un commentaire qui explique justement la règle, ou dans une chaîne reproduisant un texte
externe. Aucun motif ne sait faire cette différence.

Deux régimes, et c'est le point de conception de ce script.

**Le cliquet** ne porte que sur les **sources Java** (`src/main/java`, `src/test/java`), encore loin du
plancher. Un cliquet unique qui aurait aussi compté la documentation pourrait **masquer** une régression
dans l'une par un nettoyage dans l'autre : le total resterait stable et le verdict vert.

**La tolérance zéro** porte sur les zones **déjà nettoyées** ([#ZONES_NETTOYEES]). Une zone au plancher
n'a pas besoin d'une marge, elle a besoin d'un refus, et un refus ne se masque pas.
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


# Ce qui est **cité** ou **affiché** n'est pas notre prose. Trois formes, une seule règle :
#
# - le glyphe de valeur absente entre chevrons de code ;
# - un libellé de l'application entre guillemets français, qu'une fiche d'écran reproduit fidèlement ;
# - un cadratin **seul dans une cellule de tableau**, qui est le même glyphe posé dans du Markdown.
#
# Les trois se reconnaissent à leur encadrement. Trois listes d'exceptions auraient dérivé séparément.
CITE = re.compile("`" + CADRATIN + "`|«[^»\n]*»|\\|\\s*" + CADRATIN + "\\s*(?=\\|)")

# Les zones **nettoyées** par #2365, en tolérance zéro. Ajouter une tranche revient à ajouter **une
# ligne** ici : c'est délibéré, chaque tranche touchant ce même script, et une insertion d'une ligne
# se résout sans réfléchir là où un bloc de code aurait sérialisé les tranches.
#
# Tolérance zéro et non cliquet : une zone au plancher n'a pas besoin d'une marge, et une marge
# partagée avec une zone encore loin du plancher resterait masquable.
ZONES_NETTOYEES = (
    ("documentation utilisateur", pathlib.Path("docs"), ()),
    ("brief projet", pathlib.Path("brief"), ()),
    # `decisions` en attente : les cadratins des en-têtes d'ADR (Statut, Chantier, Vérification) sont
    # des **séparateurs de format**, lus par quatre analyseurs. Les changer est une migration de
    # format, pas un nettoyage de prose, et elle aura sa propre tranche.
    ("documentation développeur", pathlib.Path("dev-docs"), ("decisions",)),
)


def prose(racine: pathlib.Path, exclus: tuple[str, ...] = ()) -> list[str]:
    """Les cadratins de **prose** d'une zone nettoyée, citations exclues.

    `racine` est explicite : le garde-fou `verifie_scripts.py` y pointe une fixture jetable, et
    [#ZONES_NETTOYEES] la fournit pour chaque zone du dépôt. `exclus` nomme les sous-dossiers encore
    en chantier, pour qu'une zone puisse se garder **par morceaux** au lieu d'attendre d'être entière.
    """
    trouves = []
    if not racine.exists():
        return trouves
    for page in sorted(racine.rglob("*.md")):
        if any(part in exclus for part in page.parts):
            continue
        for numero, ligne in enumerate(page.read_text(encoding="utf-8").splitlines(), 1):
            if CADRATIN in CITE.sub("", ligne):
                trouves.append(f"{page}:{numero}  {ligne.strip()[:EXTRAIT]}")
    return trouves


if __name__ == "__main__":
    code = rapporte("2843", "tiret cadratin dans une source Java", suspects())

    for libelle, racine, exclus in ZONES_NETTOYEES:
        rechutes = prose(racine, exclus)
        print(f"\n{libelle} : {len(rechutes)} cadratin(s) de prose (tolérance zéro)")
        for suspect in rechutes:
            print(f"  {suspect}")
        if rechutes:
            print(
                f"\nLa zone « {libelle} » est nettoyée : un cadratin de prose y est une rechute.\n"
                "Écrivez un deux-points ou une virgule. Si vous citez le glyphe de valeur absente ou\n"
                "un libellé de l'application, encadrez-le de guillemets français ou de chevrons de code."
            )
            code = 1

    sys.exit(code)
