#!/usr/bin/env python3
"""ADR 0037 — Une barre d'actions plie, elle ne tronque pas : le slot d'actions est un FlowPane.

Pourquoi « probable » et non « certaine » : une `HBox` n'est pas fautive en soi. L'ADR ne vise que le
SLOT D'ACTIONS d'un écran - la rangée de boutons dont le nombre croît au fil des fonctionnalités, et qui
doit renvoyer à la ligne plutôt qu'ellipser ses libellés. Une `HBox` qui aligne deux éléments fixes n'a
rien à plier. Distinguer les deux demande de savoir ce que la rangée porte : c'est un humain qui
tranche, le script ne fait que remonter les `HBox` dont la classe ou l'identifiant évoque un slot
d'actions.

Le repère : le slot partagé porte `entete-actions` (migré en FlowPane par l'ADR). Les variantes
`barre-actions`, `cartes-actions` et tout `fx:id` contenant « action » sont les suspects à réexaminer.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

# La styleClass ou l'fx:id, porté par la balise HBox elle-même, évoque un slot d'actions.
SLOT = re.compile(r'<HBox\b[^>]*(?:styleClass|fx:id)="[^"]*action[^"]*"', re.I | re.S)

COMMENTAIRE = re.compile(r"<!--.*?-->", re.S)


def sans_commentaires(source: str) -> str:
    """Neutralise les commentaires FXML en préservant les sauts de ligne (numéros justes). Aucun
    <HBox ...action...> n'est commenté aujourd'hui, mais un script probable qui compterait un exemple
    en commentaire serait faux par construction - c'est le défaut trouvé sur 0010 et 0046 en clôture."""
    return COMMENTAIRE.sub(lambda m: re.sub(r"[^\n]", " ", m.group()), source)


def suspects() -> list[str]:
    trouves = []
    for f in sorted(pathlib.Path("src/main/java").rglob("*.fxml")):
        texte = sans_commentaires(f.read_text(encoding="utf-8"))
        for balise in SLOT.finditer(texte):
            ligne = texte[: balise.start()].count("\n") + 1
            extrait = re.sub(r"\s+", " ", balise.group())[:90]
            trouves.append(f"{f}:{ligne}  {extrait}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0037", "slot d'actions déclaré en HBox au lieu de FlowPane", suspects()))
