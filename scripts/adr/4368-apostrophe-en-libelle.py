#!/usr/bin/env python3
"""Garde sur l apostrophe courbe dans un libelle montre a l utilisateur.

Le depot ecrit l apostrophe droite. La regle est dans `CONTRIBUTING.md`, section « Le registre »,
et rien ne la tenait.

**Pourquoi ce garde ne regarde QUE les chaines litterales de `src/main/java`.** L apostrophe courbe
se compte partout, mais elle ne NUIT que la ou elle sort du depot. Mesure du 2026-08-24 : 188
occurrences dans 68 fichiers, dont 97 dans le brief, 65 dans des commentaires et de la javadoc de
`src/main`, 13 dans les tests, une dans un atelier, et DOUZE dans des chaines litterales. Seules ces
douze atteignaient un ecran, et deux ecrans affichaient alors deux apostrophes differentes pour le
meme mot. Elles sont corrigees, et la zone est tenue a zero.

Un cliquet a 188 aurait coute une relecture de 68 fichiers pour un defaut qui n en concernait que
trois. Ce garde vaut par ce qu il n examine pas.

**Ce que le releve ne lit pas, et pourquoi.**

- Les commentaires et la javadoc. Ils ne sortent pas du depot. La regle du registre vaut pour eux,
  la relecture la tient, et l article A31 le dit.
- Le brief. C est un document de conception repris tel quel, et sa prose n est pas un libellé.
- Les tests. Une assertion cite le libelle qu elle verifie : si le libelle est droit, l assertion
  l est aussi, et c est le libelle qui commande.
- Les chaines a guillemets SIMPLES. Java n en a pas pour du texte : `'x'` est un caractere.

**La cecite declaree.** L appartenance a une chaine se decide sur les guillemets DOUBLES, par une
expression qui saute les echappements. Une chaine de texte en bloc (`\"\"\"`) n est pas reconnue :
le depot n en emploie aucune qui porte une apostrophe, et le jour ou il le fera, ce garde ne la
verra pas. C est ecrit ici plutot que tu.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

ADR = "4368"
RACINE = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = RACINE / "src" / "main" / "java"

COURBE = "’"
CHAINE = re.compile(r'"(?:[^"\\]|\\.)*"')


def suspects(racine: pathlib.Path = None) -> list[str]:
    """Les apostrophes courbes vivant dans une chaine litterale, une par occurrence."""
    trouves = []
    for f in sorted((racine or PRODUCTION).rglob("*.java")):
        for n, ligne in enumerate(f.read_text(encoding="utf-8").split("\n"), 1):
            if COURBE not in ligne:
                continue
            for m in CHAINE.finditer(ligne):
                if COURBE in m.group(0):
                    for _ in range(m.group(0).count(COURBE)):
                        trouves.append(f"{f.name}:{n}  {ligne.strip()[:78]}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte(ADR, "apostrophe courbe dans un libelle montre", suspects(), apercu=15))
