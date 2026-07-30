#!/usr/bin/env python3
"""Loupe de l'ADR 0044 - Le mécanisme de parallélisme suit la nature de l'attente.

Cette décision est `humaine` : aucun motif ne dit si une attente est de l'I/O (→ threads virtuels, on
peut en lancer des milliers, ils dorment pendant la latence réseau ou disque) ou du calcul (→ pool borné
au nombre de cœurs, sinon on sature le CPU sans rien gagner). Mais on peut lister la SURFACE DE
PARALLÉLISME - les endroits où un mécanisme est CHOISI (threads virtuels, threads plateforme, pools
d'exécuteurs, streams parallèles) - pour qu'un relecteur confronte chacun à la nature de son attente
pendant la passe humaine d'un chantier qui touche à la concurrence.

Ce n'est pas une liste d'infractions : c'est la liste des endroits à regarder. Les commentaires sont
retirés d'abord - un Javadoc qui *cite* `Thread.ofVirtual()` pour l'expliquer n'en crée pas un, et le
compter noierait la surface (le défaut exact de la passe 1 de clôture du chantier ADR). Le pattern est
volontairement large et s'affine au fil des cas connus.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import loupe, sans_commentaires_java  # noqa: E402

SRC = pathlib.Path("src/main/java/fr/univ_amu/iut")

# Choix d'un mécanisme de parallélisme : création de threads/exécuteurs, ou stream parallèle.
MECANISME = re.compile(
    r"Thread\.ofVirtual\(|Thread\.ofPlatform\(|"
    r"Executors\.new\w+\(|newVirtualThreadPerTaskExecutor|"
    r"new\s+ForkJoinPool|\.parallelStream\(|\.parallel\(\)"
)


def candidats(racine: pathlib.Path = SRC) -> list[str]:
    trouves = []
    if not racine.exists():
        return trouves
    for source in sorted(racine.rglob("*.java")):
        texte = sans_commentaires_java(source.read_text(encoding="utf-8"))
        for numero, ligne in enumerate(texte.splitlines(), 1):
            if MECANISME.search(ligne):
                trouves.append(f"{source}:{numero}  {ligne.strip()[:100]}")
    return trouves


if __name__ == "__main__":
    sys.exit(
        loupe(
            "0044",
            "surface de parallélisme (à confronter à la nature de l'attente : I/O → threads virtuels, calcul → pool borné)",
            candidats(),
        )
    )
