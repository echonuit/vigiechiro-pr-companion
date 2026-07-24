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
from _commun import rapporte, sans_commentaires_xml  # noqa: E402

# La styleClass ou l'fx:id, porté par la balise HBox elle-même, évoque un slot d'actions.
SOURCES = pathlib.Path("src/main/java")

SLOT = re.compile(r'<HBox\b[^>]*(?:styleClass|fx:id)="[^"]*action[^"]*"', re.I | re.S)


def suspects(sources: pathlib.Path = SOURCES) -> list[str]:
    # Commentaires FXML retirés d'abord : un <HBox ...action...> en commentaire serait un faux positif.
    # Retrait mutualisé dans _commun (défaut trouvé sur 0010/0046 en clôture).
    trouves = []
    for f in sorted(sources.rglob('*.fxml')):
        texte = sans_commentaires_xml(f.read_text(encoding="utf-8"))
        for balise in SLOT.finditer(texte):
            ligne = texte[: balise.start()].count("\n") + 1
            extrait = re.sub(r"\s+", " ", balise.group())[:90]
            trouves.append(f"{f}:{ligne}  {extrait}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0037", "slot d'actions déclaré en HBox au lieu de FlowPane", suspects()))
