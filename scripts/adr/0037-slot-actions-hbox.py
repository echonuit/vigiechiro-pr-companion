#!/usr/bin/env python3
"""ADR 0037 - Une barre d'actions plie, elle ne tronque pas : le slot d'actions est un FlowPane.

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
from _commun import (
    PRODUCTION,
    RACINE_DEPOT,
    RACINES,
    imprime_contrat,
    rapporte,
    sans_commentaires_xml,
)

# La styleClass ou l'fx:id, porté par la balise HBox elle-même, évoque un slot d'actions.
# Les DEUX arbres (#4462). Aucune decision n avait restreint ce garde a la production : il est ne
# avant que la question ne se pose. La dette de qualite ne connait pas de code de seconde zone, et
# la mesure d ouverture a rendu ZERO suspect dans l arbre de test - l extension ne coute donc rien
# et ferme la question pour de bon, la ou la laisser ouverte laissait un angle mort grandir.
SOURCES = PRODUCTION

SLOT = re.compile(r'<HBox\b[^>]*(?:styleClass|fx:id)="[^"]*action[^"]*"', re.I | re.S)


def fichiers(sources: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5015).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu il RETENAIT : un ciblage
    manque donnait zero suspect sur zero fichier, et ce zero passait pour un succes.
    """
    arbres = [sources] if sources else list(RACINES)
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.fxml"))


def suspects(sources: pathlib.Path | None = None) -> list[str]:
    # Commentaires FXML retirés d'abord : un <HBox ...action...> en commentaire serait un faux positif.
    # Retrait mutualisé dans _commun (défaut trouvé sur 0010/0046 en clôture).
    trouves = []
    for f in fichiers(sources):
        texte = sans_commentaires_xml(f.read_text(encoding="utf-8"))
        for balise in SLOT.finditer(texte):
            ligne = texte[: balise.start()].count("\n") + 1
            extrait = re.sub(r"\s+", " ", balise.group())[:90]
            trouves.append(f"{f}:{ligne}  {extrait}")
    return trouves


CONTRAT = {
    "geste": "slot d actions declare en HBox au lieu de FlowPane",
    "population": "PRODUCTION + TESTS",
    "dispositif": "cliquet",
    "seuil": "2, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_0037_slot_actions",
    "decision": "ADR 0037",
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
        rapporte(
            "0037",
            "slot d'actions déclaré en HBox au lieu de FlowPane",
            suspects(),
            lus=len(fichiers()),
        )
    )
