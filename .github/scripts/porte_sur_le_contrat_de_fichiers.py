#!/usr/bin/env python3
"""Dit si le diff courant touche le **contrat de systeme de fichiers** (#3525, porte du bash).

## Pourquoi ce script existe plutot qu un filtre `paths:`

Un `paths:` aurait empeche le job de demarrer sur la plupart des PR. Or ce depot n a **aucune
protection de branche** : un job absent du recapitulatif est indiscernable d un job vert, y compris
pour une boucle d attente qui lit « aucune rouge, rien en cours ». C est le motif de l ADR 2748 - un
dispositif qui peut ne rien verifier doit le dire.

Le job tourne donc toujours, et **cette etape** decide. Quand elle rend `non`, le job finit vert en
ayant ecrit pourquoi : c est un silence explicite, pas une absence.

## Ce qu il regarde

Les classes dont le comportement depend du systeme sous-jacent, et leurs tests : renommage atomique,
verrou de fichier, extraction ZIP, copie et deplacement entre volumes, emplacement de la
configuration d amorcage. Plus le workflow et ce script eux-memes - sans quoi une modification du
dispositif ne serait jamais eprouvee par le dispositif.

## La liste reste UNE LIGNE PAR CHEMIN, et ce n est pas un detail de forme

`verifie_inventaires_ci.py` la lit avec le motif `^(src/test/java/\\S+\\.java)$` pour confronter les
chemins surveilles aux classes que la matrice de `maven.yml` joue. Emballer ces chemins autrement -
une liste Python d elements sur une meme ligne, par exemple - rendrait ce garde-la MUET sans qu il
rougisse. La chaine ci-dessous garde donc la forme que le bash lui donnait.

Usage : python3 .github/scripts/porte_sur_le_contrat_de_fichiers.py
"""

from __future__ import annotations

import os
import subprocess
import sys

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
"""


def _git(*arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["git", *arguments], capture_output=True, text=True, check=False)


def base_de_comparaison() -> str:
    """Le point de divergence pour une PR, le commit precedent sinon.

    On demande a GitHub le SHA de base plutot que de le CALCULER par `merge-base` : le calcul
    exigeait l historique des DEUX cotes, donc un `fetch-depth: 0` au checkout - et ce clone integral
    partait en vrille jusqu a epuiser les vingt minutes du job, 1 199 s et 1 200 s le meme jour sur
    ubuntu ET macos, quand l etape prend 3 s a profondeur 1 (#4440).
    """
    depuis_la_forge = os.environ.get("GITHUB_BASE_SHA")
    if depuis_la_forge:
        _git("fetch", "--no-tags", "--depth=1", "origin", depuis_la_forge)
        if _git("cat-file", "-e", f"{depuis_la_forge}^{{commit}}").returncode == 0:
            return depuis_la_forge

    # Repli sur le calcul, hors PR ou si le SHA n arrive pas. La profondeur peut alors ne pas
    # suffire, et l absence de base fait VERIFIER TOUT plutot que conclure au silence.
    branche = os.environ.get("GITHUB_BASE_REF")
    if branche:
        _git("fetch", "--no-tags", "--depth=50", "origin", branche)
        calcul = _git("merge-base", "HEAD", f"origin/{branche}")
        if calcul.returncode == 0 and calcul.stdout.strip():
            return calcul.stdout.strip()

    precedent = _git("rev-parse", "HEAD~1")
    return precedent.stdout.strip() if precedent.returncode == 0 else ""


def _ajoute(variable: str, ligne: str) -> None:
    """Ecrit dans le fichier que la forge designe, ou sur la sortie standard hors CI."""
    chemin = os.environ.get(variable)
    if chemin:
        with open(chemin, "a", encoding="utf-8") as f:
            f.write(ligne + "\n")
    else:
        print(ligne)


def juger() -> int:
    """Le verdict, et ce que l etape en dit dans le resume."""
    surveilles = {l for l in SURVEILLES.splitlines() if l}
    base = base_de_comparaison()

    if not base:
        print(
            "Base de comparaison introuvable : on vérifie tout plutôt que de conclure au silence."
        )
        _ajoute("GITHUB_OUTPUT", "concerne=oui")
        return 0

    diff = _git("diff", "--name-only", base, "HEAD")
    modifies = diff.stdout.splitlines() if diff.returncode == 0 else []
    touches = [m for m in modifies if m in surveilles]

    resume = ["### Contrat de système de fichiers", ""]
    if touches:
        resume.append(f"Ce diff touche {len(touches)} fichier(s) surveillé(s) :")
        resume.append("")
        resume += [f"- `{t}`" for t in touches]
    else:
        resume.append(
            "**Sans objet** : aucun fichier surveillé dans ce diff, les tests de contrat ne sont pas"
        )
        resume.append("rejoués sur les trois plateformes.")
        resume.append("")
        resume.append(
            "Le job s'exécute quand même, et le dit : un job absent du récapitulatif se lirait comme"
        )
        resume.append("un job vert, dans un dépôt qui n'a aucune protection de branche.")
    for ligne in resume:
        _ajoute("GITHUB_STEP_SUMMARY", ligne)

    if touches:
        for t in touches:
            print(f"  · {t}")
        _ajoute("GITHUB_OUTPUT", "concerne=oui")
    else:
        print("Aucun fichier surveillé dans ce diff : sans objet.")
        _ajoute("GITHUB_OUTPUT", "concerne=non")
    return 0


if __name__ == "__main__":
    sys.exit(juger())
