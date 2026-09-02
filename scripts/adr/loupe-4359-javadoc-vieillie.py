#!/usr/bin/env python3
"""Loupe de l'ADR 4359 - les blocs dont le CODE a bougé après leur javadoc.

## Ce qu'elle sert

La résorption du lot 2 (#4394) raccourcit des blocs. Contracter une javadoc **déjà fausse** ne la
corrige pas : elle la préserve, en plus court et donc plus crédible. Cette loupe dit **où regarder
en priorité** avant de couper.

Le signal est grossier et assumé comme tel : un commit qui n'a touché qu'une accolade fait remonter
la date du code sans rien invalider. Ce n'est pas une liste de fautes, c'est une surface de revue -
et l'article A6 range en `humaine` ce qui décide si un texte décrit encore son code.

## Ce qui l'a motivée, mesuré

Sur un échantillon de 60 des 554 fichiers porteurs de dette, **27 %** avaient vu leur code bouger
après leur javadoc. Et la tranche 2 a trouvé **deux blocs faux sur douze ouverts** - un qui annonçait
quatre onglets là où le code en rend six, un autre qui décrivait le comportement d'avant sa propre
correction. Les deux ont été attrapés par accident, parce qu'ils se contredisaient eux-mêmes ; un
bloc faux et cohérent serait passé.

## Le grain, et pourquoi le bloc plutôt que le fichier

Un fichier entier remonte dès qu'une seule de ses méthodes bouge, ce qui désignerait des blocs
intacts et noierait le signal. La loupe compare donc **chaque bloc** au code qu'il surmonte, jusqu'au
bloc suivant.

## Sa cécité, déclarée

- Elle lit les **deux** arbres Java, comme le cliquet auquel elle s'adosse : un commentaire de
  classe de test vieillit comme un autre.
- Elle ne voit pas un bloc **faux dès le premier jour** : les deux dates sont alors les mêmes, et
  c'est le cas de plus de la moitié du corpus. C'est la limite qui compte, et elle est réelle.
- Une javadoc corrigée **après** le code sort de la liste, à juste titre - mais rien ne dit qu'elle a
  été corrigée POUR le suivre.
"""

import importlib.util
import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT, RACINES, imprime_contrat, loupe

ADR = "4359"
RACINE = pathlib.Path(__file__).resolve().parents[2]

ARBRES = tuple(arbre.as_posix() for arbre in RACINES)

# Les seuils de prose au-delà desquels un bloc entre dans le cliquet de l'ADR 4359, **importés** de
# son garde et non recopiés. La version précédente écrivait `SEUIL = 8` en dur sous un commentaire
# qui affirmait l'inverse : la loupe regardait donc déjà autre chose que ce qui était compté, et le
# seuil par nature n'a fait qu'aggraver l'écart.
_garde = importlib.util.spec_from_file_location(
    "cliquet4359", pathlib.Path(__file__).parent / "4359-javadoc-narratif.py"
)
_cliquet = importlib.util.module_from_spec(_garde)
_garde.loader.exec_module(_cliquet)
SEUILS = _cliquet.SEUILS
nature = _cliquet.nature

HORODATAGE = re.compile(r"^author-time (\d+)$", re.M)


def fichiers(racine: pathlib.Path) -> list[str]:
    """Les fichiers Java des deux arbres, suivis par git."""
    sortie = subprocess.run(
        ["git", "-C", str(racine), "ls-files", "-z", *ARBRES], capture_output=True, check=True
    ).stdout.decode()
    return sorted(c for c in sortie.split("\0") if c.endswith(".java"))


def temps_des_lignes(racine: pathlib.Path, chemin: str) -> list[int]:
    """L'horodatage d'auteur de chaque ligne, dans l'ordre du fichier.

    `--line-porcelain` et non `--porcelain` : la forme courte ne répète l'en-tête que pour la
    première ligne d'un même commit, et les suivantes se liraient alors à zéro. Une première version
    l'employait, et rendait « aucun candidat » sur un corpus qui en portait cent cinquante.
    """
    sortie = subprocess.run(
        ["git", "-C", str(racine), "blame", "--line-porcelain", "--", chemin],
        capture_output=True,
        check=True,
    ).stdout.decode("utf-8", "replace")
    temps, courant = [], 0
    for ligne in sortie.split("\n"):
        trouve = HORODATAGE.match(ligne)
        if trouve:
            courant = int(trouve.group(1))
        elif ligne.startswith("\t"):
            temps.append(courant)
    return temps


