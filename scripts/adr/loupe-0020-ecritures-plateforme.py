#!/usr/bin/env python3
"""Loupe de l'ADR 0020 — Écrire sur la plateforme : ne rien inventer, ne rien effacer.

Cette décision est `humaine` : aucun motif ne dit si une requête « invente » ou « efface ». Mais on
peut lister la SURFACE D'ÉCRITURE - les méthodes du client d'API qui envoient quelque chose au serveur -
pour qu'un relecteur les confronte une à une aux trois règles de l'ADR pendant la passe humaine d'un
chantier qui touche à l'API.

Ce n'est pas une liste d'infractions : c'est la liste des endroits à regarder. Le pattern est
volontairement large (méthodes dont le nom évoque une écriture) et s'affine au fil des cas connus.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import loupe  # noqa: E402

API = pathlib.Path("src/main/java/fr/univ_amu/iut/commun/api")

# Verbes d'écriture, dans le nom d'une méthode publique du client.
ECRITURE = re.compile(
    r"\b(?:public|protected)\b[^;{]*\b"
    r"(creer|publier|deposer|envoyer|ecrire|poster|patcher|"
    r"mettreAJour|corriger|supprimer|effacer|televerser|attacher)\w*\s*\(",
    re.I,
)


def candidats() -> list[str]:
    trouves = []
    if not API.exists():
        return trouves
    for source in sorted(API.rglob("*.java")):
        for numero, ligne in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
            if ECRITURE.search(ligne):
                trouves.append(f"{source}:{numero}  {ligne.strip()[:100]}")
    return trouves


if __name__ == "__main__":
    sys.exit(loupe("0020", "surface d'écriture vers la plateforme (à confronter aux 3 règles)", candidats()))
