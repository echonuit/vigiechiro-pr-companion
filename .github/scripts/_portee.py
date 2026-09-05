#!/usr/bin/env python3
"""Le mecanisme d une PORTEE de job : quelle base, quel diff, et comment le silence s ecrit.

## Pourquoi ce module existe

`porte_sur_le_contrat_de_fichiers.py` a etabli le patron : un `paths:` aurait empeche le job de
demarrer, or un job absent du recapitulatif est indiscernable d un job vert dans un depot sans
protection de branche (ADR 2748). Le job tourne donc toujours, et une ETAPE decide.

Le chantier #5294 generalise ce patron a six autres jobs. Recopier `base_de_comparaison()` sept fois
serait recopier sept fois la lecon de #4440 - le clone integral qui partait en vrille jusqu a
epuiser les vingt minutes du job, 1 199 s et 1 200 s le meme jour sur ubuntu ET macos, quand l etape
prend 3 s a profondeur 1.

Ce module extrait donc le mecanisme, et RIEN d autre : ce qu un job surveille, et la facon dont il
apparie un chemin, restent chez l appelant. C est le parti de `_forge.py`, pour la meme raison - les
bords different d un garde a l autre, le centre non.

## Ce qu il n est pas

Il n a aucun point d entree et ne juge rien : il n est donc pas un garde, et n a pas sa place au
tableau des gardes de `dev-docs/ci-cd-release.md`. Le souligne initial le dit, et l inventaire le
confirme par son AST plutot que par son nom.

## Le repli, qui est la moitie du dispositif

`base_de_comparaison()` ne rend JAMAIS une base fausse : elle rend une base, ou rien. Et sans base,
l appelant verifie TOUT plutot que de conclure au silence. Le defaut penche du cote couteux, jamais
du cote muet.

| declencheur | ce qu on obtient | verdict |
|---|---|---|
| demande (meme depot, ou fork) | le SHA que la forge fournit | vrai calcul |
| poussee sur `main` | rien | on verifie tout |
| `workflow_dispatch` | rien | on verifie tout |
| forge en panne reseau | rien | on verifie tout |

`git diff <base> HEAD` fonctionne sur un clone superficiel : on compare deux arbres, on ne cherche
pas d ancetre commun. C est tout l interet d avoir abandonne `merge-base`.

Sur une demande, `HEAD` est le commit de FUSION que `actions/checkout` pose : le diff inclut donc
les commits de `main` arrives depuis. C est un sur-ensemble, donc du bon cote.
"""

from __future__ import annotations

import os
import subprocess


def git(*arguments: str) -> subprocess.CompletedProcess[str]:
    """Un appel a git qui ne leve pas : l appelant lit le code de retour."""
    return subprocess.run(["git", *arguments], capture_output=True, text=True, check=False)


def base_de_comparaison() -> str:
    """Le point de divergence pour une demande, le commit precedent sinon, ou la chaine vide.

    On demande a la forge le SHA de base plutot que de le CALCULER par `merge-base` : le calcul
    exigeait l historique des DEUX cotes, donc un `fetch-depth: 0` au checkout (#4440).
    """
    depuis_la_forge = os.environ.get("GITHUB_BASE_SHA")
    if depuis_la_forge:
        git("fetch", "--no-tags", "--depth=1", "origin", depuis_la_forge)
        if git("cat-file", "-e", f"{depuis_la_forge}^{{commit}}").returncode == 0:
            return depuis_la_forge

    branche = os.environ.get("GITHUB_BASE_REF")
    if branche:
        git("fetch", "--no-tags", "--depth=50", "origin", branche)
        calcul = git("merge-base", "HEAD", f"origin/{branche}")
        if calcul.returncode == 0 and calcul.stdout.strip():
            return calcul.stdout.strip()

    precedent = git("rev-parse", "HEAD~1")
    return precedent.stdout.strip() if precedent.returncode == 0 else ""


def ajoute(variable: str, ligne: str) -> None:
    """Ecrit dans le fichier que la forge designe, ou sur la sortie standard hors CI."""
    chemin = os.environ.get(variable)
    if chemin:
        with open(chemin, "a", encoding="utf-8") as f:
            f.write(ligne + "\n")
    else:
        print(ligne)


def fichiers_modifies(base: str) -> list[str]:
    """Les chemins que ce diff touche, ou une liste vide si git ne sait pas repondre."""
    diff = git("diff", "--name-only", base, "HEAD")
    return diff.stdout.splitlines() if diff.returncode == 0 else []


def resume(titre: str, lignes: list[str]) -> None:
    """Le bloc que l etape laisse au recapitulatif, sous son propre titre.

    Le titre porte le nom du job : sept portees ecrivant dans le meme recapitulatif s y melangeraient
    sans lui.
    """
    for ligne in [f"### {titre}", ""] + lignes:
        ajoute("GITHUB_STEP_SUMMARY", ligne)


def sans_base(titre: str) -> int:
    """Le cas ou l on verifie tout par ignorance, ECRIT plutot que taise.

    Ce cas sortait en `concerne=oui` sans rien ecrire au recapitulatif. C etait l ADR 2748 a l
    envers : le seul cas ou le dispositif avoue ne pas savoir etait justement celui qu on ne voyait
    pas. Un lecteur du recapitulatif concluait que la portee avait tranche, alors qu elle avait
    renonce.
    """
    motif = "Base de comparaison introuvable : on vérifie tout plutôt que de conclure au silence."
    print(motif)
    resume(
        titre,
        [
            f"**Tout est vérifié.** {motif}",
            "",
            "C'est le cas d'une poussée sur `main`, d'un déclenchement manuel, ou d'une forge qui",
            "n'a pas répondu. Le défaut penche du côté coûteux, jamais du côté muet.",
        ],
    )
    ajoute("GITHUB_OUTPUT", "concerne=oui")
    return 0
