#!/usr/bin/env python3
"""Dit si le diff courant touche le **contrat de systeme de fichiers** (#3525, porte du bash).

## Pourquoi ce script existe plutot qu un filtre `paths:`

Un `paths:` aurait empeche le job de demarrer sur la plupart des demandes. Or ce depot n a **aucune
protection de branche** : un job absent du recapitulatif est indiscernable d un job vert, y compris
pour une boucle d attente qui lit « aucune rouge, rien en cours ». C est le motif de l ADR 2748 - un
dispositif qui peut ne rien verifier doit le dire.

Le job tourne donc toujours, et **cette etape** decide. Quand elle rend `non`, le job finit vert en
ayant ecrit pourquoi : c est un silence explicite, pas une absence.

Le mecanisme - base, diff, resume - vit desormais dans `_portee.py`, que six autres jobs partagent
depuis le chantier #5294. Ce fichier ne garde que ce qui lui est propre : la liste, et l appariement
exact qu elle demande.

## Ce qu il regarde

Les classes dont le comportement depend du systeme sous-jacent, et leurs tests : renommage atomique,
verrou de fichier, extraction ZIP, copie et deplacement entre volumes, emplacement de la
configuration d amorcage. Plus le workflow et ce script eux-memes - sans quoi une modification du
dispositif ne serait jamais eprouvee par le dispositif.

## La liste reste UNE LIGNE PAR CHEMIN, et ce n est pas un detail de forme

`verifie_inventaires_ci.py` lit ce bloc avec le motif `^(src/test/java/\\S+\\.java)$` pour confronter
les chemins surveilles aux classes que la matrice de `maven.yml` joue. Emballer ces chemins
autrement - une liste Python d elements sur une meme ligne, par exemple - rendrait ce garde-la MUET
sans qu il rougisse. La chaine ci-dessous garde donc la forme que le bash lui donnait.

Depuis #5296, ce garde ne lit QUE le bloc `SURVEILLES`, et non le fichier entier. Il lisait tout, si
bien qu une ligne de cette forme ecrite n importe ou - un exemple de docstring, un commentaire de
conception - devenait un test « surveille » qu il fallait ajouter au `-Dtest=` de `maven.yml`. Et si
la classe citee n existait pas, ce `-Dtest=` rendait `Tests run: 0`, c est-a-dire un faux vert.

Usage : python3 .github/scripts/porte_sur_le_contrat_de_fichiers.py
"""

from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _portee import ajoute, base_de_comparaison, fichiers_modifies, resume, sans_base

TITRE = "Portée · contrat-fichiers"

# Chemins surveilles, un par ligne. Le nom de CHAQUE entree est une decision : ajouter une classe
# ici, c est declarer que son comportement depend du systeme. Le faire a la legere rallonge le gate
# de trois plateformes ; l oublier laisse un chemin de disque non verifie.
#
# `GestesFichiers` et `TailleFichier` y figurent depuis #3794, et ils meritent un mot : ce ne sont pas
# des classes qui *font* du disque, ce sont les **points d injection** par lesquels tout le reste y
# accede. Leurs implementations par defaut SONT le comportement reel du produit. Changer l une
# d elles change ce que font les huit classes ci-dessus sur les trois plateformes, et jusqu ici cela
# ne declenchait rien.
#
# Le garde des inventaires ne pouvait pas le trouver : il confronte des inventaires, et qu une classe
# MERITE d etre surveillee reste un jugement. C est pourquoi cette entree-ci est posee a la main.
SURVEILLES = """
src/main/java/fr/univ_amu/iut/commun/model/EcritureAtomique.java
src/main/java/fr/univ_amu/iut/commun/model/ConfigurationAmorcage.java
src/main/java/fr/univ_amu/iut/commun/persistence/VerrouWorkspace.java
src/main/java/fr/univ_amu/iut/commun/persistence/ArborescenceFichiers.java
src/main/java/fr/univ_amu/iut/commun/persistence/BasculeRacines.java
src/main/java/fr/univ_amu/iut/commun/persistence/RestaurationComplete.java
src/main/java/fr/univ_amu/iut/importation/model/ExtracteurZip.java
src/main/java/fr/univ_amu/iut/importation/model/BornesExtraction.java
src/main/java/fr/univ_amu/iut/commun/persistence/GestesFichiers.java
src/main/java/fr/univ_amu/iut/commun/model/TailleFichier.java
src/test/java/fr/univ_amu/iut/commun/model/EcritureAtomiqueTest.java
src/test/java/fr/univ_amu/iut/commun/model/ConfigurationAmorcageTest.java
src/test/java/fr/univ_amu/iut/commun/persistence/VerrouWorkspaceTest.java
src/test/java/fr/univ_amu/iut/commun/persistence/ArborescenceFichiersTest.java
src/test/java/fr/univ_amu/iut/commun/persistence/RestaurationCompleteTest.java
src/test/java/fr/univ_amu/iut/importation/ExtracteurZipTest.java
src/test/java/fr/univ_amu/iut/importation/ExtracteurZipQuotasTest.java
src/test/java/fr/univ_amu/iut/importation/BornesExtractionTest.java
.github/workflows/maven.yml
.github/scripts/porte_sur_le_contrat_de_fichiers.py
.github/scripts/_portee.py
"""


def juger() -> int:
    """Le verdict, et ce que l etape en dit dans le resume."""
    surveilles = {l for l in SURVEILLES.splitlines() if l}
    base = base_de_comparaison()

    if not base:
        return sans_base(TITRE)

    touches = [m for m in fichiers_modifies(base) if m in surveilles]

    if touches:
        lignes = [f"Ce diff touche {len(touches)} fichier(s) surveillé(s) :", ""]
        lignes += [f"- `{t}`" for t in touches]
    else:
        lignes = [
            f"**Sans objet** : aucun des {len(surveilles)} chemins surveillés n'apparaît dans ce",
            "diff, les tests de contrat ne sont pas rejoués sur les trois plateformes.",
            "",
            "Le job s'exécute quand même, et le dit : un job absent du récapitulatif se lirait comme",
            "un job vert, dans un dépôt qui n'a aucune protection de branche.",
        ]
    resume(TITRE, lignes)

    if touches:
        for t in touches:
            print(f"  · {t}")
        ajoute("GITHUB_OUTPUT", "concerne=oui")
    else:
        print(f"Aucun des {len(surveilles)} chemins surveillés dans ce diff : sans objet.")
        ajoute("GITHUB_OUTPUT", "concerne=non")
    return 0


if __name__ == "__main__":
    sys.exit(juger())
