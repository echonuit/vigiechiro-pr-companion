#!/usr/bin/env python3
"""Loupe de l'ADR 0020 - Écrire sur la plateforme : ne rien inventer, ne rien effacer.

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
from _commun import RACINE_DEPOT, RACINES, imprime_contrat, loupe

# Les DEUX arbres (ADR 4488). Une loupe aveugle a la moitie du code a le meme defaut qu un garde
# qui l est : elle surfacerait moins sans jamais le dire. Mesure d ouverture, les deux repertoires
# de test existant bel et bien (23 et 797 fichiers) : ZERO candidat cote test, l extension ne coute
# donc rien et ferme la question.
PAQUET = "fr/univ_amu/iut/commun/api"
ARBRES = tuple(arbre / PAQUET for arbre in RACINES)
API = ARBRES[0]

# Verbes d'écriture, dans le nom d'une méthode publique du client.
ECRITURE = re.compile(
    r"\b(?:public|protected)\b[^;{]*\b"
    r"(creer|publier|deposer|envoyer|ecrire|poster|patcher|"
    r"mettreAJour|corriger|supprimer|effacer|televerser|attacher)\w*\s*\(",
    re.I,
)


def fichiers(api: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unités que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu'il RETENAIT. Un ciblage manqué
    donnait donc zéro suspect sur zéro fichier, et ce zéro passait pour un succès.
    """
    # Le retour anticipe sur un repertoire absent devient un FILTRE : avec deux arbres, l un peut
    # manquer sans que l autre cesse d etre lu, et une racine de temoin n a pas a exister non plus.
    arbres = [api] if api else list(ARBRES)
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java"))


def candidats(api: pathlib.Path | None = None) -> list[str]:
    trouves = []
    for source in fichiers(api):
        for numero, ligne in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
            if ECRITURE.search(ligne):
                trouves.append(f"{source}:{numero}  {ligne.strip()[:100]}")
    return trouves


CONTRAT = {
    "geste": "surface d ecriture vers la plateforme",
    "population": "PRODUCTION + TESTS",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_scripts.py#test_loupe_0020",
    "decision": "ADR 0020",
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
        loupe(
            "0020",
            "surface d'écriture vers la plateforme (à confronter aux 3 règles)",
            candidats(),
            lus=len(fichiers()),
        )
    )
