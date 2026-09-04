#!/usr/bin/env python3
"""Trie les echecs d une execution de la suite, pour l etape 1 de #3526 (porte du bash en #5231).

## Ce qu il produit, et pourquoi c est le livrable

On ne savait pas ce que la suite donne sous Windows et macOS : elle n y avait jamais tourne. Avant de
programmer quoi que ce soit, il fallait un **chiffre** - combien d echecs, et de quelle nature.

Deux comptes separes, TestFX et le reste. Les 140 classes annotees
`@ExtendWith(ApplicationExtension.class)` sont 21 % de la suite, et leur comportement headless hors
Linux etait la grande inconnue : si elles echouent en masse pour une raison unique, elles noieraient
le signal des autres. Les compter a part rend les deux lisibles sans rien exclure.

Le tri se fait sur les **rapports** et non en relancant la suite par sous-ensembles : passer 536 noms
de classes a `-Dtest=` depasse la limite de ligne de commande de Windows, et un dispositif qui se
casse sur la plateforme qu il vient mesurer ne mesure rien.

## L ordre des deux verdicts, et pourquoi il compte

L interruption se juge AVANT les echecs. Une suite coupee rend zero echec sur les classes qu elle n a
pas atteintes, donc le test des echecs la laisserait passer en vert. C est exactement ce qui s est
produit : un job coupe a 92 minutes sur un plafond de 90 a rendu le meme tableau, avec 618 classes au
lieu de 758, sous le titre « toutes les classes de test » et sans un echec. Le compte etait exact ;
la phrase au-dessus ne l etait pas, et c est elle qu on lit (#4544).

Usage : python3 .github/scripts/trie_les_echecs_de_plateforme.py [rapports] [sources] [marqueur]
        python3 .github/scripts/trie_les_echecs_de_plateforme.py --auto-test
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

ANNOTATION = "@ExtendWith(ApplicationExtension.class)"
ECHECS = re.compile(r'failures="([0-9]+)"')
ERREURS = re.compile(r'errors="([0-9]+)"')
FAUTIVE = re.compile(r'failures="[1-9]|errors="[1-9]')


def classes_testfx(sources: pathlib.Path) -> set[str]:
    """Les classes TestFX, reconnues a leur extension JUnit."""
    if not sources.is_dir():
        return set()
    return {
        p.stem
        for p in sources.rglob("*.java")
        if ANNOTATION in p.read_text(encoding="utf-8", errors="ignore")
    }


def _nom_court(xml: pathlib.Path) -> str:
    return xml.stem.removeprefix("TEST-").rsplit(".", 1)[-1]


def compte(rapports: pathlib.Path, fx: set[str], mode: str) -> tuple[int, int, int]:
    """Classes, echecs, erreurs pour une famille.

    Un rapport tronque - un fork tue - n a pas d attribut, et vaut zero plutot que de faire echouer
    le denombrement : c est un compte, pas un verdict.
    """
    classes = echecs = erreurs = 0
    for xml in sorted(rapports.glob("TEST-*.xml")):
        est_fx = _nom_court(xml) in fx
        if (mode == "fx") != est_fx:
            continue
        texte = xml.read_text(encoding="utf-8", errors="ignore")
        classes += 1
        trouve = ECHECS.search(texte)
        echecs += int(trouve.group(1)) if trouve else 0
        trouve = ERREURS.search(texte)
        erreurs += int(trouve.group(1)) if trouve else 0
    return classes, echecs, erreurs


def trier(
    rapports: str | pathlib.Path = "target/surefire-reports",
    sources: str | pathlib.Path = "src/test/java",
    marqueur: str | pathlib.Path = "target/suite-est-allee-au-bout",
    classes_demandees: str | None = None,
    resume: str | None = None,
) -> int:
    """Le compte rendu, puis le verdict, et le code de sortie qui va avec."""
    dossier = pathlib.Path(rapports)
    if not dossier.is_dir():
        print(f"Aucun rapport dans {rapports} : la suite n'a pas produit de compte rendu.")
        return 1

    demandees = (
        classes_demandees if classes_demandees is not None else os.environ.get("CLASSES", "")
    )
    plateforme = os.environ.get("RUNNER_OS") or "cette plateforme"
    fx = classes_testfx(pathlib.Path(sources))
    cfx, efx, rfx = compte(dossier, fx, "fx")
    cre, ere, rre = compte(dossier, fx, "reste")
    lues = cfx + cre
    alle_au_bout = pathlib.Path(marqueur).is_file()

    lignes = [f"### Ce que la suite donne sur {plateforme}", ""]
    # Dire SUR QUOI le compte a porte. Sans cette ligne, « aucun echec » sur trois classes se
    # relirait comme « aucun echec » tout court (#3754).
    if demandees:
        lignes.append(
            f"⚠️ **Passage ciblé** : `{demandees}`. Ce compte ne dit **rien** du reste de la suite."
        )
    elif alle_au_bout:
        lignes.append("Passage **complet** : toutes les classes de test.")
    else:
        lignes.append(
            f"⚠️ **Passage INTERROMPU** : la suite ne s'est pas terminée. Les {lues} classes comptées"
        )
        lignes.append(
            "ci-dessous sont celles qui ont eu le temps de rendre un rapport. Ce compte ne dit **rien**"
        )
        lignes.append("des autres, et un passage tronqué n'est pas une preuve.")
    lignes += [
        "",
        "| Famille | Classes | Échecs | Erreurs |",
        "|---|---|---|---|",
        f"| TestFX (`ApplicationExtension`) | {cfx} | **{efx}** | **{rfx}** |",
        f"| Le reste | {cre} | **{ere}** | **{rre}** |",
        "",
    ]
    if efx + rfx + ere + rre == 0:
        # La conclusion suit le PERIMETRE. Dire « la suite passe » apres un passage cible
        # contredisait l en-tete pose trois lignes plus haut, et c etait la moitie rassurante de la
        # contradiction, donc celle qu on retient.
        if demandees:
            lignes.append(
                "**Aucun échec** sur les classes demandées. Le reste de la suite n'a pas été exécuté."
            )
        else:
            lignes.append("**Aucun échec.** La suite passe telle quelle sur cette plateforme.")
    else:
        lignes += ["#### Les classes en cause", ""]
        for xml in sorted(dossier.glob("TEST-*.xml")):
            if not FAUTIVE.search(xml.read_text(encoding="utf-8", errors="ignore")):
                continue
            nom = xml.stem.removeprefix("TEST-")
            famille = "TestFX" if nom.rsplit(".", 1)[-1] in fx else "reste"
            lignes.append(f"- `{nom}` ({famille})")

    compte_rendu = "\n".join(lignes) + "\n"
    print(compte_rendu, end="")
    # Au journal ET au resume, jamais deux fois au meme endroit.
    destination = resume if resume is not None else os.environ.get("GITHUB_STEP_SUMMARY", "")
    if destination:
        with open(destination, "a", encoding="utf-8") as sortie:
            sortie.write(compte_rendu)

    # L interruption se juge AVANT les echecs.
    if not demandees and not alle_au_bout:
        print(
            f"::error title=La suite a été interrompue sur {plateforme}::{lues} classe(s) ont rendu "
            "un rapport, et la suite ne s'est pas terminée. Ce passage n'est pas une preuve."
        )
        return 1

    total = efx + rfx + ere + rre
    if total > 0:
        print(
            f"::error title=La suite ne passe pas sur {plateforme}::{total} échec(s) et erreur(s) - "
            "voir le tableau du résumé."
        )
        return 1
    return 0


# (nom, motif attendu, code attendu, marqueur, classes)
CAS = (
    # Le defaut de #4544, dans les deux sens. Sans marqueur, les memes rapports sans un seul echec
    # doivent rougir : c est tout l objet du garde, puisqu un passage tronque rend zero echec.
    ("sans témoin, un passage sans échec est REFUSÉ", "INTERROMPU", 1, False, ""),
    (
        "sans témoin, le journal nomme le nombre de classes lues",
        "2 classe(s) ont rendu un rapport",
        1,
        False,
        "",
    ),
    # Le controle de l autre bord, sans lequel le garde pourrait refuser TOUT et paraitre bon.
    ("avec témoin, le même passage est accepté", "Aucun échec.", 0, True, ""),
    ("avec témoin, il se dit complet", "Passage **complet**", 0, True, ""),
    # Un passage cible n a jamais pretendu etre complet : le marqueur ne le concerne pas.
    ("un passage ciblé passe sans témoin", "Passage ciblé", 0, False, "UnModeleTest"),
    (
        "un passage ciblé ne se déclare pas complet",
        "ne dit **rien** du reste",
        0,
        False,
        "UnModeleTest",
    ),
)


def _auto_test() -> int:
    """Les six cas de la version bash, dont quatre controles negatifs."""
    import contextlib
    import io
    import shutil
    import tempfile

    total = echecs = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-trie-") as tmp:
        bac = pathlib.Path(tmp)
        sources = bac / "sources"
        sources.mkdir()
        # Une classe TestFX et une autre, pour que le tri des deux familles soit exerce.
        (sources / "UneVueTest.java").write_text(ANNOTATION + "\n", encoding="utf-8")
        (sources / "UnModeleTest.java").write_text("class UnModeleTest {}\n", encoding="utf-8")

        for nom, motif, code_attendu, marqueur, classes in CAS:
            rapports = bac / "rapports"
            shutil.rmtree(rapports, ignore_errors=True)
            rapports.mkdir()
            (rapports / "TEST-fr.essai.UneVueTest.xml").write_text(
                '<testsuite name="fr.essai.UneVueTest" tests="2" failures="0" errors="0" skipped="0"/>\n',
                encoding="utf-8",
            )
            (rapports / "TEST-fr.essai.UnModeleTest.xml").write_text(
                '<testsuite name="fr.essai.UnModeleTest" tests="3" failures="0" errors="0" skipped="0"/>\n',
                encoding="utf-8",
            )
            temoin = bac / "temoin"
            temoin.unlink(missing_ok=True)
            if marqueur:
                temoin.touch()
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                code = trier(rapports, sources, temoin, classes, resume="")
            obtenu = tampon.getvalue()
            total += 1
            if motif in obtenu and code == code_attendu:
                print(f"  [OK   ] {nom:<58} -> code {code}")
            else:
                lignes = obtenu.splitlines()
                extrait = lignes[-2] if len(lignes) >= 2 else ""
                print(f"  [ÉCHEC] {nom:<58} -> code {code} : {extrait}")
                echecs += 1

    print()
    print(f"{total} cas, dont 4 contrôles négatifs.")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(
        trier(
            sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports",
            sys.argv[2] if len(sys.argv) > 2 else "src/test/java",
            sys.argv[3] if len(sys.argv) > 3 else "target/suite-est-allee-au-bout",
        )
    )
