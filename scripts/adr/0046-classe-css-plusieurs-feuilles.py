#!/usr/bin/env python3
"""ADR 0046 — Une classe CSS a une seule feuille pour maison.

Pourquoi « probable » et non « certaine » : distinguer un CONCEPT DU PROJET (`.field-label`, une
étiquette de champ, qui ne doit vivre qu'à un endroit) d'un sélecteur de FRAMEWORK (`.label` de JavaFX,
`.ikonli-font-icon` d'Ikonli, légitimement stylé par plusieurs features) n'est pas décidable par le seul
nom. Le script écarte les sélecteurs de framework qu'il connaît et liste le reste ; l'humain tranche les
cas où un concept maison a vraiment deux définitions.

L'invariant est déjà violé : cinq classes maison (`.field-label`, `.field-hint`, `.section-label`,
`.modale-titre`, `.message-erreur`) sont définies à la fois dans `design.css` (le socle) et dans
`sites.css`, alors que l'ADR veut qu'une feature ne redéfinisse pas un concept transverse. C'est
précisément pour cela que la vérification est `probable` et non `certaine` : un test rouge dès sa
naissance serait une dette déguisée, le cliquet rend la dette visible et décroissante.
"""

import collections
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

# Sélecteurs de framework (JavaFX, Ikonli) : légitimement présents dans plusieurs feuilles, ce ne sont
# pas des concepts du projet. Les redéfinir par feature est un usage normal, pas la cible de l'ADR.
FRAMEWORK = {
    "root", "label", "text-field", "text-area", "button", "viewport", "list-view", "list-cell",
    "table-view", "table-row-cell", "scroll-pane", "scroll-bar", "menu-bar", "menu-item", "combo-box",
    "check-box", "radio-button", "titled-pane", "tab", "tab-pane", "tab-header-area", "tooltip",
    "hyperlink", "slider", "progress-bar", "separator", "cell", "content", "corner", "column-header",
    "toggle-button", "split-pane", "spinner", "choice-box", "date-picker", "context-menu", "tree-cell",
    "tree-view", "header-panel", "arrow", "arrow-button", "thumb", "track", "increment-button",
    "decrement-button", "text", "graphic-container", "ikonli-font-icon",
}

SELECTEUR = re.compile(r"(?:^|[\s,}])\.([A-Za-z_][\w-]*)\s*[,{:]")


def feuilles() -> list[pathlib.Path]:
    return sorted(pathlib.Path("src/main/resources").rglob("*.css")) + sorted(
        pathlib.Path("src/main/java").rglob("*.css")
    )


def suspects() -> list[str]:
    par_classe = collections.defaultdict(set)
    for f in feuilles():
        for nom in SELECTEUR.findall(f.read_text(encoding="utf-8")):
            if nom not in FRAMEWORK:
                par_classe[nom].add(f.name)
    trouves = []
    for classe, ou in sorted(par_classe.items()):
        if len(ou) > 1:
            trouves.append(f".{classe} : {', '.join(sorted(ou))}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0046", "classe maison définie dans plusieurs feuilles", suspects()))