def prose(lignes: list[str]) -> int:
    """Les lignes de prose d'un bloc : ni vides, ni étiquettes de contrat et leurs suites."""
    compte, dans_etiquette = 0, False
    for ligne in lignes:
        corps = ligne.strip()[3:].strip()
        if not corps:
            dans_etiquette = False
            continue
        if re.match(r"@(param|return|throws)\b", corps):
            dans_etiquette = True
            continue
        if dans_etiquette:
            continue
        compte += 1
    return compte


def candidats_du_fichier(chemin: str, lignes: list[str], temps: list[int]) -> list[str]:
    """Les blocs de `lignes` dont le code qui suit est plus récent que le bloc lui-même.

    Fonction PURE : elle ne lit ni git ni le disque, pour qu'un cas témoin puisse la conduire sur des
    lignes fabriquées. C'est la partie où la loupe peut se tromper, donc celle qu'il faut éprouver.
    """
    trouves, i = [], 0
    while i < len(lignes):
        if not lignes[i].strip().startswith("///"):
            i += 1
            continue
        j = i
        while j < len(lignes) and lignes[j].strip().startswith("///"):
            j += 1
        # Le code que ce bloc surmonte : jusqu'au bloc suivant, ou la fin du fichier.
        k = j
        while k < len(lignes) and not lignes[k].strip().startswith("///"):
            k += 1
        # La nature de ce que le bloc surmonte decide de son seuil, comme dans le cliquet.
        declaration = next((l for l in lignes[j:k] if l.strip()), "")
        if prose(lignes[i:j]) > SEUILS[nature(declaration)]:
            doc = max(temps[i:j], default=0)
            code = max((t for t, l in zip(temps[j:k], lignes[j:k]) if l.strip()), default=0)
            if code > doc:
                trouves.append(
                    f"{chemin}:{i + 1}  bloc de {prose(lignes[i:j])} lignes, "
                    f"code plus récent de {(code - doc) // 86400} jour(s)"
                )
        i = max(j, k)
    return trouves


def candidats(racine: pathlib.Path | None = None) -> list[str]:
    """Les blocs sous cliquet dont le code a bougé après eux."""
    base = racine or RACINE
    trouves = []
    for chemin in fichiers(base):
        lignes = (base / chemin).read_text(encoding="utf-8").split("\n")
        temps = temps_des_lignes(base, chemin)
        if len(temps) < len(lignes) - 1:
            # Un fichier que blame et la lecture ne comptent pas pareil : on ne devine pas.
            continue
        trouves.extend(candidats_du_fichier(chemin, lignes, temps))
    return trouves


CONTRAT = {
    "geste": "bloc sous cliquet dont le code a bouge apres la javadoc",
    "population": "PRODUCTION + TESTS",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_scripts.py#test_loupe_4359_javadoc_vieillie",
    "decision": "ADR 4359",
}


if __name__ == "__main__":
    # AVANT tout le reste : un contrat s imprime sans rien lire et sans rien exiger.
    if "--contrat" in sys.argv:
        sys.exit(
            imprime_contrat(
                pathlib.Path(__file__).resolve().relative_to(RACINE_DEPOT).as_posix(), CONTRAT
            )
        )
    sys.exit(
        loupe(
            ADR,
            "blocs sous cliquet dont le code a bougé après la javadoc",
            candidats(),
            # `fichiers()` n'a pas de defaut : lui passer RACINE, et non rien. Une signature dont le
            # defaut est une constante et non `None` est l'une des divergences qui ont casse trois
            # gardes quand la PR #5040 a tente de traiter cette famille par motif.
            lus=len(fichiers(RACINE)),
        )
    )
