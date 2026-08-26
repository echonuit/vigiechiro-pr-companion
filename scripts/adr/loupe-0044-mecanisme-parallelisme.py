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
from _commun import RACINES, loupe, sans_commentaires_java  # noqa: E402

# Les DEUX arbres (ADR 4488). Une loupe aveugle a la moitie du code a le meme defaut qu un garde
# qui l est : elle surfacerait moins sans jamais le dire. Mesure d ouverture, les deux repertoires
# de test existant bel et bien (23 et 797 fichiers) : ZERO candidat cote test, l extension ne coute
# donc rien et ferme la question.
PAQUET = "fr/univ_amu/iut"
ARBRES = tuple(arbre / PAQUET for arbre in RACINES)
SRC = ARBRES[0]

# Choix d'un mécanisme de parallélisme : création de threads/exécuteurs, ou stream parallèle.
MECANISME = re.compile(
    r"Thread\.ofVirtual\(|Thread\.ofPlatform\(|"
    r"Executors\.new\w+\(|newVirtualThreadPerTaskExecutor|"
    r"new\s+ForkJoinPool|\.parallelStream\(|\.parallel\(\)"
)


def candidats(racine: pathlib.Path | None = None) -> list[str]:
    trouves = []
    # Le retour anticipe sur un repertoire absent devient un FILTRE : avec deux arbres, l un peut
    # manquer sans que l autre cesse d etre lu, et une racine de temoin n a pas a exister non plus.
    arbres = [racine] if racine else list(ARBRES)
    for source in sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java")):
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
