#!/usr/bin/env python3
"""ADR 3053 - Une capture exige son libellé plutôt que de s'abstenir.

Le motif : dans un outil de capture, une recherche qui se termine par `findFirst().ifPresent(...)`.
Ces outils désignent leurs contrôles par le libellé affiché (« Lieu », « Import », « Taxon parent »).
Quand la recherche s'abstient, l'aperçu est produit SANS le geste et publié sous une légende affirmant
le contraire - une capture fausse, que rien ne distingue d'une bonne, et qui reste dans la galerie
jusqu'à ce qu'un humain la regarde.

Pourquoi « probable » et non « certaine » : un outil peut avoir une raison légitime de s'abstenir sur
une valeur réellement facultative. Le script ne peut pas trancher ; il nomme les suspects et verrouille
leur nombre. Le cliquet vaut 0 : les quatre cas connus sont corrigés, et le prochain sera une décision.

Pourquoi ce périmètre : hors des outils de capture, `ifPresent` est l'usage normal d'un Optional. Étendre
le motif à tout le dépôt produirait un bruit qu'on finirait par désactiver, ce qui coûte plus cher que
l'absence de cliquet.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import PRODUCTION, RACINES, rapporte, sans_commentaires_java  # noqa: E402

# Les DEUX arbres (#4462). Aucune decision n avait restreint ce garde a la production : il est ne
# avant que la question ne se pose. La dette de qualite ne connait pas de code de seconde zone, et
# la mesure d ouverture a rendu ZERO suspect dans l arbre de test - l extension ne coute donc rien
# et ferme la question pour de bon, la ou la laisser ouverte laissait un angle mort grandir.
SOURCES = PRODUCTION

# La forme exacte qu'avaient les quatre outils : on cherche, et on ne fait rien si on ne trouve pas.
ABSTENTION = re.compile(r"\.findFirst\(\)\s*\.ifPresent\(", re.S)


def est_outil_de_capture(source: pathlib.Path) -> bool:
    """Un `Capture*.java` sous un paquet `outils`.

    Les deux conditions sont nécessaires : le nom seul attraperait des classes de test hors de ce scan,
    et le paquet seul attraperait les graines et les aides qui n'écrivent aucune image.
    """
    return source.name.startswith("Capture") and source.parent.name == "outils"


def suspects(sources: pathlib.Path | None = None) -> list[str]:
    # Commentaires retirés d'abord : un exemple cité dans un Javadoc n'est pas du code (c'est le cas de
    # l'en-tête d'ApercuFx, qui décrit précisément le motif qu'il remplace).
    trouves = []
    arbres = [sources] if sources else list(RACINES)
    for source in sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java")):
        if not est_outil_de_capture(source):
            continue
        texte = sans_commentaires_java(source.read_text(encoding="utf-8"))
        for abstention in ABSTENTION.finditer(texte):
            ligne = texte[: abstention.start()].count("\n") + 1
            trouves.append(
                f"{source}:{ligne}  recherche qui s'abstient (voir ApercuFx.exigerParLibelle)"
            )
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("3053", "capture : le geste qui ne se fait pas", suspects()))
