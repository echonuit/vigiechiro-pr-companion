#!/usr/bin/env python3
"""ADR 2493 - Une modale à révélation suit la croissance de son contenu.

Une modale est dimensionnée à son ouverture, sur le contenu visible à cet instant. Un bandeau de retour
qui paraît ensuite (BandeauRetour, LibelleRetour, un `setManaged(true)`) agrandit la mise en page sans
agrandir la fenêtre : les boutons du bas passent sous la ligne de flottaison. `Modales.suivreLaCroissance`
corrige cela (#1534), mais chaque modale doit penser à le câbler.

Pourquoi « probable » et non « certaine » : « est-ce une modale ? » est approché par le nom du
controller (`*Modale*Controller`) - un popup nommé autrement serait manqué ; et « révèle-t-elle un
bandeau qui pousse des boutons ? » se lit à l'intention. Le script remonte les controllers de modale qui
RÉVÈLENT (BandeauRetour / LibelleRetour / setManaged) mais n'appellent PAS suivreLaCroissance : ce sont
les suspects qu'un humain confirme. Trois ont été trouvés à l'audit (#2493) : Rattachement, ModalePoint,
ModaleSite - le même défaut que la connexion (#2486), à trois exemplaires.
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
VUES = PRODUCTION

# Un controller qui MONTRE une modale : proxy par le nom (toutes les modales du dépôt le portent).
MODALE = re.compile(r"Modale.*Controller\.java$")

# Les façons de RÉVÉLER du contenu après l'ouverture (bandeau de retour, bascule managed).
REVELE = re.compile(
    r"LibelleRetour\.installer|BandeauRetour|setManaged\(true\)|managedProperty\(\)\.bind"
)


def suspects(vues: pathlib.Path | None = None) -> list[str]:
    trouves = []
    arbres = [vues] if vues else list(RACINES)
    for source in sorted(f for a in arbres if a.is_dir() for f in a.rglob("*Controller.java")):
        if not MODALE.search(source.name):
            continue
        code = sans_commentaires_java(source.read_text(encoding="utf-8"))
        revele = REVELE.search(code)
        cable = "suivreLaCroissance" in code
        if revele and not cable:
            trouves.append(f"{source} : révèle un bandeau mais ne suit pas la croissance")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("2493", "modale qui révèle un bandeau sans suivreLaCroissance", suspects()))
